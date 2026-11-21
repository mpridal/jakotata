#pragma once

#include <oboe/Oboe.h>

#include <atomic>
#include <condition_variable>
#include <memory>
#include <mutex>
#include <thread>
#include <vector>

#include "jakotata/dsp.h"
#include "jakotata/voice.h"

namespace jakotata {

/** Režimy zvuku (zadání kap. 3.2). */
enum class SoundMode : int { Silent = 0, Headphones = 1, Parrot = 2 };

/** Stav Papouška pro UI. */
enum class ParrotState : int { Listening = 0, Recording = 1, Processing = 2, Playing = 3 };

/**
 * Nízkolatenční full duplex přes Oboe/AAudio (zadání kap. 3.3): mikrofon → sledování hlasu dcery →
 * podle režimu živý převod do sluchátek, Papoušek (věta po větě) nebo ticho. Mikrofon je vždy
 * vestavěný (nikdy SCO/HFP), výstup jde na aktuální zařízení (reproduktor, kabelová i BT A2DP sluchátka).
 */
class AudioEngine : public oboe::FullDuplexStream, public oboe::AudioStreamErrorCallback {
public:
    AudioEngine();
    ~AudioEngine() override;

    bool startEngine();
    void stopEngine();

    void setMode(SoundMode m) { mode_.store(static_cast<int>(m)); }
    void setVoiceEnabled(bool on) { voiceOn_.store(on); }
    void setAmount(float a) { amount_.store(a); }
    void setDad(const VoiceParams& dad);

    float inputLevel() const { return level_.load(); }
    int parrotState() const { return parrot_.load(); }
    float kidF0() const { return kidF0_.load(); }
    /** Latence algoritmu živého převodu + bufferů streamů [ms]. */
    float liveLatencyMs() const;
    /** Round-trip test: cvaknutí z reproduktoru → mikrofon [ms], −1 při neúspěchu. Blokuje až 2 s. */
    int measureRoundTripMs();

    oboe::DataCallbackResult onBothStreamsReady(const void* inputData, int numInputFrames, void* outputData,
                                                int numOutputFrames) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    bool openStreams();
    void closeStreams();
    void workerLoop();
    void processParrot(const float* in, float* out, int n);

    std::shared_ptr<oboe::AudioStream> in_, out_;
    std::mutex streamLock_;
    float sampleRate_ = 48000.f;

    VoiceTracker tracker_;
    VoiceConverter live_;
    VoiceConverter hq_;
    Vad vad_;
    int vadFill_ = 0;
    float vadEnergy_ = 0.f;
    int medianCounter_ = 0;

    std::atomic<int> mode_{static_cast<int>(SoundMode::Parrot)};
    std::atomic<bool> voiceOn_{true};
    std::atomic<float> amount_{0.8f};
    std::atomic<float> level_{0.f};
    std::atomic<float> kidF0_{250.f};
    std::array<std::atomic<float>, 3> kidFormants_{};

    // Profil táty: UI → audio vlákno přes mutex + příznak (callback jen try_lock).
    std::mutex dadLock_;
    VoiceParams pendingDad_;
    std::atomic<bool> dadChanged_{false};
    VoiceParams dad_;

    // Papoušek.
    std::atomic<int> parrot_{static_cast<int>(ParrotState::Listening)};
    std::vector<float> preroll_;
    int prerollPos_ = 0;
    std::vector<float> sentence_;
    int sentenceLen_ = 0;
    std::vector<float> playback_;
    std::atomic<int> playLen_{0};
    int playPos_ = 0;
    int cooldown_ = 0;
    std::thread worker_;
    std::mutex workLock_;
    std::condition_variable workCv_;
    bool workPending_ = false;
    bool quit_ = false;

    // Test latence.
    std::atomic<int> latState_{0};  // 0 nic, 1 vyslat cvaknutí, 2 čekat na vstup
    std::atomic<int> latFrames_{0};
    std::atomic<int> latResult_{-1};
};

}  // namespace jakotata
