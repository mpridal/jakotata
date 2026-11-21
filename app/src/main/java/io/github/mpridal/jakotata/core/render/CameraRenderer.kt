package io.github.mpridal.jakotata.core.render

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import io.github.mpridal.jakotata.core.PerfStats
import io.github.mpridal.jakotata.core.face.FaceFrame
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor

/**
 * Vykreslování na vlastním EGL vlákně (zadání kap. 4): snímek kamery jako externí OES textura
 * přes celou plochu, nad ním (debug) síť bodů obličeje. Kontext má EGL_RECORDABLE_ANDROID, aby
 * ho v milníku 5 mohl sdílet enkodér videa.
 *
 * Výstup je SurfaceTexture z TextureView (kvůli skleněnému rozmazání v Compose, které SurfaceView neumí).
 */
class CameraRenderer(
    private val faceSource: () -> FaceFrame?,
    private val meshEdges: IntArray,
) {
    @Volatile var mirror: Boolean = true
    @Volatile var showMesh: Boolean = true

    private val thread = HandlerThread("JakoGL").apply { start() }
    private val handler = Handler(thread.looper)
    private val executor = Executor { handler.post(it) }

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var pbuffer: EGLSurface = EGL14.EGL_NO_SURFACE
    private var window: EGLSurface = EGL14.EGL_NO_SURFACE
    private var viewW = 0
    private var viewH = 0

    // Aktuální vstup z kamery.
    private var cameraTexId = 0
    private var cameraTexture: SurfaceTexture? = null
    private var bufferW = 0
    private var bufferH = 0
    @Volatile private var rotation = 0
    private val texMatrix = FloatArray(16)
    private var hasFrame = false

    private var cameraProgram = 0
    private var meshProgram = 0
    private val quad = GlUtil.floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val meshVertices = GlUtil.floatBuffer(MAX_EDGES * 4)

    init {
        handler.post { initGl() }
    }

    /** CameraX dodá buffer náhledu sem. */
    val surfaceProvider = Preview.SurfaceProvider { request -> handler.post { onSurfaceRequest(request) } }

    fun attachOutput(output: SurfaceTexture, width: Int, height: Int) = handler.post {
        releaseWindow()
        window = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, output, intArrayOf(EGL14.EGL_NONE), 0)
        viewW = width; viewH = height
        draw()
    }

    fun resizeOutput(width: Int, height: Int) = handler.post { viewW = width; viewH = height; draw() }

    /** Synchronně – TextureView smí uvolnit SurfaceTexture až po návratu. */
    fun detachOutput() = runBlockingOnGl { releaseWindow() }

    fun release() {
        runBlockingOnGl {
            releaseWindow()
            cameraTexture?.release()
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(eglDisplay, pbuffer)
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
        thread.quitSafely()
    }

    private fun runBlockingOnGl(block: () -> Unit) {
        if (Thread.currentThread() == thread) { block(); return }
        val latch = CountDownLatch(1)
        if (handler.post { try { block() } finally { latch.countDown() } }) latch.await()
    }

    private fun initGl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "eglInitialize failed" }
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        check(EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, num, 0) && num[0] > 0) { "No EGL config" }
        eglConfig = configs[0]
        eglContext = EGL14.eglCreateContext(
            eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0,
        )
        pbuffer = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(eglDisplay, pbuffer, pbuffer, eglContext)
        cameraProgram = GlUtil.program(CAMERA_VS, CAMERA_FS)
        meshProgram = GlUtil.program(MESH_VS, MESH_FS)
    }

    private fun onSurfaceRequest(request: SurfaceRequest) {
        makeCurrent()
        val tex = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0])
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        val st = SurfaceTexture(tex[0])
        val res = request.resolution
        st.setDefaultBufferSize(res.width, res.height)
        st.setOnFrameAvailableListener({ draw() }, handler)
        val surface = Surface(st)

        val oldTexture = cameraTexture
        val oldTexId = cameraTexId
        cameraTexture = st; cameraTexId = tex[0]
        bufferW = res.width; bufferH = res.height
        hasFrame = false

        request.setTransformationInfoListener(executor) { info -> rotation = info.rotationDegrees }
        request.provideSurface(surface, executor) {
            surface.release()
            if (cameraTexture !== st) {
                st.release()
                GLES30.glDeleteTextures(1, intArrayOf(tex[0]), 0)
            }
        }
        // Předchozí vstup (jiná kamera) se uvolní, až ho CameraX pustí; jen přestaneme kreslit.
        if (oldTexture != null && oldTexId != 0) Log.d(TAG, "Camera input replaced")
    }

    private fun makeCurrent() {
        val s = if (window != EGL14.EGL_NO_SURFACE) window else pbuffer
        EGL14.eglMakeCurrent(eglDisplay, s, s, eglContext)
    }

    private fun releaseWindow() {
        if (window != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(eglDisplay, pbuffer, pbuffer, eglContext)
            EGL14.eglDestroySurface(eglDisplay, window)
            window = EGL14.EGL_NO_SURFACE
        }
    }

    private fun draw() {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) return
        makeCurrent()
        val st = cameraTexture
        if (st != null) {
            try {
                st.updateTexImage()
                st.getTransformMatrix(texMatrix)
                hasFrame = true
            } catch (e: RuntimeException) {
                Log.w(TAG, "updateTexImage failed", e)
            }
        }
        if (window == EGL14.EGL_NO_SURFACE || viewW == 0) return

        GLES30.glViewport(0, 0, viewW, viewH)
        GLES30.glClearColor(0.17f, 0.14f, 0.2f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        val mapping = DisplayMapping.cover(bufferW, bufferH, rotation, viewW, viewH, mirror)
        if (st != null && hasFrame) drawCamera(mapping)
        if (showMesh) drawMesh(mapping)
        EGL14.eglSwapBuffers(eglDisplay, window)

        if (st != null && hasFrame) {
            val age = System.nanoTime() - st.timestamp
            PerfStats.onFrameRendered(if (age in 0..1_000_000_000L) age else 0L)
        }
    }

    private fun drawCamera(m: DisplayMapping) {
        GLES30.glUseProgram(cameraProgram)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(cameraProgram, "uTex"), 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(cameraProgram, "uTexMatrix"), 1, false, texMatrix, 0)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(cameraProgram, "uScale"), m.scaleX, m.scaleY)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(cameraProgram, "uMirror"), if (m.mirror) 1f else 0f)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(cameraProgram, "uRotation"), rotation)
        val loc = GLES30.glGetAttribLocation(cameraProgram, "aPos")
        GLES30.glEnableVertexAttribArray(loc)
        quad.position(0)
        GLES30.glVertexAttribPointer(loc, 2, GLES30.GL_FLOAT, false, 0, quad)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(loc)
    }

    private fun drawMesh(m: DisplayMapping) {
        val frame = faceSource() ?: return
        if (frame.faces.isEmpty()) return
        GLES30.glUseProgram(meshProgram)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(meshProgram, "uScale"), m.scaleX, m.scaleY)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(meshProgram, "uMirror"), if (m.mirror) 1f else 0f)
        GLES30.glUniform4f(GLES30.glGetUniformLocation(meshProgram, "uColor"), 1f, 0.42f, 0.34f, 0.85f)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        val loc = GLES30.glGetAttribLocation(meshProgram, "aPt")
        GLES30.glEnableVertexAttribArray(loc)
        for (face in frame.faces) {
            meshVertices.clear()
            var n = 0
            var i = 0
            while (i + 1 < meshEdges.size && n < MAX_EDGES) {
                val a = meshEdges[i] * 3
                val b = meshEdges[i + 1] * 3
                if (b + 1 < face.size && a + 1 < face.size) {
                    meshVertices.put(face[a]).put(face[a + 1]).put(face[b]).put(face[b + 1])
                    n++
                }
                i += 2
            }
            meshVertices.position(0)
            GLES30.glVertexAttribPointer(loc, 2, GLES30.GL_FLOAT, false, 0, meshVertices)
            GLES30.glDrawArrays(GLES30.GL_LINES, 0, n * 2)
        }
        GLES30.glDisableVertexAttribArray(loc)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private companion object {
        const val TAG = "CameraRenderer"
        const val EGL_RECORDABLE_ANDROID = 0x3142
        const val MAX_EDGES = 3000

        // Obrazovka (y dolů) → narovnaný obraz → zrcadlení → buffer podle otočení → matice SurfaceTexture.
        const val CAMERA_VS = """#version 300 es
in vec2 aPos;
uniform mat4 uTexMatrix;
uniform vec2 uScale;
uniform float uMirror;
uniform int uRotation;
out vec2 vTex;
void main() {
    gl_Position = vec4(aPos, 0.0, 1.0);
    vec2 p = vec2(aPos.x * 0.5 + 0.5, 0.5 - aPos.y * 0.5);
    vec2 u = (p - 0.5) / uScale + 0.5;
    if (uMirror > 0.5) u.x = 1.0 - u.x;
    vec2 b;
    if (uRotation == 90) b = vec2(u.y, 1.0 - u.x);
    else if (uRotation == 180) b = vec2(1.0 - u.x, 1.0 - u.y);
    else if (uRotation == 270) b = vec2(1.0 - u.y, u.x);
    else b = u;
    vTex = (uTexMatrix * vec4(b.x, 1.0 - b.y, 0.0, 1.0)).xy;
}"""

        const val CAMERA_FS = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
uniform samplerExternalOES uTex;
in vec2 vTex;
out vec4 outColor;
void main() { outColor = texture(uTex, vTex); }"""

        const val MESH_VS = """#version 300 es
in vec2 aPt;
uniform vec2 uScale;
uniform float uMirror;
void main() {
    vec2 u = aPt;
    if (uMirror > 0.5) u.x = 1.0 - u.x;
    vec2 p = (u - 0.5) * uScale + 0.5;
    gl_Position = vec4(p.x * 2.0 - 1.0, 1.0 - p.y * 2.0, 0.0, 1.0);
}"""

        const val MESH_FS = """#version 300 es
precision mediump float;
uniform vec4 uColor;
out vec4 outColor;
void main() { outColor = uColor; }"""
    }
}
