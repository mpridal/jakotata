package io.github.mpridal.jakotata.core.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Nahrávání hlasu táty pro profil (zadání kap. 3.4): 48 kHz mono, max. 60 s, měřič úrovně.
 * Běží na vlastním vlákně; během nahrávání musí být [VoiceEngine] zastavený.
 */
class VoiceProfileRecorder {
    @Volatile var level: Float = 0f
        private set
    @Volatile var peak: Float = 0f
        private set
    @Volatile var seconds: Float = 0f
        private set

    @Volatile private var recording = false
    private var thread: Thread? = null
    private var samples = FloatArray(0)
    private var length = 0

    @SuppressLint("MissingPermission") // oprávnění kontroluje UI před spuštěním
    fun start(): Boolean {
        if (recording) return true
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        if (minBuf <= 0) return false
        // VOICE_RECOGNITION: vestavěný mikrofon bez AGC a potlačení šumu – věrné spektrum hlasu.
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, minBuf * 4)
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return false }
        samples = FloatArray(SAMPLE_RATE * MAX_SECONDS)
        length = 0
        peak = 0f
        recording = true
        rec.startRecording()
        thread = Thread({
            val buf = FloatArray(SAMPLE_RATE / 50)
            while (recording && length < samples.size) {
                val n = rec.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
                if (n <= 0) continue
                var e = 0f
                for (i in 0 until n) {
                    val v = buf[i]
                    if (length < samples.size) samples[length++] = v
                    e += v * v
                    if (abs(v) > peak) peak = abs(v)
                }
                level = sqrt(e / n)
                seconds = length.toFloat() / SAMPLE_RATE
            }
            rec.stop()
            rec.release()
            recording = false
        }, "JakoVoiceRec").apply { start() }
        return true
    }

    val isRecording: Boolean get() = recording

    /** Zastaví a vrátí nahrané vzorky. */
    fun stop(): FloatArray {
        recording = false
        thread?.join(1000)
        thread = null
        return samples.copyOf(length)
    }

    companion object {
        const val SAMPLE_RATE = 48000
        const val MAX_SECONDS = 60

        /** Analýza nahrávky v C++ (výška, formanty, spektrum, kvalita). */
        fun analyze(pcm: FloatArray): VoiceProfile? =
            if (NativeVoice.loaded && pcm.isNotEmpty()) VoiceProfile.fromAnalysis(NativeVoice.nativeAnalyze(pcm, SAMPLE_RATE)) else null

        /** Uloží 16bit PCM WAV (původní nahrávka do profilu, příprava na fázi 2). */
        fun writeWav(file: File, pcm: FloatArray, sampleRate: Int = SAMPLE_RATE) {
            val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (v in pcm) data.putShort((v.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + pcm.size * 2); put("WAVE".toByteArray())
                put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(sampleRate)
                putInt(sampleRate * 2); putShort(2); putShort(16)
                put("data".toByteArray()); putInt(pcm.size * 2)
            }
            RandomAccessFile(file, "rw").use { f ->
                f.setLength(0)
                f.write(header.array())
                f.write(data.array())
            }
        }
    }
}
