#pragma once

#include <algorithm>
#include <array>
#include <cmath>
#include <vector>

namespace jakotata {

constexpr float kPi = 3.14159265358979f;

inline float dbToGain(float db) { return std::pow(10.f, db / 20.f); }
inline float gainToDb(float g) { return 20.f * std::log10(std::max(g, 1e-9f)); }

/** Biquad (RBJ Audio EQ Cookbook), Direct Form II transposed. */
struct Biquad {
    float b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0;
    float z1 = 0, z2 = 0;

    float process(float x) {
        float y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        return y;
    }
    void reset() { z1 = z2 = 0; }

    void setPeaking(float sampleRate, float freq, float q, float gainDb) {
        float A = std::pow(10.f, gainDb / 40.f);
        float w = 2.f * kPi * freq / sampleRate;
        float alpha = std::sin(w) / (2.f * q);
        float c = std::cos(w);
        float a0 = 1.f + alpha / A;
        b0 = (1.f + alpha * A) / a0;
        b1 = -2.f * c / a0;
        b2 = (1.f - alpha * A) / a0;
        a1 = -2.f * c / a0;
        a2 = (1.f - alpha / A) / a0;
    }

    void setBandpass(float sampleRate, float freq, float q) {
        float w = 2.f * kPi * freq / sampleRate;
        float alpha = std::sin(w) / (2.f * q);
        float a0 = 1.f + alpha;
        b0 = alpha / a0;
        b1 = 0.f;
        b2 = -alpha / a0;
        a1 = -2.f * std::cos(w) / a0;
        a2 = (1.f - alpha) / a0;
    }

    void setLowpass(float sampleRate, float freq, float q = 0.7071f) {
        float w = 2.f * kPi * freq / sampleRate;
        float alpha = std::sin(w) / (2.f * q);
        float c = std::cos(w);
        float a0 = 1.f + alpha;
        b0 = (1.f - c) / 2.f / a0;
        b1 = (1.f - c) / a0;
        b2 = b0;
        a1 = -2.f * c / a0;
        a2 = (1.f - alpha) / a0;
    }

    /** Odezva v dB na frekvenci f. */
    float responseDb(float sampleRate, float f) const {
        float w = 2.f * kPi * f / sampleRate;
        float cr = std::cos(w), ci = -std::sin(w), c2r = std::cos(2 * w), c2i = -std::sin(2 * w);
        float nr = b0 + b1 * cr + b2 * c2r, ni = b1 * ci + b2 * c2i;
        float dr = 1 + a1 * cr + a2 * c2r, di = a1 * ci + a2 * c2i;
        return 10.f * std::log10((nr * nr + ni * ni) / std::max(dr * dr + di * di, 1e-12f));
    }
};

/** Pásma pro měření dlouhodobého spektra a ekvalizér (≈ 2/3 oktávy, zadání kap. 3.1). */
constexpr int kBands = 10;
constexpr std::array<float, kBands> kBandFreqs = {100, 160, 250, 400, 630, 1000, 1600, 2500, 4000, 6300};

/**
 * Dlouhodobé spektrum (LTAS) v pásmech: banka pásmových propustí a průměrná energie.
 * Stejný kód měří tátu (profil) i převedený hlas dcery, takže se křivky dají přímo odečíst.
 */
class BandAnalyzer {
public:
    void setup(float sampleRate) {
        for (int i = 0; i < kBands; ++i) filters_[i].setBandpass(sampleRate, kBandFreqs[i], 1.4f);
        reset();
    }
    void reset() {
        for (auto& f : filters_) f.reset();
        energy_.fill(0.0);
        count_ = 0;
    }
    /** Přidá vzorek (počítat jen ve znělých úsecích). */
    void add(float x) {
        for (int i = 0; i < kBands; ++i) {
            float y = filters_[i].process(x);
            energy_[i] += static_cast<double>(y) * y;
        }
        ++count_;
    }
    /** Jen průchod filtry bez započtení (udržuje stav filtrů v neznělých úsecích). */
    void skip(float x) { for (auto& f : filters_) f.process(x); }

    long count() const { return count_; }

    /** Úrovně v dB relativně k průměru přes pásma. */
    std::array<float, kBands> levelsDb() const {
        std::array<float, kBands> out{};
        float mean = 0.f;
        for (int i = 0; i < kBands; ++i) {
            out[i] = 10.f * std::log10(std::max(energy_[i] / std::max<long>(count_, 1), 1e-20));
            mean += out[i];
        }
        mean /= kBands;
        for (auto& v : out) v -= mean;
        return out;
    }

private:
    std::array<Biquad, kBands> filters_{};
    std::array<double, kBands> energy_{};
    long count_ = 0;
};

/** Banka peaking filtrů na středech pásem – barva hlasu podle táty. */
class Equalizer {
public:
    void setup(float sampleRate) { sampleRate_ = sampleRate; setGains({}); }

    /** Zisky v dB pro každé pásmo, omezené na ±12 dB (zadání kap. 3.1). */
    void setGains(const std::array<float, kBands>& gainsDb) {
        for (int i = 0; i < kBands; ++i) {
            gains_[i] = std::clamp(gainsDb[i], -12.f, 12.f);
            filters_[i].setPeaking(sampleRate_, kBandFreqs[i], 1.4f, gains_[i]);
        }
    }
    const std::array<float, kBands>& gains() const { return gains_; }

    float process(float x) {
        for (auto& f : filters_) x = f.process(x);
        return x;
    }
    float responseDb(float f) const {
        float s = 0.f;
        for (const auto& b : filters_) s += b.responseDb(sampleRate_, f);
        return s;
    }

private:
    float sampleRate_ = 48000.f;
    std::array<Biquad, kBands> filters_{};
    std::array<float, kBands> gains_{};
};

/**
 * Detekce řeči podle energie s adaptivním prahem šumu a doznívání; hlásí konec věty po pauze.
 * Volá se po rámcích (např. 10 ms).
 */
class Vad {
public:
    void setup(float frameSeconds, float endPauseSeconds = 0.6f, float minSpeechSeconds = 0.25f) {
        frameSec_ = frameSeconds;
        endPauseFrames_ = static_cast<int>(endPauseSeconds / frameSeconds + 0.5f);
        minSpeechFrames_ = static_cast<int>(minSpeechSeconds / frameSeconds + 0.5f);
        reset();
    }
    void reset() {
        noiseDb_ = -60.f;
        speaking_ = false;
        silentFrames_ = 0;
        speechFrames_ = 0;
    }

    enum class Event { None, SpeechStart, SentenceEnd };

    Event process(float rms) {
        float db = gainToDb(rms);
        // Šum: rychle dolů, pomalu nahoru (sleduje minimum).
        if (db < noiseDb_) noiseDb_ += (db - noiseDb_) * 0.2f;
        else noiseDb_ += (db - noiseDb_) * 0.002f;
        bool voice = db > std::max(noiseDb_ + 10.f, -50.f);
        Event ev = Event::None;
        if (voice) {
            silentFrames_ = 0;
            ++speechFrames_;
            if (!speaking_ && speechFrames_ >= 3) { speaking_ = true; ev = Event::SpeechStart; }
        } else if (speaking_) {
            if (++silentFrames_ >= endPauseFrames_) {
                bool longEnough = speechFrames_ >= minSpeechFrames_;
                speaking_ = false;
                speechFrames_ = 0;
                silentFrames_ = 0;
                if (longEnough) ev = Event::SentenceEnd;
            }
        } else {
            speechFrames_ = 0;
        }
        lastVoice_ = voice;
        return ev;
    }

    bool speaking() const { return speaking_; }
    bool voiceInFrame() const { return lastVoice_; }
    float noiseDb() const { return noiseDb_; }

private:
    float frameSec_ = 0.01f;
    int endPauseFrames_ = 60;
    int minSpeechFrames_ = 25;
    float noiseDb_ = -60.f;
    bool speaking_ = false;
    bool lastVoice_ = false;
    int silentFrames_ = 0;
    int speechFrames_ = 0;
};

/** Noise gate, lehký kompresor a limiter (zadání kap. 3.1 – dynamika). */
class Dynamics {
public:
    void setup(float sampleRate) {
        attack_ = std::exp(-1.f / (0.003f * sampleRate));
        release_ = std::exp(-1.f / (0.120f * sampleRate));
        gateRelease_ = std::exp(-1.f / (0.060f * sampleRate));
        env_ = 0.f;
        gateGain_ = 0.f;
    }
    void setGateThresholdDb(float db) { gateThreshold_ = dbToGain(db); }

    float process(float x) {
        float a = std::fabs(x);
        env_ = a > env_ ? attack_ * env_ + (1 - attack_) * a : release_ * env_ + (1 - release_) * a;
        // Gate: pod prahem plynule ztlumit.
        float gateTarget = env_ > gateThreshold_ ? 1.f : 0.f;
        gateGain_ = gateTarget > gateGain_ ? gateTarget : gateRelease_ * gateGain_ + (1 - gateRelease_) * gateTarget;
        // Kompresor: práh −18 dBFS, poměr 3:1.
        float envDb = gainToDb(env_);
        float compDb = envDb > -18.f ? (-18.f + (envDb + 18.f) / 3.f) - envDb : 0.f;
        float y = x * gateGain_ * dbToGain(compDb + makeupDb_);
        // Limiter: tvrdý strop −1 dBFS s měkkým kolenem (tanh).
        const float ceiling = 0.891f;
        return ceiling * std::tanh(y / ceiling);
    }

private:
    float attack_ = 0, release_ = 0, gateRelease_ = 0;
    float env_ = 0, gateGain_ = 0;
    float gateThreshold_ = 0.003f;
    float makeupDb_ = 4.f;
};

/** Medián z kruhového bufferu hodnot (bez alokací po konstrukci). */
class RunningMedian {
public:
    explicit RunningMedian(int capacity) : values_(capacity, 0.f), scratch_(capacity, 0.f) {}
    void add(float v) {
        values_[pos_] = v;
        pos_ = (pos_ + 1) % static_cast<int>(values_.size());
        if (count_ < static_cast<int>(values_.size())) ++count_;
    }
    int count() const { return count_; }
    void clear() { count_ = 0; pos_ = 0; }
    float median() {
        if (count_ == 0) return 0.f;
        std::copy(values_.begin(), values_.begin() + count_, scratch_.begin());
        auto mid = scratch_.begin() + count_ / 2;
        std::nth_element(scratch_.begin(), mid, scratch_.begin() + count_);
        return *mid;
    }

private:
    std::vector<float> values_;
    std::vector<float> scratch_;
    int pos_ = 0;
    int count_ = 0;
};

}  // namespace jakotata
