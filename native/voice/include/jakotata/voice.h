#pragma once

#include <array>
#include <memory>
#include <vector>

#include "jakotata/dsp.h"
#include "jakotata/formants.h"
#include "jakotata/yin.h"

namespace jakotata {

/** Hlas táty z profilu (zadání kap. 3.4). */
struct VoiceParams {
    float medianF0 = 110.f;
    std::array<float, 3> formants{550.f, 1500.f, 2500.f};
    std::array<float, kBands> bandsDb{};
    bool hasBands = false;
};

/** Výsledek analýzy nahrávky hlasu (profil táty). */
struct VoiceAnalysis {
    float medianF0 = 0, f0P10 = 0, f0P90 = 0;
    std::array<float, 3> formants{};
    float voicedSeconds = 0;
    float clipFraction = 0;
    float snrDb = 0;
    std::array<float, kBands> bandsDb{};

    static constexpr int kSize = 9 + kBands;
    void toArray(float* out) const;
};

/**
 * Průběžné sledování výšky (medián F0) a formantů mluvčího. Signál 48 kHz se decimuje na 12 kHz,
 * YIN každých ~10 ms, formanty každých ~40 ms ve znělých úsecích. Bez alokací po setup().
 */
class VoiceTracker {
public:
    VoiceTracker();
    void setup(float sampleRate, float defaultF0, std::array<float, 3> defaultFormants);
    void push(const float* x, int n);
    float medianF0();
    std::array<float, 3> medianFormants();
    bool voiced() const { return voiced_; }
    float lastF0() const { return lastF0_; }

private:
    void analyze();
    float sampleRate_ = 48000.f;
    int decim_ = 4;
    int phase_ = 0;
    Biquad aa1_, aa2_;
    std::vector<float> ring_, linear_;
    int ringPos_ = 0;
    int sinceAnalysis_ = 0;
    int analysisCount_ = 0;
    Yin yin_;
    FormantEstimator formant_;
    RunningMedian f0Median_;
    std::array<RunningMedian, 3> fMedian_;
    float defaultF0_ = 250.f;
    std::array<float, 3> defaultFormants_{};
    bool voiced_ = false;
    float lastF0_ = 0.f;
    float noiseRms_ = 0.01f;
};

/**
 * Převod hlasu (zadání kap. 3.1): posun výšky a formantů (Signalsmith Stretch), ekvalizér podle
 * tátova spektra (průběžně adaptovaný) a dynamika. Výška a formanty se nastavují z venku podle
 * [VoiceTracker] dcery a profilu táty.
 */
class VoiceConverter {
public:
    VoiceConverter();
    ~VoiceConverter();
    /** @param lowLatency malý blok pro živý poslech ve sluchátkách (≤ 50 ms), jinak vyšší kvalita */
    void setup(float sampleRate, bool lowLatency, int maxBlock);
    void setParams(const VoiceParams& dad);
    /** Výška a formanty dcery + „Kolik táty“ (0–1) → posun. */
    void updateShift(float kidF0, const std::array<float, 3>& kidFormants, float amount);
    void process(const float* in, float* out, int n);
    void reset();
    int latencySamples() const;
    float pitchSemitones() const { return semitones_; }
    float formantFactor() const { return formantFactor_; }
    const Equalizer& equalizer() const { return eq_; }

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
    float sampleRate_ = 48000.f;
    VoiceParams dad_;
    Equalizer eq_;
    BandAnalyzer outBands_;
    Dynamics dyn_;
    float semitones_ = 0.f;
    float formantFactor_ = 1.f;
    std::array<float, kBands> eqTarget_{};
    int frameFill_ = 0;
    float frameEnergy_ = 0.f;
    int samplesSinceEq_ = 0;
    std::vector<float> scratch_;
};

/** Analýza nahrávky (offline, smí alokovat). */
VoiceAnalysis analyzeVoice(const float* pcm, int n, float sampleRate);

}  // namespace jakotata
