package io.github.mpridal.jakotata.ui.camera

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Živý obraz kamery s maskou a (debug) sítí bodů. */
@Composable
fun CameraPreview(
    state: CameraState,
    front: Boolean,
    showMesh: Boolean,
    maskEnabled: Boolean,
    maskAmount: Float,
    dadEyes: Boolean,
    imageMode: io.github.mpridal.jakotata.core.render.ImageMode,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val rotation = LocalView.current.display?.rotation ?: 0
    val renderer = state.renderer

    renderer.mirror = front
    renderer.showMesh = showMesh
    renderer.maskEnabled = maskEnabled
    renderer.maskAmount = maskAmount
    renderer.dadEyes = dadEyes
    renderer.imageMode = imageMode

    DisposableEffect(lifecycleOwner, front, rotation) {
        state.tracker.latest.set(null)
        state.camera.bind(
            lifecycleOwner, front, rotation, renderer.surfaceProvider, state.tracker,
            onSensorRotation = { renderer.sensorRotationDegrees = it },
        )
        onDispose { state.camera.unbind() }
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
