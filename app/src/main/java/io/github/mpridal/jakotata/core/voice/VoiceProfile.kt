package io.github.mpridal.jakotata.core.voice

import org.json.JSONArray
import org.json.JSONObject

/** Hlas táty (zadání kap. 3.4): výška, formanty a dlouhodobé spektrum z nahrávky 45–60 s. */
data class VoiceProfile(
    val medianF0: Float,
    val f0P10: Float,
    val f0P90: Float,
    val formants: List<Float>,
    val voicedSeconds: Float,
    val clipFraction: Float,
    val snrDb: Float,
    val bandsDb: List<Float>,
) {
    /** Pro C++: [medianF0, F1, F2, F3, hasBands, pásma…]. */
    fun toNative(): FloatArray = floatArrayOf(medianF0, formants[0], formants[1], formants[2], 1f) + bandsDb.toFloatArray()

    fun toJson(): JSONObject = JSONObject()
        .put("version", 1)
        .put("medianF0", medianF0.toDouble()).put("f0P10", f0P10.toDouble()).put("f0P90", f0P90.toDouble())
        .put("formants", JSONArray(formants.map { it.toDouble() }))
        .put("voicedSeconds", voicedSeconds.toDouble()).put("clipFraction", clipFraction.toDouble())
        .put("snrDb", snrDb.toDouble())
        .put("bandsDb", JSONArray(bandsDb.map { it.toDouble() }))

    /** Kontroly kvality nahrávky (zadání kap. 3.4). */
    enum class Problem { TOO_LITTLE_SPEECH, CLIPPING, NOISY }

    fun problems(): List<Problem> = buildList {
        if (voicedSeconds < 20f) add(Problem.TOO_LITTLE_SPEECH)
        if (clipFraction > 0.001f) add(Problem.CLIPPING)
        if (snrDb < 15f) add(Problem.NOISY)
    }

    companion object {
        const val BANDS = 10

        fun fromAnalysis(a: FloatArray) = VoiceProfile(
            medianF0 = a[0], f0P10 = a[1], f0P90 = a[2],
            formants = listOf(a[3], a[4], a[5]),
            voicedSeconds = a[6], clipFraction = a[7], snrDb = a[8],
            bandsDb = (0 until BANDS).map { a[9 + it] },
        )

        fun fromJson(o: JSONObject): VoiceProfile {
            fun arr(name: String) = o.getJSONArray(name).let { j -> (0 until j.length()).map { j.getDouble(it).toFloat() } }
            return VoiceProfile(
                o.getDouble("medianF0").toFloat(), o.getDouble("f0P10").toFloat(), o.getDouble("f0P90").toFloat(),
                arr("formants"), o.getDouble("voicedSeconds").toFloat(), o.getDouble("clipFraction").toFloat(),
                o.getDouble("snrDb").toFloat(), arr("bandsDb"),
            )
        }
    }
}
