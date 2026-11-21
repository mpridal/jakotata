#include <gtest/gtest.h>

#include <cmath>
#include <cstdlib>
#include <new>
#include <random>
#include <vector>

#include "jakotata/dsp.h"
#include "jakotata/formants.h"
#include "jakotata/voice.h"
#include "jakotata/yin.h"

using namespace jakotata;

namespace {
constexpr float kSr = 48000.f;

/** Harmonický signál (hlas-like): základní tón + harmonické klesající 1/k. */
std::vector<float> harmonic(float f0, float seconds, float sr = kSr, int harmonics = 8, float amp = 0.3f) {
    std::vector<float> x(static_cast<size_t>(seconds * sr));
    for (size_t i = 0; i < x.size(); ++i) {
        float s = 0.f;
        for (int k = 1; k <= harmonics; ++k) s += std::sin(2.f * kPi * f0 * k * i / sr) / k;
        x[i] = amp * s / 2.f;
    }
    return x;
}

// Počítadlo alokací pro test „žádné alokace v callbacku“ (zadání kap. 10).
int gAllocations = 0;
bool gCounting = false;
}  // namespace

void* operator new(std::size_t n) {
    if (gCounting) ++gAllocations;
    if (void* p = std::malloc(n)) return p;
    throw std::bad_alloc();
}
void operator delete(void* p) noexcept { std::free(p); }
void operator delete(void* p, std::size_t) noexcept { std::free(p); }

TEST(Yin, DetectsPureTonesWithin2Percent) {
    Yin yin(2048, 1024);
    for (float f : {80.f, 110.f, 220.f, 330.f, 440.f}) {
        const float sr = 12000.f;
        std::vector<float> x(1024);
        for (size_t i = 0; i < x.size(); ++i) x[i] = std::sin(2.f * kPi * f * i / sr);
        float conf = 0;
        float est = yin.detect(x.data(), static_cast<int>(x.size()), sr, 60.f, 600.f, 0.15f, &conf);
        EXPECT_NEAR(est, f, f * 0.02f) << "f=" << f;
        EXPECT_GT(conf, 0.8f);
    }
}

TEST(Yin, SilenceAndNoiseAreUnvoiced) {
    Yin yin(1024, 512);
    std::vector<float> noise(1024);
    std::mt19937 rng(1);
    std::uniform_real_distribution<float> d(-1.f, 1.f);
    for (auto& v : noise) v = d(rng);
    float conf = 1;
    float f = yin.detect(noise.data(), 1024, 12000.f, 60.f, 600.f, 0.15f, &conf);
    EXPECT_TRUE(f == 0.f || conf < 0.6f);
}

TEST(VoiceTracker, MedianOfHarmonicSignal) {
    VoiceTracker t;
    t.setup(kSr, 250.f, {700, 2000, 3200});
    auto x = harmonic(260.f, 2.f);
    for (size_t i = 0; i < x.size(); i += 480) t.push(x.data() + i, static_cast<int>(std::min<size_t>(480, x.size() - i)));
    EXPECT_NEAR(t.medianF0(), 260.f, 260.f * 0.02f);
}

TEST(Biquad, PeakingHasRequestedGainAtCenter) {
    Biquad b;
    b.setPeaking(kSr, 1000.f, 1.4f, 6.f);
    EXPECT_NEAR(b.responseDb(kSr, 1000.f), 6.f, 0.05f);
    EXPECT_NEAR(b.responseDb(kSr, 100.f), 0.f, 0.5f);
}

TEST(Equalizer, GainsClampedTo12dB) {
    Equalizer eq;
    eq.setup(kSr);
    std::array<float, kBands> g{};
    g[5] = 20.f;
    g[2] = -30.f;
    eq.setGains(g);
    EXPECT_FLOAT_EQ(eq.gains()[5], 12.f);
    EXPECT_FLOAT_EQ(eq.gains()[2], -12.f);
    EXPECT_GT(eq.responseDb(1000.f), 9.f);
}

TEST(Vad, DetectsSentenceEndAfterPause) {
    Vad vad;
    vad.setup(0.01f, 0.6f);
    int starts = 0, ends = 0, endFrame = -1;
    for (int f = 0; f < 300; ++f) {
        float rms = (f >= 50 && f < 150) ? 0.1f : 0.0005f;  // 1 s řeči od 0,5 s
        auto ev = vad.process(rms);
        if (ev == Vad::Event::SpeechStart) ++starts;
        if (ev == Vad::Event::SentenceEnd) { ++ends; endFrame = f; }
    }
    EXPECT_EQ(starts, 1);
    EXPECT_EQ(ends, 1);
    EXPECT_NEAR(endFrame, 150 + 60, 2);
}

TEST(Formants, FindsResonancesOfSyntheticVowel) {
    // Pulsní buzení 120 Hz přes sériové dvoupólové rezonátory 700, 1200 a 2600 Hz (model samohlásky „a“).
    const float sr = 12000.f;
    const float freqs[3] = {700.f, 1200.f, 2600.f};
    float y1[3] = {}, y2[3] = {};
    std::vector<float> all(2048);
    for (size_t i = 0; i < all.size(); ++i) {
        float s = (i % 100 == 0) ? 1.f : 0.f;
        for (int k = 0; k < 3; ++k) {
            float r = std::exp(-kPi * 90.f / sr);
            float y = s + 2.f * r * std::cos(2.f * kPi * freqs[k] / sr) * y1[k] - r * r * y2[k];
            y2[k] = y1[k]; y1[k] = y;
            s = y;
        }
        all[i] = s;
    }
    FormantEstimator fe(512, 12);
    float f[3] = {};
    int n = fe.estimate(all.data() + 1536, 512, sr, f);
    ASSERT_EQ(n, 3);
    EXPECT_NEAR(f[0], 700.f, 70.f);
    EXPECT_NEAR(f[1], 1200.f, 120.f);
    EXPECT_NEAR(f[2], 2600.f, 200.f);
}

TEST(VoiceConverter, ShiftsPitchTowardsDad) {
    VoiceConverter conv;
    conv.setup(kSr, false, 4096);
    VoiceParams dad;
    dad.medianF0 = 120.f;
    conv.setParams(dad);
    conv.updateShift(240.f, {700, 2000, 3200}, 1.f);
    EXPECT_NEAR(conv.pitchSemitones(), -12.f, 0.01f);
    // Výchozí formanty táty 550/1500/2500 vůči 700/2000/3200 → průměr poměrů ≈ 0,772.
    EXPECT_NEAR(conv.formantFactor(), (550.f / 700 + 1500.f / 2000 + 2500.f / 3200) / 3.f, 1e-4f);

    auto in = harmonic(240.f, 3.f);
    std::vector<float> out(in.size());
    conv.process(in.data(), out.data(), static_cast<int>(in.size()));
    VoiceTracker t;
    t.setup(kSr, 0.f, {0, 0, 0});
    for (size_t i = kSr; i < out.size(); i += 480) t.push(out.data() + i, 480);  // přeskočit náběh
    EXPECT_NEAR(t.medianF0(), 120.f, 120.f * 0.05f);
}

TEST(VoiceConverter, AmountZeroKeepsOwnVoice) {
    VoiceConverter conv;
    conv.setup(kSr, true, 1024);
    conv.setParams(VoiceParams{});
    conv.updateShift(260.f, {700, 2000, 3200}, 0.f);
    EXPECT_FLOAT_EQ(conv.pitchSemitones(), 0.f);
    EXPECT_FLOAT_EQ(conv.formantFactor(), 1.f);
}

TEST(VoiceConverter, LowLatencyModeUnder50ms) {
    VoiceConverter conv;
    conv.setup(kSr, true, 1024);
    EXPECT_LT(conv.latencySamples() / kSr * 1000.f, 50.f);
}

TEST(VoiceConverter, NoAllocationsWhileProcessing) {
    VoiceConverter conv;
    conv.setup(kSr, true, 512);
    conv.setParams(VoiceParams{});
    VoiceTracker tracker;
    tracker.setup(kSr, 250.f, {700, 2000, 3200});
    auto in = harmonic(250.f, 1.f);
    std::vector<float> out(512);
    gAllocations = 0;
    gCounting = true;
    for (size_t i = 0; i + 192 <= in.size(); i += 192) {
        tracker.push(in.data() + i, 192);
        conv.updateShift(tracker.medianF0(), tracker.medianFormants(), 0.8f);
        conv.process(in.data() + i, out.data(), 192);
    }
    gCounting = false;
    EXPECT_EQ(gAllocations, 0);
}

TEST(AnalyzeVoice, ReportsMedianAndVoicedTime) {
    auto x = harmonic(112.f, 3.f);
    auto a = analyzeVoice(x.data(), static_cast<int>(x.size()), kSr);
    EXPECT_NEAR(a.medianF0, 112.f, 112.f * 0.02f);
    EXPECT_GT(a.voicedSeconds, 2.5f);
    EXPECT_FLOAT_EQ(a.clipFraction, 0.f);
}
