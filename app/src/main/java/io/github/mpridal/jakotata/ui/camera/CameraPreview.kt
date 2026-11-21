package io.github.mpridal.jakotata.ui.camera

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.mpridal.jakotata.core.camera.CameraSource
import io.github.mpridal.jakotata.core.face.FaceTracker
import io.github.mpridal.jakotata.core.render.CameraRenderer

/**
 * Živý obraz kamery s rendererem a sledováním obličeje. Jediné místo, kde UI drží `core` objekty
 * kamery, obličeje a renderu – samo MediaPipe ani GL nevolá (zadání kap. 4.2).
 */
@Composable
fun CameraPreview(
    front: Boolean,
    showMesh: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val rotation = LocalView.current.display?.rotation ?: 0

    val tracker = remember { FaceTracker(context) }
    val renderer = remember { CameraRenderer({ tracker.latest.get() }, tracker.meshEdges) }
    val camera = remember { CameraSource(context) }

    renderer.mirror = front
    renderer.showMesh = showMesh

    DisposableEffect(Unit) {
        onDispose {
            camera.release()
            renderer.release()
            tracker.close()
        }
    }

    DisposableEffect(lifecycleOwner, front, rotation) {
        tracker.latest.set(null)
        camera.bind(lifecycleOwner, front, rotation, renderer.surfaceProvider, tracker, onSensorRotation = { renderer.sensorRotationDegrees = it })
        onDispose { camera.unbind() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        renderer.attachOutput(st, w, h)
                    }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                        renderer.resizeOutput(w, h)
                    }
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        renderer.detachOutput()
                        return true
                    }
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        },
    )
}
