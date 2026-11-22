package io.github.mpridal.jakotata.core.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import io.github.mpridal.jakotata.core.PerfStats
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Body obličeje jednoho snímku. Souřadnice jsou normalizované (0–1) v narovnaném obraze kamery
 * (osa y dolů, bez zrcadlení): pro každý obličej pole [x0, y0, z0, x1, y1, z1, …] s 478 body.
 */
class FaceFrame(
    val faces: List<FloatArray>,
    /** 52 blendshapů na obličej (pořadí podle MediaPipe), prázdné pokud nejsou. */
    val blendshapes: List<FloatArray>,
    /** Transformační matice hlavy 4×4 (column-major) na obličej. */
    val matrices: List<FloatArray>,
    val timestampNs: Long,
    /** Poměr stran (šířka/výška) narovnaného snímku, na kterém detekce běžela. */
    val imageAspect: Float,
    /** Čas pořízení snímku kamerou [ns] – stejná časová osa jako časové značky náhledu. */
    val captureNs: Long = 0L,
    /** Rychlost bodů [jednotek/ns] na obličej pro předpověď pohybu (kompenzace zpoždění detekce). */
    val velocities: List<FloatArray> = emptyList(),
) {
    /**
     * Body obličeje [face] posunuté dopředu do času [targetNs] podle rychlosti (max. 120 ms).
     * Detekce trvá desítky ms, takže bez předpovědi maska za obličejem viditelně zaostává.
     */
    fun predicted(face: Int, targetNs: Long, out: FloatArray): FloatArray {
        val src = faces[face]
        val v = velocities.getOrNull(face)
        val dt = (targetNs - captureNs).coerceIn(0L, 120_000_000L).toFloat()
        if (v == null || captureNs == 0L || dt == 0f || v.size != src.size || out.size < src.size) return src
        for (i in src.indices) out[i] = src[i] + v[i] * dt
        return out
    }
}

/**
 * Sledování obličeje přes MediaPipe Face Landmarker v režimu LIVE_STREAM (zadání kap. 2.1).
 * Model `face_landmarker.task` je v assets APK. GPU delegate, při chybě CPU.
 * Poslední výsledek je v [latest]; čte ho vlákno renderu.
 */
class FaceTracker(context: Context, private val maxFaces: Int = 1) : ImageAnalysis.Analyzer, Closeable {

    val latest = AtomicReference<FaceFrame?>(null)

    /** Hrany standardní teselace obličeje MediaPipe: dvojice indexů bodů [a0, b0, a1, b1, …]. */
    val meshEdges: IntArray = FaceLandmarker.FACE_LANDMARKS_TESSELATION
        .flatMap { listOf(it.start(), it.end()) }
        .toIntArray()

    /** Trojúhelníky sítě (indexy vrcholů po trojicích) pro vykreslení masky. */
    val meshTriangles: IntArray = FaceTopology.trianglesFromEdges(meshEdges)
    val ovalLoop: IntArray = FaceTopology.loopFromEdges(edges(FaceLandmarker.FACE_LANDMARKS_FACE_OVAL))
    val leftEyeLoop: IntArray = FaceTopology.loopFromEdges(edges(FaceLandmarker.FACE_LANDMARKS_LEFT_EYE))
    val rightEyeLoop: IntArray = FaceTopology.loopFromEdges(edges(FaceLandmarker.FACE_LANDMARKS_RIGHT_EYE))

    /** Barva kůže dcery (vyhlazená v čase) pro barevné sladění masky. */
    val kidSkin = AtomicReference<SkinStats?>(null)
    private val pendingBitmaps = ConcurrentHashMap<Long, Bitmap>()
    private var pixelBuffer = IntArray(0)
    private var skinFrame = 0

    private val smoothers = Array(2) { LandmarkSmoother() }
    private val startTimes = ConcurrentHashMap<Long, Long>()
    private val aspects = ConcurrentHashMap<Long, Float>()
    private val captureTimes = ConcurrentHashMap<Long, Long>()
    private var prevFaces: List<FloatArray> = emptyList()
    private var prevVel: List<FloatArray> = emptyList()
    private var prevCaptureNs = 0L
    private var lastTimestampMs = 0L
    private val appContext = context.applicationContext
    private val lock = Any()
    private var closed = false

    // Model se načte až na vlákně analýzy, ne na hlavním vlákně (rychlý start, zadání kap. 5).
    private var landmarker: FaceLandmarker? = null

    private fun create(context: Context, maxFaces: Int): FaceLandmarker {
        fun build(delegate: Delegate): FaceLandmarker {
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(MODEL)
                        .setDelegate(delegate)
                        .build(),
                )
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(maxFaces)
                .setOutputFaceBlendshapes(true)
                .setOutputFacialTransformationMatrixes(true)
                .setResultListener(::onResult)
                .setErrorListener { e -> Log.w(TAG, "Face landmarker error", e) }
                .build()
            return FaceLandmarker.createFromOptions(context, options)
        }
        return try {
            build(Delegate.GPU).also { PerfStats.delegate = "GPU" }
        } catch (e: Exception) {
            Log.w(TAG, "GPU delegate unavailable, falling back to CPU", e)
            build(Delegate.CPU).also { PerfStats.delegate = "CPU" }
        }
    }

    override fun analyze(image: ImageProxy) {
        image.use {
            // Časové značky pro LIVE_STREAM musí přísně růst.
            val ts = maxOf(SystemClock.uptimeMillis(), lastTimestampMs + 1)
            lastTimestampMs = ts
            // Snímek narovnáme sami: MediaPipe s rotací v ImageProcessingOptions vrací body
            // normalizované se zaměněnými rozměry (síť byla protažená v poměru 9:16).
            val rot = it.imageInfo.rotationDegrees
            val raw = it.toBitmap()
            val bitmap = if (rot != 0) {
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rot.toFloat()) }, false)
            } else raw
            aspects[ts] = bitmap.width.toFloat() / bitmap.height
            captureTimes[ts] = it.imageInfo.timestamp
            pendingBitmaps[ts] = bitmap
            PerfStats.analysisSize = "${it.width}x${it.height}r$rot"
            synchronized(lock) {
                if (closed) return
                val lm = landmarker ?: create(appContext, maxFaces).also { landmarker = it }
                startTimes[ts] = System.nanoTime()
                lm.detectAsync(BitmapImageBuilder(bitmap).build(), ts)
            }
        }
    }

    private fun onResult(result: FaceLandmarkerResult, @Suppress("UNUSED_PARAMETER") input: com.google.mediapipe.framework.image.MPImage) {
        val now = System.nanoTime()
        val started = startTimes.remove(result.timestampMs()) ?: now
        val aspect = aspects.remove(result.timestampMs()) ?: 0f
        val captureNs = captureTimes.remove(result.timestampMs()) ?: 0L
        captureTimes.keys.filter { it < result.timestampMs() }.forEach { captureTimes.remove(it) }
        val bitmap = pendingBitmaps.remove(result.timestampMs())
        pendingBitmaps.keys.filter { it < result.timestampMs() }.forEach { pendingBitmaps.remove(it) }
        val faces = result.faceLandmarks().take(smoothers.size).mapIndexed { i, landmarks ->
            val coords = FloatArray(landmarks.size * 3)
            landmarks.forEachIndexed { j, p ->
                coords[j * 3] = p.x(); coords[j * 3 + 1] = p.y(); coords[j * 3 + 2] = p.z()
            }
            smoothers[i].smooth(coords, now)
            coords
        }
        for (i in faces.size until smoothers.size) smoothers[i].reset()
        val blendshapes = result.faceBlendshapes().orElse(emptyList()).map { cats ->
            FloatArray(cats.size) { k -> cats[k].score() }
        }
        val matrices = result.facialTransformationMatrixes().orElse(emptyList())
        // Rychlost bodů mezi posledními dvěma detekcemi (vyhlazená), pro předpověď v rendereru.
        val dtNs = (captureNs - prevCaptureNs).toFloat()
        val velocities = faces.mapIndexed { i, f ->
            val prev = prevFaces.getOrNull(i)
            val pv = prevVel.getOrNull(i)
            if (prev == null || prev.size != f.size || dtNs <= 0f || dtNs > 200_000_000f) FloatArray(f.size)
            else FloatArray(f.size) { k ->
                val v = (f[k] - prev[k]) / dtNs
                if (pv != null && pv.size == f.size) pv[k] * 0.4f + v * 0.6f else v
            }
        }
        prevFaces = faces; prevVel = velocities; prevCaptureNs = captureNs
        latest.set(FaceFrame(faces, blendshapes, matrices, now, aspect, captureNs, velocities))
        // Barva kůže stačí každý 3. snímek (šetří CPU), vyhlazená klouzavým průměrem.
        if (bitmap != null && faces.isNotEmpty() && skinFrame++ % 3 == 0) updateKidSkin(bitmap, faces[0])
        PerfStats.onDetection(now - started, faces.size)
    }

    private fun updateKidSkin(bitmap: Bitmap, coords: FloatArray) {
        val n = bitmap.width * bitmap.height
        if (pixelBuffer.size != n) pixelBuffer = IntArray(n)
        bitmap.getPixels(pixelBuffer, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val s = SkinColor.stats(pixelBuffer, bitmap.width, bitmap.height, coords, FaceTopology.SKIN_SAMPLES) ?: return
        val prev = kidSkin.get()
        kidSkin.set(prev?.blend(s, 0.2f) ?: s)
    }

    private fun edges(connections: Collection<com.google.mediapipe.tasks.components.containers.Connection>): IntArray =
        connections.flatMap { listOf(it.start(), it.end()) }.toIntArray()

    override fun close() {
        synchronized(lock) {
            closed = true
            landmarker?.close()
            landmarker = null
        }
    }

    private companion object {
        const val TAG = "FaceTracker"
        const val MODEL = "face_landmarker.task"
    }
}
