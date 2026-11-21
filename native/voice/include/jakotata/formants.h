#pragma once

#include <cmath>
#include <vector>

#include "jakotata/dsp.h"

namespace jakotata {

/**
 * Odhad formantů F1–F3 z LPC obálky (zadání kap. 3.1): preemfáze, Hammingovo okno, autokorelace,
 * Levinson–Durbin a vrcholy obálky 1/|A(e^jω)| na mřížce frekvencí. Bez alokací po konstrukci.
 */
class FormantEstimator {
public:
    FormantEstimator(int maxWindow, int order = 12, int bins = 240)
        : buf_(maxWindow), r_(order + 1), a_(order + 1), tmp_(order + 1), env_(bins), order_(order), bins_(bins) {}

    /** @return počet nalezených formantů (0–3), hodnoty v Hz do out[0..2] */
    int estimate(const float* x, int n, float sampleRate, float* out) {
        if (n > static_cast<int>(buf_.size())) n = static_cast<int>(buf_.size());
        if (n < order_ * 4) return 0;
        // Preemfáze + okno.
        float prev = x[0];
        for (int i = 0; i < n; ++i) {
            float e = x[i] - 0.97f * prev;
            prev = x[i];
            float w = 0.54f - 0.46f * std::cos(2.f * kPi * i / (n - 1));
            buf_[i] = e * w;
        }
        for (int k = 0; k <= order_; ++k) {
            double s = 0;
            for (int i = k; i < n; ++i) s += static_cast<double>(buf_[i]) * buf_[i - k];
            r_[k] = static_cast<float>(s);
        }
        if (r_[0] <= 1e-9f) return 0;
        r_[0] *= 1.0001f;  // mírná regularizace
        // Levinson–Durbin.
        std::fill(a_.begin(), a_.end(), 0.f);
        a_[0] = 1.f;
        float err = r_[0];
        for (int i = 1; i <= order_; ++i) {
            float acc = r_[i];
            for (int j = 1; j < i; ++j) acc += a_[j] * r_[i - j];
            float k = -acc / err;
            for (int j = 0; j <= i; ++j) tmp_[j] = a_[j];
            for (int j = 1; j < i; ++j) a_[j] = tmp_[j] + k * tmp_[i - j];
            a_[i] = k;
            err *= (1.f - k * k);
            if (err <= 0.f) return 0;
        }
        // Obálka na mřížce fmin–fmax.
        const float fmin = 150.f, fmax = std::min(4000.f, sampleRate * 0.45f);
        for (int b = 0; b < bins_; ++b) {
            float f = fmin + (fmax - fmin) * b / (bins_ - 1);
            float w = 2.f * kPi * f / sampleRate;
            float re = 0, im = 0;
            for (int j = 0; j <= order_; ++j) {
                re += a_[j] * std::cos(w * j);
                im -= a_[j] * std::sin(w * j);
            }
            env_[b] = 1.f / std::max(re * re + im * im, 1e-12f);
        }
        int found = 0;
        for (int b = 1; b + 1 < bins_ && found < 3; ++b) {
            if (env_[b] > env_[b - 1] && env_[b] >= env_[b + 1]) {
                // Parabolická interpolace vrcholu v log měřítku.
                float l = std::log(env_[b - 1]), c = std::log(env_[b]), rr = std::log(env_[b + 1]);
                float den = l - 2 * c + rr;
                float off = std::fabs(den) > 1e-9f ? 0.5f * (l - rr) / den : 0.f;
                float f = fmin + (fmax - fmin) * (b + off) / (bins_ - 1);
                if (f > 200.f) out[found++] = f;
            }
        }
        return found;
    }

private:
    std::vector<float> buf_, r_, a_, tmp_, env_;
    int order_;
    int bins_;
};

}  // namespace jakotata
