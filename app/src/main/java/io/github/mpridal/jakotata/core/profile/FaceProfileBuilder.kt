package io.github.mpridal.jakotata.core.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import io.github.mpridal.jakotata.core.face.FaceTopology
import io.github.mpridal.jakotata.core.face.SkinColor
import java.io.Closeable
import kotlin.math.max

/**
 * Z fotky táty udělá [FaceProfile] (zadání kap. 2.2): najde obličej, zkontroluje, že je zepředu,
 * oči otevřené a pusa zavřená, vyřízne čtverec kolem obličeje a spočítá barvu kůže.
 */
class FaceProfileBuilder(context: Context) : Closeable {

    sealed interface Result {
        class Ok(val profile: FaceProfile) : Result
        data object NoFace : Result
        data object NotFrontal : Result
        data object EyesClosed : Result
        data object MouthOpen : Result
    }

    private val landmarker: FaceLandmarker = FaceLandmarker.createFromOptions(
        context.applicationContext,
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(1)
            .setOutputFaceBlendshapes(true)
            .setOutputFacialTransformationMatrixes(true)
            .build(),
    )

    /** Pomalé (stovky ms) – volat mimo hlavní vlákno. */
    fun build(photo: Bitmap, name: String): Result {
        val src = if (photo.config == Bitmap.Config.ARGB_8888) photo else photo.copy(Bitmap.Config.ARGB_8888, false)
        val result = landmarker.detect(BitmapImageBuilder(src).build())
        val landmarks = result.faceLandmarks().firstOrNull() ?: return Result.NoFace

        val matrix = result.facialTransformationMatrixes().orElse(emptyList()).firstOrNull()
        if (matrix != null && FaceTopology.headTiltDegrees(matrix) > MAX_TILT_DEG) return Result.NotFrontal
        val shapes = result.faceBlendshapes().orElse(emptyList()).firstOrNull().orEmpty()
            .associate { it.categoryName() to it.score() }
        if ((shapes["eyeBlinkLeft"] ?: 0f) > 0.5f || (shapes["eyeBlinkRight"] ?: 0f) > 0.5f) return Result.EyesClosed
        if ((shapes["jawOpen"] ?: 0f) > 0.2f) return Result.MouthOpen

        // Čtverec kolem obličeje (1,5× větší strana), převzorkovaný na 1024 × 1024.
        val n = FaceTopology.MESH_VERTICES
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in 0 until n) {
            val x = landmarks[i].x() * src.width; val y = landmarks[i].y() * src.height
            minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y)
        }
        val side = max(maxX - minX, maxY - minY) * 1.5f
        val left = (minX + maxX) / 2f - side / 2f
        val top = (minY + maxY) / 2f - side / 2f
        val scale = TEXTURE_SIZE / side

        val texture = Bitmap.createBitmap(TEXTURE_SIZE, TEXTURE_SIZE, Bitmap.Config.ARGB_8888)
        Canvas(texture).apply {
            drawColor(Color.BLACK)
            drawBitmap(src, Matrix().apply { postTranslate(-left, -top); postScale(scale, scale) }, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val coords = FloatArray(n * 3)
        for (i in 0 until n) {
            coords[i * 3] = (landmarks[i].x() * src.width - left) / side
            coords[i * 3 + 1] = (landmarks[i].y() * src.height - top) / side
            coords[i * 3 + 2] = landmarks[i].z()
        }
        val pixels = IntArray(TEXTURE_SIZE * TEXTURE_SIZE).also { texture.getPixels(it, 0, TEXTURE_SIZE, 0, 0, TEXTURE_SIZE, TEXTURE_SIZE) }
        val skin = SkinColor.stats(pixels, TEXTURE_SIZE, TEXTURE_SIZE, coords, FaceTopology.SKIN_SAMPLES) ?: return Result.NoFace
        return Result.Ok(FaceProfile(name, texture, coords, skin, System.currentTimeMillis()))
    }

    override fun close() = landmarker.close()

    private companion object {
        const val TEXTURE_SIZE = 1024
        const val MAX_TILT_DEG = 15f
    }
}
