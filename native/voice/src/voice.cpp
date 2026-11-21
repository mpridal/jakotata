#include "jakotata/voice.h"

#include <algorithm>
#include <cmath>

#include "signalsmith-stretch/signalsmith-stretch.h"

namespace jakotata {

namespace {
constexpr int kDecimatedWindow = 512;   // 42,7 ms při 12 kHz
constexpr int kAnalysisHop = 128;       // 10,7 ms
constexpr float kFmin = 60.f;
constexpr float kFmax = 600.f;
}  // namespace

void VoiceAnalysis::toArray(float* out) const {
    out[0] = medianF0; out[1] = f0P10; out[2] = f0P90;
    out[3] = formants[0]; out[4] = formants[1]; out[5] = formants[2];
    out[6] = voicedSeconds; out[7] = clipFraction; out[8] = snrDb;
    for (int i = 0; i < kBands; ++i) out[9 + i] = bandsDb[i];
}

// ---------------------------------------------------------------- VoiceTracker

VoiceTracker::VoiceTracker()
    : ring_(kDecimatedWindow, 0.f), linear_(kDecimatedWindow, 0.f),
      yin_(kDecimatedWindow, kDecimatedWindow / 2), formant_(kDecimatedWindow),
      f0Median_(300), fMedian_{RunningMedian(150), RunningMedian(150), RunningMedian(150)} {}

void VoiceTracker::setup(float sampleRate, float defaultF0, std::array<float, 3> defaultFormants) {
    sampleRate_ = sampleRate;
    decim_ = std::max(1, static_cast<int>(sampleRate / 12000.f + 0.5f));
    float decimRate = sampleRate / decim_;
    aa1_.setLowpass(sampleRate, decimRate * 0.42f);
    aa2_.setLowpass(sampleRate, decimRate * 0.42f);
    defaultF0_ = defaultF0;
    defaultFormants_ = defaultFormants;
    std::fill(ring_.begin(), ring_.end(), 0.f);
    ringPos_ = phase_ = sinceAnalysis_ = analysisCount_ = 0;
    f0Median_.clear();
    for (auto& m : fMedian_) m.clear();
}

void VoiceTracker::push(const float* x, int n) {
    for (int i = 0; i < n; ++i) {
        float y = aa2_.process(aa1_.process(x[i]));
        if (++phase_ < decim_) continue;
        phase_ = 0;
        ring_[ringPos_] = y;
        ringPos_ = (ringPos_ + 1) % kDecimatedWindow;
        if (++sinceAnalysis_ >= kAnalysisHop) {
            sinceAnalysis_ = 0;
            analyze();
        }
    }
}

void VoiceTracker::analyze() {
    for (int i = 0; i < kDecimatedWindow; ++i) linear_[i] = ring_[(ringPos_ + i) % kDecimatedWindow];
    float energy = 0.f;
    for (float v : linear_) energy += v * v;
    float rms = std::sqrt(energy / kDecimatedWindow);
    float conf = 0.f;
    float f0 = rms > 0.003f ? yin_.detect(linear_.data(), kDecimatedWindow, sampleRate_ / decim_, kFmin, kFmax, 0.15f, &conf) : 0.f;
    voiced_ = f0 > 0.f && conf > 0.6f;
    lastF0_ = voiced_ ? f0 : 0.f;
    if (!voiced_) return;
    f0Median_.add(f0);
    if ((++analysisCount_ & 3) == 0) {
        float f[3];
        if (formant_.estimate(linear_.data(), kDecimatedWindow, sampleRate_ / decim_, f) == 3) {
            for (int k = 0; k < 3; ++k) fMedian_[k].add(f[k]);
        }
    }
}

float VoiceTracker::medianF0() {
    return f0Median_.count() >= 20 ? f0Median_.median() : defaultF0_;
}

std::array<float, 3> VoiceTracker::medianFormants() {
    if (fMedian_[0].count() < 10) return defaultFormants_;
    return {fMedian_[0].median(), fMedian_[1].median(), fMedian_[2].median()};
}

// ---------------------------------------------------------------- VoiceConverter

struct VoiceConverter::Impl {
    signalsmith::stretch::SignalsmithStretch<float> stretch{42};
};

VoiceConverter::VoiceConverter() : impl_(std::make_unique<Impl>()) {}
VoiceConverter::~VoiceConverter() = default;

void VoiceConverter::setup(float sampleRate, bool lowLatency, int maxBlock) {
    sampleRate_ = sampleRate;
    if (lowLatency) {
        // Blok ≈ 2–3 periody mužského hlasu (25 ms) kvůli latenci ve sluchátkách (zadání kap. 3.3).
        int block = static_cast<int>(sampleRate * 0.025f);
        impl_->stretch.configure(1, block, block / 4);
    } else {
        impl_->stretch.presetDefault(1, sampleRate);
    }
    eq_.setup(sampleRate);
    outBands_.setup(sampleRate);
    dyn_.setup(sampleRate);
    dyn_.setGateThresholdDb(-48.f);
    scratch_.assign(std::max(maxBlock, 256), 0.f);
    eqTarget_.fill(0.f);
    reset();
}

void VoiceConverter::reset() {
    impl_->stretch.reset();
    frameFill_ = 0;
    frameEnergy_ = 0.f;
    samplesSinceEq_ = 0;
}

int VoiceConverter::latencySamples() const {
    return impl_->stretch.inputLatency() + impl_->stretch.outputLatency();
}

void VoiceConverter::setParams(const VoiceParams& dad) {
    dad_ = dad;
    outBands_.reset();
    eqTarget_.fill(0.f);
    eq_.setGains(eqTarget_);
}

void VoiceConverter::updateShift(float kidF0, const std::array<float, 3>& kidFormants, float amount) {
    amount = std::clamp(amount, 0.f, 1.f);
    // Výška: poměr mediánů, omezeno −18 až 0 půltónů (zadání kap. 3.1).
    float ratio = kidF0 > 0.f ? dad_.medianF0 / kidF0 : 1.f;
    float semis = std::clamp(12.f * std::log2(std::max(ratio, 1e-3f)), -18.f, 0.f);
    semitones_ = semis * amount;
    // Formanty: průměr poměrů F1–F3, omezeno 0,70–1,00.
    float sum = 0.f;
    int cnt = 0;
    for (int k = 0; k < 3; ++k) {
        if (kidFormants[k] > 0.f && dad_.formants[k] > 0.f) { sum += dad_.formants[k] / kidFormants[k]; ++cnt; }
    }
    float factor = std::clamp(cnt ? sum / cnt : 1.f, 0.70f, 1.0f);
    formantFactor_ = std::pow(factor, amount);
    impl_->stretch.setTransposeSemitones(semitones_);
    impl_->stretch.setFormantFactor(formantFactor_, true);
}

void VoiceConverter::process(const float* in, float* out, int n) {
    // Signalsmith potřebuje oddělený výstup; scratch má velikost maxBlock.
    while (n > 0) {
        int chunk = std::min(n, static_cast<int>(scratch_.size()));
        const float* inputs[1] = {in};
        float* outputs[1] = {scratch_.data()};
        impl_->stretch.process(inputs, chunk, outputs, chunk);
        for (int i = 0; i < chunk; ++i) {
            float y = eq_.process(scratch_[i]);
            // Spektrum převedeného hlasu měříme jen nad −40 dBFS RMS (≈ řeč), obálka ~20 ms.
            frameEnergy_ = 0.999f * frameEnergy_ + 0.001f * y * y;
            if (frameEnergy_ > 1e-4f) outBands_.add(y); else outBands_.skip(y);
            out[i] = dyn_.process(y);
        }
        // Adaptace EQ jednou za 0,5 s: tátovo LTAS − LTAS převedeného hlasu (zadání kap. 3.1).
        samplesSinceEq_ += chunk;
        if (dad_.hasBands && samplesSinceEq_ >= static_cast<int>(sampleRate_ * 0.5f)) {
            samplesSinceEq_ = 0;
            if (outBands_.count() > sampleRate_ * 2) {
                auto measured = outBands_.levelsDb();
                std::array<float, kBands> gains{};
                for (int b = 0; b < kBands; ++b) {
                    float want = dad_.bandsDb[b] - (measured[b] - eq_.gains()[b]);
                    eqTarget_[b] += (want - eqTarget_[b]) * 0.2f;
                    gains[b] = eqTarget_[b];
                }
                // Vyhlazení sousedních pásem.
                std::array<float, kBands> smooth{};
                for (int b = 0; b < kBands; ++b) {
                    float s = gains[b] * 2.f, w = 2.f;
                    if (b > 0) { s += gains[b - 1]; w += 1.f; }
                    if (b + 1 < kBands) { s += gains[b + 1]; w += 1.f; }
                    smooth[b] = s / w;
                }
                eq_.setGains(smooth);
            }
        }
        in += chunk;
        out += chunk;
        n -= chunk;
    }
}

// ---------------------------------------------------------------- analyzeVoice

VoiceAnalysis analyzeVoice(const float* pcm, int n, float sampleRate) {
    VoiceAnalysis a;
    if (n <= 0) return a;
    VoiceTracker tracker;
    tracker.setup(sampleRate, 0.f, {0.f, 0.f, 0.f});
    std::vector<float> f0s;
    std::vector<float> f1, f2, f3;
    FormantEstimator fe(kDecimatedWindow);
    BandAnalyzer bands;
    bands.setup(sampleRate);

    const int frame = static_cast<int>(sampleRate * 0.01f);
    std::vector<float> frameDb;
    int clipped = 0;
    long voicedSamples = 0;
    for (int start = 0; start + frame <= n; start += frame) {
        tracker.push(pcm + start, frame);
        double e = 0;
        for (int i = 0; i < frame; ++i) {
            float v = pcm[start + i];
            e += v * v;
            if (std::fabs(v) > 0.99f) ++clipped;
        }
        frameDb.push_back(gainToDb(static_cast<float>(std::sqrt(e / frame))));
        bool voiced = tracker.voiced();
        if (voiced) {
            f0s.push_back(tracker.lastF0());
            voicedSamples += frame;
        }
        for (int i = 0; i < frame; ++i) {
            if (voiced) bands.add(pcm[start + i]); else bands.skip(pcm[start + i]);
        }
    }
    auto pct = [](std::vector<float> v, float p) {
        if (v.empty()) return 0.f;
        auto it = v.begin() + static_cast<long>(p * (v.size() - 1));
        std::nth_element(v.begin(), it, v.end());
        return *it;
    };
    a.medianF0 = pct(f0s, 0.5f);
    a.f0P10 = pct(f0s, 0.1f);
    a.f0P90 = pct(f0s, 0.9f);
    a.formants = tracker.medianFormants();
    a.voicedSeconds = voicedSamples / sampleRate;
    a.clipFraction = static_cast<float>(clipped) / n;
    a.snrDb = pct(frameDb, 0.9f) - pct(frameDb, 0.1f);
    if (bands.count() > 0) a.bandsDb = bands.levelsDb();
    return a;
}

}  // namespace jakotata
