package io.github.mpridal.jakotata.core.record

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.view.Surface
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MP4 s videem H.264 (vstup přes Surface z GL rendereru) a zvukem AAC 48 kHz mono (zadání kap. 4).
 * Časové značky obou stop jsou ve stejné časové ose [µs] od začátku nahrávání.
 * Vlastní enkodér místo CameraX VideoCapture – ten by nahrál mikrofon, ne převedený hlas.
 */
class Mp4Recorder(
    fd: FileDescriptor,
    val width: Int,
    val height: Int,
    private val fps: Int = 30,
    private val sampleRate: Int = 48000,
) {
    private val muxer = MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val video: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private val audio: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    val inputSurface: Surface

    private val lock = Object()
    private var videoTrack = -1
    private var audioTrack = -1
    private var muxerStarted = false
    private val pending = ArrayList<Pending>()
    private var lastVideoPts = -1L
    private var lastAudioPts = -1L
    private var videoThread: Thread? = null
    private var stopped = false

    private class Pending(val audio: Boolean, val data: ByteBuffer, val info: MediaCodec.BufferInfo)

    init {
        val vf = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, (width * height * fps * 0.2f).toInt().coerceIn(2_000_000, 12_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        video.configure(vf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = video.createInputSurface()
        val af = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        audio.configure(af, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    }

    fun start() {
        video.start()
        audio.start()
        videoThread = Thread({ drainLoop(video, isAudio = false) }, "JakoVideoEnc").apply { start() }
    }

    /** PCM float → AAC. Volat z jednoho vlákna (pumpa zvuku), pts v µs od začátku nahrávání. */
    fun writeAudio(pcm: FloatArray, n: Int, ptsUs: Long) {
        var offset = 0
        var pts = ptsUs
        while (offset < n) {
            val idx = audio.dequeueInputBuffer(10_000)
            if (idx < 0) { drain(audio, isAudio = true, 0); continue }
            val buf = audio.getInputBuffer(idx) ?: return
            buf.clear()
            buf.order(ByteOrder.nativeOrder())
            val count = minOf(n - offset, buf.capacity() / 2)
            for (i in 0 until count) buf.putShort((pcm[offset + i].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            audio.queueInputBuffer(idx, 0, count * 2, pts, 0)
            offset += count
            pts += count * 1_000_000L / sampleRate
            drain(audio, isAudio = true, 0)
        }
    }

    /** Ukončí obě stopy a zavře soubor (blokuje, než enkodéry dodají zbytek). */
    fun stop(lastAudioPtsUs: Long) {
        synchronized(lock) { if (stopped) return; stopped = true }
        try {
            video.signalEndOfInputStream()
            val idx = audio.dequeueInputBuffer(50_000)
            if (idx >= 0) audio.queueInputBuffer(idx, 0, 0, maxOf(lastAudioPtsUs, 0), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            // Dotáhnout zvuk do EOS.
            var guard = 0
            while (drain(audio, isAudio = true, 10_000) && guard++ < 500) Unit
            videoThread?.join(3000)
        } catch (e: Exception) {
            Log.w(TAG, "Stop failed", e)
        } finally {
            runCatching { video.stop() }; runCatching { video.release() }
            runCatching { audio.stop() }; runCatching { audio.release() }
            runCatching { inputSurface.release() }
            synchronized(lock) {
                if (muxerStarted) runCatching { muxer.stop() }.onFailure { Log.w(TAG, "Muxer stop failed", it) }
                runCatching { muxer.release() }
            }
        }
    }

    private fun drainLoop(codec: MediaCodec, isAudio: Boolean) {
        while (drain(codec, isAudio, 10_000)) Unit
    }

    /** @return false po dosažení konce stopy */
    private fun drain(codec: MediaCodec, isAudio: Boolean, timeoutUs: Long): Boolean {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val idx = try { codec.dequeueOutputBuffer(info, timeoutUs) } catch (e: IllegalStateException) { return false }
            when {
                idx == MediaCodec.INFO_TRY_AGAIN_LATER -> return true
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> synchronized(lock) {
                    val track = muxer.addTrack(codec.outputFormat)
                    if (isAudio) audioTrack = track else videoTrack = track
                    if (videoTrack >= 0 && audioTrack >= 0 && !muxerStarted) {
                        muxer.start()
                        muxerStarted = true
                        pending.forEach { write(it.audio, it.data, it.info) }
                        pending.clear()
                    }
                }
                idx >= 0 -> {
                    val out = codec.getOutputBuffer(idx)
                    val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    if (out != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        synchronized(lock) {
                            if (muxerStarted) write(isAudio, out, info)
                            else {
                                // Před startem muxeru (čeká se na formát druhé stopy) si data zkopírujeme.
                                val copy = ByteBuffer.allocateDirect(info.size)
                                out.position(info.offset); out.limit(info.offset + info.size)
                                copy.put(out); copy.flip()
                                pending.add(Pending(isAudio, copy, MediaCodec.BufferInfo().apply { set(0, info.size, info.presentationTimeUs, info.flags) }))
                            }
                        }
                    }
                    codec.releaseOutputBuffer(idx, false)
                    if (eos) return false
                }
            }
        }
    }

    private fun write(isAudio: Boolean, data: ByteBuffer, info: MediaCodec.BufferInfo) {
        // Časové značky musí v každé stopě růst.
        val last = if (isAudio) lastAudioPts else lastVideoPts
        if (info.presentationTimeUs <= last) info.presentationTimeUs = last + 1
        if (isAudio) lastAudioPts = info.presentationTimeUs else lastVideoPts = info.presentationTimeUs
        data.position(info.offset)
        data.limit(info.offset + info.size)
        muxer.writeSampleData(if (isAudio) audioTrack else videoTrack, data, info)
    }

    private companion object {
        const val TAG = "Mp4Recorder"
    }
}
