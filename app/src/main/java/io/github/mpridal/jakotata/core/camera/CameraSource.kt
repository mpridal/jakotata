package io.github.mpridal.jakotata.core.camera

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Kamera přes CameraX (zadání kap. 4): náhled do OES textury rendereru a souběžně menší snímky
 * pro sledování obličeje. Obě cesty mají stejný poměr stran 16:9, aby body seděly na obraz.
 */
class CameraSource(private val context: Context) {

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "JakoFace").apply { priority = Thread.NORM_PRIORITY + 1 }
    }
    private var provider: ProcessCameraProvider? = null

    fun bind(
        owner: LifecycleOwner,
        front: Boolean,
        targetRotation: Int,
        previewSurface: Preview.SurfaceProvider,
        analyzer: ImageAnalysis.Analyzer,
        processingSize: Size = Size(1280, 720),
        analysisSize: Size = Size(640, 360),
    ) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val p = future.get().also { provider = it }
            val preview = Preview.Builder()
                .setResolutionSelector(selector(processingSize))
                .setTargetRotation(targetRotation)
                .build()
                .also { it.surfaceProvider = previewSurface }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(selector(analysisSize))
                .setTargetRotation(targetRotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { it.setAnalyzer(analysisExecutor, analyzer) }
            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            try {
                p.unbindAll()
                p.bindToLifecycle(owner, selector, preview, analysis)
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun unbind() {
        provider?.unbindAll()
    }

    fun release() {
        unbind()
        analysisExecutor.shutdown()
    }

    private fun selector(size: Size) = ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
        .setResolutionStrategy(ResolutionStrategy(size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
        .build()

    private companion object {
        const val TAG = "CameraSource"
    }
}
