package io.github.mpridal.jakotata.core.voice

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Stav Papouška pro UI; hodnota odpovídá C++ `ParrotState`. */
enum class ParrotState { LISTENING, RECORDING, PROCESSING, PLAYING }

/**
 * Převod hlasu v reálném čase (zadání kap. 3): obálka nad C++ enginem (Oboe). Sleduje připojená
 * výstupní zařízení a režim upraví – živý hlas jen v kabelových sluchátkách.
 */
class VoiceEngine(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _output = MutableStateFlow(currentOutput())
    val output: StateFlow<OutputKind> = _output

    private var requested = SoundMode.PARROT
    private val _mode = MutableStateFlow(SoundMode.PARROT)
    /** Skutečně použitý režim (po úpravě podle výstupu). */
    val mode: StateFlow<SoundMode> = _mode

    @Volatile private var running = false
    // Otevírání a zavírání streamů trvá – nikdy na hlavním vlákně.
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "JakoVoiceCtl") }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = onDevicesChanged()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = onDevicesChanged()
    }

    val available: Boolean get() = NativeVoice.loaded

    private var registered = false

    /** Spustí převod hlasu na pozadí (neblokuje). */
    fun start() {
        if (!NativeVoice.loaded) return
        if (!registered) { audio.registerAudioDeviceCallback(deviceCallback, main); registered = true }
        applyMode()
        worker.execute { if (!running) running = NativeVoice.nativeStart() }
    }

    /** Zastaví převod hlasu na pozadí (neblokuje). */
    fun stop() {
        if (registered) { audio.unregisterAudioDeviceCallback(deviceCallback); registered = false }
        worker.execute { if (running) { NativeVoice.nativeStop(); running = false } }
    }

    /** Zastaví a počká na dokončení (před nahráváním hlasu, kdy mikrofon potřebuje AudioRecord). */
    fun stopAndWait() {
        stop()
        worker.submit {}.get()
    }

    fun setMode(mode: SoundMode) { requested = mode; applyMode() }
    fun cycleMode() = setMode(SoundModes.next(_mode.value, _output.value))

    fun setVoiceEnabled(on: Boolean) { if (NativeVoice.loaded) NativeVoice.nativeSetVoiceEnabled(on) }
    fun setAmount(amount: Float) { if (NativeVoice.loaded) NativeVoice.nativeSetAmount(amount) }

    /** Profil hlasu táty; bez profilu ukázkový mužský hlas (110 Hz). */
    fun setProfile(profile: VoiceProfile?) {
        if (NativeVoice.loaded) NativeVoice.nativeSetDad(profile?.toNative())
    }

    fun inputLevel(): Float = if (running) NativeVoice.nativeInputLevel() else 0f
    fun parrotState(): ParrotState = if (running) ParrotState.entries[NativeVoice.nativeParrotState().coerceIn(0, 3)] else ParrotState.LISTENING
    fun kidF0(): Float = if (running) NativeVoice.nativeKidF0() else 0f
    fun liveLatencyMs(): Float = if (running) NativeVoice.nativeLiveLatencyMs() else 0f

    /** Round-trip test latence (cvaknutí z reproduktoru → mikrofon), blokuje až 2 s – volat mimo UI vlákno. */
    fun measureRoundTripMs(): Int = if (running) NativeVoice.nativeMeasureRoundTrip() else -1

    /** Nahrávací cesta převedeného hlasu pro video (zadání kap. 4.1). */
    fun setRecording(on: Boolean) { if (NativeVoice.loaded) NativeVoice.nativeSetRecording(on) }
    fun readRecording(dst: FloatArray): Int = if (running) NativeVoice.nativeReadRecording(dst) else 0
    /** CLOCK_MONOTONIC [ns] prvního nahraného vzorku; 0 dokud není známý. */
    fun recordingStartNs(): Long = if (NativeVoice.loaded) NativeVoice.nativeRecordingStartNs() else 0L
    fun recordingLatencyNs(): Long = if (NativeVoice.loaded) NativeVoice.nativeRecordingLatencyNs() else 0L
    val isRunning: Boolean get() = running

    private fun onDevicesChanged() {
        _output.value = currentOutput()
        applyMode()
    }

    private fun applyMode() {
        val m = SoundModes.effective(requested, _output.value)
        _mode.value = m
        if (NativeVoice.loaded) NativeVoice.nativeSetMode(m.native)
    }

    private fun currentOutput(): OutputKind =
        SoundModes.outputKind(audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type })
}
