package io.github.mpridal.jakotata.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Výkonové údaje pro debug overlay (zadání kap. 5 a 6.6). */
data class PerfSnapshot(
    val renderFps: Float = 0f,
    val detectFps: Float = 0f,
    /** Průměrná doba detekce bodů obličeje [ms]. */
    val detectMs: Float = 0f,
    /** Průměrné stáří snímku kamery v okamžiku vykreslení [ms] (od expozice senzoru). */
    val frameAgeMs: Float = 0f,
    val faces: Int = 0,
    val delegate: String = "",
    val analysisSize: String = "",
)

/** Počítadla plněná z vláken renderu a detekce; [sample] je volá jednou za čas z UI. */
object PerfStats {
    private val renderFrames = AtomicInteger()
    private val detectFrames = AtomicInteger()
    private val detectNsSum = AtomicLong()
    private val frameAgeNsSum = AtomicLong()
    @Volatile var faces = 0
    @Volatile var delegate = ""
    @Volatile var analysisSize = ""
    private var lastSampleNs = System.nanoTime()

    private val _snapshot = MutableStateFlow(PerfSnapshot())
    val snapshot: StateFlow<PerfSnapshot> = _snapshot

    fun onFrameRendered(frameAgeNs: Long) {
        renderFrames.incrementAndGet()
        frameAgeNsSum.addAndGet(frameAgeNs)
    }

    fun onDetection(durationNs: Long, faceCount: Int) {
        detectFrames.incrementAndGet()
        detectNsSum.addAndGet(durationNs)
        faces = faceCount
    }

    fun sample() {
        val now = System.nanoTime()
        val dt = (now - lastSampleNs) / 1e9f
        lastSampleNs = now
        if (dt <= 0f) return
        val r = renderFrames.getAndSet(0)
        val d = detectFrames.getAndSet(0)
        val dNs = detectNsSum.getAndSet(0)
        val ageNs = frameAgeNsSum.getAndSet(0)
        _snapshot.value = PerfSnapshot(
            renderFps = r / dt,
            detectFps = d / dt,
            detectMs = if (d > 0) dNs / d / 1e6f else 0f,
            frameAgeMs = if (r > 0) ageNs / r / 1e6f else 0f,
            faces = faces,
            delegate = delegate,
            analysisSize = analysisSize,
        )
    }
}
