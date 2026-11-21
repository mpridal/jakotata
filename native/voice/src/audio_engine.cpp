#include "audio_engine.h"

#include <android/log.h>

#include <algorithm>
#include <chrono>
#include <cmath>

#define LOG_TAG "JakoAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace jakotata {

namespace {
constexpr float kMaxSentenceSec = 10.f;
constexpr float kPrerollSec = 0.3f;
constexpr int kMaxBlock = 4096;
constexpr std::array<float, 3> kKidFormants{700.f, 2000.f, 3200.f};
}  // namespace

AudioEngine::AudioEngine() {
    for (int k = 0; k < 3; ++k) kidFormants_[k].store(kKidFormants[k]);
    worker_ = std::thread(&AudioEngine::workerLoop, this);
}

AudioEngine::~AudioEngine() {
    stopEngine();
    {
        std::lock_guard<std::mutex> g(workLock_);
        quit_ = true;
    }
    workCv_.notify_all();
    if (worker_.joinable()) worker_.join();
}

void AudioEngine::setDad(const VoiceParams& dad) {
    std::lock_guard<std::mutex> g(dadLock_);
    pendingDad_ = dad;
    dadChanged_.store(true);
}

bool AudioEngine::startEngine() {
    std::lock_guard<std::mutex> g(streamLock_);
    if (out_) return true;
    LOGI("Opening streams");
    if (!openStreams()) return false;
    LOGI("Starting duplex");
    oboe::Result r = start();
    if (r != oboe::Result::OK) {
        LOGW("Duplex start failed: %s", oboe::convertToText(r));
        closeStreams();
        return false;
    }
    LOGI("Audio started: %.0f Hz, out burst %d, in burst %d", sampleRate_, out_->getFramesPerBurst(),
         in_->getFramesPerBurst());
    return true;
}

void AudioEngine::stopEngine() {
    std::lock_guard<std::mutex> g(streamLock_);
    if (!out_) return;
    stop();
    closeStreams();
}

bool AudioEngine::openStreams() {
    oboe::AudioStreamBuilder ob;
    ob.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Mono)
        ->setSampleRate(48000)
        ->setUsage(oboe::Usage::Media)
        ->setContentType(oboe::ContentType::Speech)
        ->setDataCallback(this)
        ->setErrorCallback(this);
    oboe::Result r = ob.openStream(out_);
    if (r != oboe::Result::OK) {
        LOGW("Output open failed: %s", oboe::convertToText(r));
        out_.reset();
        return false;
    }
    sampleRate_ = static_cast<float>(out_->getSampleRate());

    oboe::AudioStreamBuilder ib;
    ib.setDirection(oboe::Direction::Input)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Mono)
        ->setSampleRate(out_->getSampleRate())
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
        // Vestavěný mikrofon bez úprav pro hovor; nikdy hovorový režim SCO (zadání kap. 3.2).
        ->setInputPreset(oboe::InputPreset::VoicePerformance)
        ->setBufferCapacityInFrames(out_->getFramesPerBurst() * 8);
    r = ib.openStream(in_);
    if (r != oboe::Result::OK) {
        LOGW("Input open failed: %s", oboe::convertToText(r));
        out_->close();
        out_.reset();
        in_.reset();
        return false;
    }
    out_->setBufferSizeInFrames(out_->getFramesPerBurst() * 2);
    setSharedInputStream(in_);
    setSharedOutputStream(out_);

    // DSP podle skutečné vzorkovací frekvence.
    tracker_.setup(sampleRate_, 250.f, kKidFormants);
    live_.setup(sampleRate_, true, kMaxBlock);
    hq_.setup(sampleRate_, false, kMaxBlock);
    {
        std::lock_guard<std::mutex> g(dadLock_);
        dad_ = pendingDad_;
        dadChanged_.store(false);
    }
    live_.setParams(dad_);
    hq_.setParams(dad_);
    vad_.setup(0.01f, 0.6f);
    vadFill_ = 0;
    vadEnergy_ = 0.f;
    preroll_.assign(static_cast<size_t>(kPrerollSec * sampleRate_), 0.f);
    prerollPos_ = 0;
    sentence_.assign(static_cast<size_t>((kMaxSentenceSec + kPrerollSec) * sampleRate_), 0.f);
    playback_.assign(sentence_.size(), 0.f);
    sentenceLen_ = 0;
    playLen_.store(0);
    playPos_ = 0;
    parrot_.store(static_cast<int>(ParrotState::Listening));
    return true;
}

void AudioEngine::closeStreams() {
    if (out_) { out_->close(); out_.reset(); }
    if (in_) { in_->close(); in_.reset(); }
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream*, oboe::Result error) {
    // Např. odpojení sluchátek → znovu otevřít na novém zařízení.
    LOGW("Stream error %s, restarting", oboe::convertToText(error));
    std::thread([this] {
        stopEngine();
        startEngine();
    }).detach();
}

float AudioEngine::liveLatencyMs() const {
    float buffers = 0.f;
    if (out_) buffers += out_->getBufferSizeInFrames();
    if (in_) buffers += in_->getFramesPerBurst();
    return (live_.latencySamples() + buffers) / sampleRate_ * 1000.f;
}

int AudioEngine::measureRoundTripMs() {
    latResult_.store(-1);
    latFrames_.store(0);
    latState_.store(1);
    auto until = std::chrono::steady_clock::now() + std::chrono::seconds(2);
    while (latState_.load() != 0 && std::chrono::steady_clock::now() < until) {
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }
    latState_.store(0);
    return latResult_.load();
}

oboe::DataCallbackResult AudioEngine::onBothStreamsReady(const void* inputData, int numInputFrames,
                                                         void* outputData, int numOutputFrames) {
    const float* in = static_cast<const float*>(inputData);
    float* out = static_cast<float*>(outputData);
    const int n = std::min(numInputFrames, numOutputFrames);
    std::fill(out, out + numOutputFrames, 0.f);
    if (n <= 0) return oboe::DataCallbackResult::Continue;

    // Nový profil táty z UI (bez čekání – když je zámek obsazený, vezmeme ho příště).
    if (dadChanged_.load() && dadLock_.try_lock()) {
        dad_ = pendingDad_;
        dadChanged_.store(false);
        dadLock_.unlock();
        live_.setParams(dad_);
    }

    // Úroveň vstupu pro měřič.
    float e = 0.f;
    for (int i = 0; i < n; ++i) e += in[i] * in[i];
    level_.store(std::sqrt(e / n));

    // Test latence: cvaknutí a čekání, až dorazí do mikrofonu.
    int lat = latState_.load();
    if (lat == 1) {
        for (int i = 0; i < std::min(n, 24); ++i) out[i] = (i % 2 ? -0.9f : 0.9f);
        latFrames_.store(0);
        latState_.store(2);
        return oboe::DataCallbackResult::Continue;
    }
    if (lat == 2) {
        int base = latFrames_.load();
        for (int i = 0; i < n; ++i) {
            if (std::fabs(in[i]) > 0.25f) {
                latResult_.store(static_cast<int>((base + i) / sampleRate_ * 1000.f));
                latState_.store(0);
                break;
            }
        }
        latFrames_.store(base + n);
        return oboe::DataCallbackResult::Continue;
    }

    const auto mode = static_cast<SoundMode>(mode_.load());
    const bool parrotBusy = mode == SoundMode::Parrot && parrot_.load() >= static_cast<int>(ParrotState::Processing);
    // Během přehrávání Papouška je mikrofon „ztlumený“ – nesleduje se, aby se nepřevedl vlastní výstup.
    if (!parrotBusy) tracker_.push(in, n);
    if (++medianCounter_ >= 10) {
        medianCounter_ = 0;
        kidF0_.store(tracker_.medianF0());
        auto f = tracker_.medianFormants();
        for (int k = 0; k < 3; ++k) kidFormants_[k].store(f[k]);
    }
    const float amount = voiceOn_.load() ? amount_.load() : 0.f;

    switch (mode) {
        case SoundMode::Headphones:
            if (voiceOn_.load()) {
                live_.updateShift(kidF0_.load(), {kidFormants_[0].load(), kidFormants_[1].load(), kidFormants_[2].load()}, amount);
                live_.process(in, out, n);
            }
            break;
        case SoundMode::Parrot:
            processParrot(in, out, n);
            break;
        case SoundMode::Silent:
            break;
    }
    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::processParrot(const float* in, float* out, int n) {
    auto state = static_cast<ParrotState>(parrot_.load());
    if (state == ParrotState::Processing) return;
    if (state == ParrotState::Playing) {
        int len = playLen_.load();
        int k = std::min(n, len - playPos_);
        for (int i = 0; i < k; ++i) out[i] = playback_[playPos_ + i];
        playPos_ += k;
        if (playPos_ >= len) {
            // Krátké doznění, než mikrofon znovu poslouchá (ozvěna z reproduktoru).
            if (cooldown_ <= 0) cooldown_ = static_cast<int>(0.25f * sampleRate_);
            cooldown_ -= n;
            if (cooldown_ <= 0) {
                vad_.reset();
                parrot_.store(static_cast<int>(ParrotState::Listening));
            }
        }
        return;
    }

    const int frame = static_cast<int>(sampleRate_ * 0.01f);
    for (int i = 0; i < n; ++i) {
        float x = in[i];
        if (state == ParrotState::Recording) {
            if (sentenceLen_ < static_cast<int>(sentence_.size())) sentence_[sentenceLen_++] = x;
        } else {
            preroll_[prerollPos_] = x;
            prerollPos_ = (prerollPos_ + 1) % static_cast<int>(preroll_.size());
        }
        vadEnergy_ += x * x;
        if (++vadFill_ < frame) continue;
        float rms = std::sqrt(vadEnergy_ / frame);
        vadFill_ = 0;
        vadEnergy_ = 0.f;
        auto ev = vad_.process(rms);
        if (state == ParrotState::Listening && ev == Vad::Event::SpeechStart) {
            // Začátek věty: předřadit posledních 0,3 s, aby se neusekl první hlásek.
            sentenceLen_ = 0;
            for (size_t k = 0; k < preroll_.size(); ++k) {
                sentence_[sentenceLen_++] = preroll_[(prerollPos_ + k) % preroll_.size()];
            }
            state = ParrotState::Recording;
            parrot_.store(static_cast<int>(state));
        } else if (state == ParrotState::Recording &&
                   (ev == Vad::Event::SentenceEnd || sentenceLen_ >= static_cast<int>(kMaxSentenceSec * sampleRate_))) {
            state = ParrotState::Processing;
            parrot_.store(static_cast<int>(state));
            {
                std::lock_guard<std::mutex> g(workLock_);
                workPending_ = true;
            }
            workCv_.notify_one();
            return;
        }
    }
}

void AudioEngine::workerLoop() {
    std::vector<float> padded, converted;
    while (true) {
        int len;
        {
            std::unique_lock<std::mutex> lk(workLock_);
            workCv_.wait(lk, [this] { return workPending_ || quit_; });
            if (quit_) return;
            workPending_ = false;
            len = sentenceLen_;
        }
        // Věta převedená v kvalitnějším režimu (ne v reálném čase), kompenzace latence algoritmu.
        {
            std::lock_guard<std::mutex> g(dadLock_);
            hq_.setParams(dadChanged_.load() ? pendingDad_ : dad_);
        }
        float amount = voiceOn_.load() ? amount_.load() : 0.f;
        hq_.reset();
        hq_.updateShift(kidF0_.load(), {kidFormants_[0].load(), kidFormants_[1].load(), kidFormants_[2].load()}, amount);
        int lat = hq_.latencySamples();
        padded.assign(static_cast<size_t>(len + lat), 0.f);
        std::copy(sentence_.begin(), sentence_.begin() + len, padded.begin());
        converted.assign(padded.size(), 0.f);
        hq_.process(padded.data(), converted.data(), static_cast<int>(padded.size()));
        int outLen = std::min(len, static_cast<int>(playback_.size()));
        std::copy(converted.begin() + lat, converted.begin() + lat + outLen, playback_.begin());
        playPos_ = 0;
        cooldown_ = 0;
        playLen_.store(outLen);
        parrot_.store(static_cast<int>(ParrotState::Playing));
    }
}

}  // namespace jakotata
