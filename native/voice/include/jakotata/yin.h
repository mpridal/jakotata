#pragma once

#include <cmath>
#include <vector>

namespace jakotata {

/**
 * Odhad základní frekvence (F0) algoritmem YIN (de Cheveigné & Kawahara, 2002).
 * Bez alokací po konstrukci – smí běžet v audio callbacku.
 */
class Yin {
public:
    explicit Yin(int maxWindow, int maxTau) : diff_(maxTau + 1, 0.f), window_(maxWindow), maxTau_(maxTau) {}

    /**
     * @param x okno signálu délky n (n ≤ maxWindow, n > tauMax)
     * @param confidence [out] 1 − hodnota normované diferenční funkce v minimu (0–1)
     * @return F0 v Hz, nebo 0 pokud signál není znělý
     */
    float detect(const float* x, int n, float sampleRate, float fmin, float fmax, float threshold = 0.15f,
                 float* confidence = nullptr) {
        int tauMin = static_cast<int>(sampleRate / fmax);
        int tauMax = static_cast<int>(sampleRate / fmin);
        if (tauMax > maxTau_) tauMax = maxTau_;
        if (n > window_) n = window_;
        int w = n - tauMax;
        if (confidence) *confidence = 0.f;
        if (w < 16 || tauMin < 2 || tauMin >= tauMax) return 0.f;

        // Diferenční funkce a kumulativní normalizace (CMND).
        diff_[0] = 1.f;
        float running = 0.f;
        for (int tau = 1; tau <= tauMax; ++tau) {
            float s = 0.f;
            for (int j = 0; j < w; ++j) {
                float d = x[j] - x[j + tau];
                s += d * d;
            }
            running += s;
            diff_[tau] = running > 0.f ? s * tau / running : 1.f;
        }
        // Absolutní práh: první minimum pod prahem; jinak globální minimum.
        int best = -1;
        for (int tau = tauMin; tau <= tauMax; ++tau) {
            if (diff_[tau] < threshold) {
                while (tau + 1 <= tauMax && diff_[tau + 1] < diff_[tau]) ++tau;
                best = tau;
                break;
            }
        }
        if (best < 0) {
            float m = 1e9f;
            for (int tau = tauMin; tau <= tauMax; ++tau) {
                if (diff_[tau] < m) { m = diff_[tau]; best = tau; }
            }
            if (m > 0.35f) return 0.f;  // neznělé
        }
        // Parabolická interpolace minima.
        float t = static_cast<float>(best);
        if (best > 1 && best < tauMax) {
            float a = diff_[best - 1], b = diff_[best], c = diff_[best + 1];
            float den = a - 2.f * b + c;
            if (std::fabs(den) > 1e-9f) t += 0.5f * (a - c) / den;
        }
        if (confidence) *confidence = 1.f - (diff_[best] < 1.f ? diff_[best] : 1.f);
        return sampleRate / t;
    }

private:
    std::vector<float> diff_;
    int window_;
    int maxTau_;
};

}  // namespace jakotata
