package io.github.mpridal.jakotata.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.mpridal.jakotata.core.camera.CameraSource
import io.github.mpridal.jakotata.core.face.FaceTracker
import io.github.mpridal.jakotata.core.profile.FaceProfile
import io.github.mpridal.jakotata.core.profile.FaceProfileBuilder
import io.github.mpridal.jakotata.core.profile.ProfileStore
import io.github.mpridal.jakotata.core.render.CameraRenderer
import io.github.mpridal.jakotata.core.render.MaskTopology
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.max

/**
 * Drží `core` objekty kamery, obličeje, renderu a profilu pro obrazovku kamery. UI volá jen tyto
 * metody, ne MediaPipe ani GL přímo (zadání kap. 4.2).
 */
class CameraState(private val context: Context) {
    val tracker = FaceTracker(context)
    val renderer = CameraRenderer(
        faceSource = { tracker.latest.get() },
        meshEdges = tracker.meshEdges,
        maskTopology = MaskTopology(tracker.meshTriangles, tracker.ovalLoop, tracker.leftEyeLoop, tracker.rightEyeLoop),
        kidSkinSource = { tracker.kidSkin.get() },
    )
    val camera = CameraSource(context)
    private val store = ProfileStore(context)

    var profile: FaceProfile? by mutableStateOf(null)
        private set

    suspend fun loadProfile() {
        val p = withContext(Dispatchers.IO) { store.load() }
        profile = p
        renderer.setProfile(p)
    }

    /** Aktuální snímek kamery → profil (neuloží ho). */
    suspend fun profileFromCamera(name: String): FaceProfileBuilder.Result? {
        val frame = suspendCancellableCoroutine<Bitmap?> { cont -> renderer.captureFrame { cont.resume(it) } } ?: return null
        return build(frame, name)
    }

    suspend fun profileFromUri(uri: Uri, name: String): FaceProfileBuilder.Result? {
        val bitmap = withContext(Dispatchers.IO) { decode(uri) } ?: return null
        return build(bitmap, name)
    }

    suspend fun saveProfile(p: FaceProfile) {
        withContext(Dispatchers.IO) { store.save(p) }
        profile = p
        renderer.setProfile(p)
    }

    private suspend fun build(bitmap: Bitmap, name: String) = withContext(Dispatchers.Default) {
        FaceProfileBuilder(context).use { it.build(bitmap, name) }
    }

    private fun decode(uri: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_PHOTO) {
                    val s = MAX_PHOTO.toFloat() / longest
                    decoder.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PHOTO) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
    } catch (e: Exception) {
        null
    }

    fun release() {
        camera.release()
        renderer.release()
        tracker.close()
    }

    private companion object {
        const val MAX_PHOTO = 1600
    }
}

@Composable
fun rememberCameraState(): CameraState {
    val context = LocalContext.current
    val state = remember { CameraState(context) }
    DisposableEffect(state) { onDispose { state.release() } }
    return state
}
