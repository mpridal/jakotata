package io.github.mpridal.jakotata.core.voice

/**
 * Kotlin brána do C++ zvukového jádra (`native/voice`). UI nevolá JNI přímo, jen přes tuto vrstvu
 * (zadání kap. 4.2). V milníku 1 jen ověřuje, že se knihovna sestaví a načte.
 */
object NativeVoice {
    private val loaded: Boolean = runCatching { System.loadLibrary("jakotata_voice") }.isSuccess

    fun versionOrNull(): String? = if (loaded) nativeVersion() else null

    private external fun nativeVersion(): String
}
