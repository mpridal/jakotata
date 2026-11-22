package io.github.mpridal.jakotata.core.record

import android.net.Uri
import android.util.Log
import io.github.mpridal.jakotata.core.render.CameraRenderer
import io.github.mpridal.jakotata.core.voice.VoiceEngine

/**
 * Jedno nahrávání videa (zadání kap. 4.1 a 6.4): obraz z GL rendereru do enkodéru, převedený
 * hlas z C++ enginu do AAC. Společná časová osa = CLOCK_MONOTONIC od [baseNs]; zvuk má odečtenou
 * latenci převodu, aby pusa seděla se zvukem.
 */
class RecordingSession(
    private val saver: MediaSaver,
    private val renderer: CameraRenderer,
    private val voice: VoiceEngine,
    private val width: Int,
    private val height: Int,
) {
    private var target: MediaSaver.Target? = null
    private var recorder: Mp4Recorder? = null
    private var pump: Thread? = null
    @Volatile private var running = false
    private var lastAudioPtsUs = 0L
    val baseNs: Long = System.nanoTime()

    fun start(): Boolean {
        val t = saver.newVideo() ?: return false
        return try {
            val r = Mp4Recorder(t.pfd.fileDescriptor, width, height)
            r.start()
            target = t
            recorder = r
            running = true
            voice.setRecording(true)
            renderer.startRecording(r.inputSurface, width, height, baseNs)
            pump = Thread(::pumpAudio, "JakoAudioPump").apply { start() }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Recording start failed", e)
            saver.abandon(t)
            false
        }
    }

    /** Zastaví nahrávání a uloží video; vrací jeho Uri, nebo null při chybě. Blokuje (volat mimo UI vlákno). */
    fun stop(): Uri? {
        if (!running) return null
        running = false
        renderer.stopRecording()
        voice.setRecording(false)
        pump?.join(2000)
        val t = target ?: return null
        return try {
            recorder?.stop(lastAudioPtsUs)
            saver.finish(t)
            t.uri
        } catch (e: Exception) {
            Log.e(TAG, "Recording stop failed", e)
            saver.abandon(t)
            null
        }
    }

    private fun pumpAudio() {
        val buf = FloatArray(4800)
        val sr = 48000
        var samples = 0L
        val silent = !voice.isRunning
        while (running) {
            val r = recorder ?: break
            if (silent) {
                // Bez mikrofonu (oprávnění) tichá stopa podle hodin, ať je MP4 platné se 2 stopami.
                Thread.sleep(20)
                val n = 960
                java.util.Arrays.fill(buf, 0, n, 0f)
                val pts = samples * 1_000_000L / sr
                r.writeAudio(buf, n, pts)
                samples += n
                lastAudioPtsUs = pts
                continue
            }
            val start = voice.recordingStartNs()
            val n = voice.readRecording(buf)
            if (n == 0 || start == 0L) { Thread.sleep(10); continue }
            val ptsNs = start - voice.recordingLatencyNs() + samples * 1_000_000_000L / sr - baseNs
            samples += n
            if (ptsNs < 0) continue  // vzorky z doby před začátkem nahrávání
            lastAudioPtsUs = ptsNs / 1000
            r.writeAudio(buf, n, lastAudioPtsUs)
        }
    }

    private companion object {
        const val TAG = "RecordingSession"
    }
}
