package io.github.mpridal.jakotata.core.voice

/**
 * JNI brána do C++ zvukového jádra (`native/voice`). Používá ji jen [VoiceEngine] a
 * [VoiceProfileRecorder]; UI ji nevolá přímo (zadání kap. 4.2).
 */
internal object NativeVoice {
    val loaded: Boolean = runCatching { System.loadLibrary("jakotata_voice") }.isSuccess

    fun versionOrNull(): String? = if (loaded) nativeVersion() else null

    external fun nativeVersion(): String
    external fun nativeStart(): Boolean
    external fun nativeStop()
    external fun nativeSetMode(mode: Int)
    external fun nativeSetVoiceEnabled(on: Boolean)
    external fun nativeSetAmount(amount: Float)
    external fun nativeSetDad(data: FloatArray?)
    external fun nativeInputLevel(): Float
    external fun nativeParrotState(): Int
    external fun nativeKidF0(): Float
    external fun nativeLiveLatencyMs(): Float
    external fun nativeMeasureRoundTrip(): Int
    external fun nativeAnalyze(pcm: FloatArray, sampleRate: Int): FloatArray
    external fun nativeSetRecording(on: Boolean)
    external fun nativeRecordingStartNs(): Long
    external fun nativeRecordingLatencyNs(): Long
    external fun nativeReadRecording(dst: FloatArray): Int
}
