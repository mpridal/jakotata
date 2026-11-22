package io.github.mpridal.jakotata.core.render

import android.graphics.Bitmap
import android.graphics.Matrix
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
import io.github.mpridal.jakotata.core.face.FaceTopology
import io.github.mpridal.jakotata.core.face.SkinStats
import io.github.mpridal.jakotata.core.profile.FaceProfile
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
    maskTopology: MaskTopology,
    private val kidSkinSource: () -> SkinStats?,
) {
    @Volatile var mirror: Boolean = true
    @Volatile var showMesh: Boolean = true
    /** Přepínač Obličej (zadání kap. 6.3). */
    @Volatile var maskEnabled: Boolean = true
    /** „Kolik táty“ 0–1. */
    @Volatile var maskAmount: Float = 0.8f
    /** Volba „Tátovy oči“: díry pro oči zavřené. */
    @Volatile var dadEyes: Boolean = false
    /** Adaptivní kvalita (zadání kap. 5): false = bez přenosu stínování. */
    @Volatile var shadingEnabled: Boolean = true

    private val mask = MaskPass(maskTopology)
    private val puppet = PuppetPass(maskTopology)

    /** Režim obrazu (zadání kap. 2.3–2.5). */
    @Volatile var imageMode: ImageMode = ImageMode.MASK
    private var lastMode = ImageMode.MASK

    /** Loutka sbírá neutrální výraz dcery („Udělej vážný obličej“). */
    val puppetCalibrating: Boolean get() = imageMode == ImageMode.PUPPET && puppet.calibrating

    // Nahrávání videa: druhý výstup do vstupní Surface enkodéru (zadání kap. 4).
    private var encoder: EGLSurface = EGL14.EGL_NO_SURFACE
    private var encW = 0
    private var encH = 0
    private var encBaseNs = 0L
    private var lastEncPts = -1L

    // Vodoznak (zadání kap. 8): jen v nahrávce a fotce, ne v náhledu.
    private var watermarkTex = 0
    private var watermarkAspect = 1f
    private var watermarkProgram = 0
    private val watermarkQuad = GlUtil.floatBuffer(16)

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
    /** Celkové otočení bufferu do narovnaného obrazu (z CameraX). */
    @Volatile private var rotation = 0
    /** Otočení, které ještě zbývá po matici SurfaceTexture (ta už může obsahovat otočení senzoru). */
    @Volatile private var shaderRotation = 0
    /** Matice SurfaceTexture přední kamery už obraz zrcadlí (camera transform) → shader nezrcadlí podruhé. */
    @Volatile private var textureMirrored = false

    /** Otočení senzoru vůči přirozené orientaci zařízení; nastaví CameraSource po připojení kamery. */
    @Volatile var sensorRotationDegrees: Int = 0
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
            releaseEncoder()
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
        mask.init()
        puppet.init()
        watermarkProgram = GlUtil.program(WATERMARK_VS, WATERMARK_FS)
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

        request.setTransformationInfoListener(executor) { info ->
            rotation = info.rotationDegrees
            // Kamera zapsala orientaci senzoru do Surface → matice SurfaceTexture už ji obsahuje.
            textureMirrored = info.hasCameraTransform() && info.isMirroring
            val extra = if (info.hasCameraTransform()) {
                ((info.rotationDegrees - sensorRotationDegrees) % 360 + 360) % 360
            } else info.rotationDegrees
            // Ve zrcadlené textuře se otáčí opačným směrem. Ověřeno jen na výšku (extra = 0); TODO tablet na šířku.
            shaderRotation = if (textureMirrored) (360 - extra) % 360 else extra
            Log.i(TAG, "Transform: rotation=${info.rotationDegrees} cameraTransform=${info.hasCameraTransform()} crop=${info.cropRect} mirroring=${info.isMirroring} buffer=${res.width}x${res.height}")
        }
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
        if (st != null && hasFrame) drawScene(viewW, viewH)
        if (showMesh && imageMode == ImageMode.MASK) drawMesh(mapping)
        EGL14.eglSwapBuffers(eglDisplay, window)

        if (st != null && hasFrame && encoder != EGL14.EGL_NO_SURFACE) drawToEncoder(st.timestamp)

        if (st != null && hasFrame) {
            // Časová značka kamery je podle zařízení v CLOCK_MONOTONIC, nebo v BOOTTIME (včetně spánku).
            val ts = st.timestamp
            val mono = System.nanoTime() - ts
            val boot = android.os.SystemClock.elapsedRealtimeNanos() - ts
            val age = if (mono in 0..1_000_000_000L) mono else boot
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
        GLES30.glUniform1f(GLES30.glGetUniformLocation(cameraProgram, "uMirror"), if (m.mirror != textureMirrored) 1f else 0f)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(cameraProgram, "uRotation"), shaderRotation)
        val loc = GLES30.glGetAttribLocation(cameraProgram, "aPos")
        GLES30.glEnableVertexAttribArray(loc)
        quad.position(0)
        GLES30.glVertexAttribPointer(loc, 2, GLES30.GL_FLOAT, false, 0, quad)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(loc)
    }

    /** Začne kreslit každý snímek i do enkodéru; časy snímků relativně k [baseNs] (CLOCK_MONOTONIC). */
    fun startRecording(surface: Surface, width: Int, height: Int, baseNs: Long) = handler.post {
        releaseEncoder()
        encoder = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, intArrayOf(EGL14.EGL_NONE), 0)
        encW = width; encH = height; encBaseNs = baseNs; lastEncPts = -1L
    }

    /** Synchronně – enkodér smí skončit až po návratu. */
    fun stopRecording() = runBlockingOnGl { releaseEncoder() }

    private fun releaseEncoder() {
        if (encoder != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(eglDisplay, pbuffer, pbuffer, eglContext)
            EGL14.eglDestroySurface(eglDisplay, encoder)
            encoder = EGL14.EGL_NO_SURFACE
        }
    }

    /** Vodoznak „JakoTáta“ do nahrávek a fotek; null = vypnutý (rodičovská zóna). */
    fun setWatermark(bitmap: Bitmap?) = handler.post {
        makeCurrent()
        if (watermarkTex != 0) { GLES30.glDeleteTextures(1, intArrayOf(watermarkTex), 0); watermarkTex = 0 }
        if (bitmap == null) return@post
        val t = IntArray(1)
        GLES30.glGenTextures(1, t, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        android.opengl.GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        watermarkTex = t[0]
        watermarkAspect = bitmap.width.toFloat() / bitmap.height
    }

    /**
     * Fotka (zadání kap. 6.4): obraz s maskou a vodoznakem v rozlišení [width]×[height], stejný ořez
     * a zrcadlení jako náhled. Callback na GL vlákně; null, když ještě není snímek.
     */
    fun capturePhoto(width: Int, height: Int, callback: (Bitmap?) -> Unit) = handler.post {
        if (!hasFrame || bufferW == 0) { callback(null); return@post }
        makeCurrent()
        callback(renderOffscreen(width, height) {
            drawScene(width, height)
            drawWatermark(width, height)
        })
    }

    private fun drawToEncoder(cameraTimestampNs: Long) {
        // Časová značka kamery je podle zařízení v CLOCK_MONOTONIC nebo BOOTTIME → převést na MONOTONIC.
        val now = System.nanoTime()
        val mono = if (kotlin.math.abs(now - cameraTimestampNs) < 1_000_000_000L) cameraTimestampNs
        else cameraTimestampNs - (android.os.SystemClock.elapsedRealtimeNanos() - now)
        val pts = mono - encBaseNs
        if (pts < 0 || pts <= lastEncPts) return
        lastEncPts = pts
        EGL14.eglMakeCurrent(eglDisplay, encoder, encoder, eglContext)
        GLES30.glViewport(0, 0, encW, encH)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        drawScene(encW, encH)
        drawWatermark(encW, encH)
        EGLExt.eglPresentationTimeANDROID(eglDisplay, encoder, pts)
        EGL14.eglSwapBuffers(eglDisplay, encoder)
        makeCurrent()
    }

    /** Malý vodoznak v pravém dolním rohu: šířka 24 % výstupu, okraj 3 %. */
    private fun drawWatermark(w: Int, h: Int) {
        if (watermarkTex == 0 || w == 0 || h == 0) return
        val qw = 0.24f * 2f
        val qh = qw * w / h / watermarkAspect
        val margin = 0.03f * 2f
        val x1 = 1f - margin; val x0 = x1 - qw
        val y0 = -1f + margin * w / h; val y1 = y0 + qh
        watermarkQuad.clear()
        watermarkQuad.put(floatArrayOf(x0, y0, 0f, 1f, x1, y0, 1f, 1f, x0, y1, 0f, 0f, x1, y1, 1f, 0f))
        watermarkQuad.position(0)
        GLES30.glUseProgram(watermarkProgram)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, watermarkTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(watermarkProgram, "uTex"), 0)
        val aPos = GLES30.glGetAttribLocation(watermarkProgram, "aPos")
        val aUv = GLES30.glGetAttribLocation(watermarkProgram, "aUv")
        GLES30.glEnableVertexAttribArray(aPos)
        GLES30.glEnableVertexAttribArray(aUv)
        watermarkQuad.position(0)
        GLES30.glVertexAttribPointer(aPos, 2, GLES30.GL_FLOAT, false, 16, watermarkQuad)
        watermarkQuad.position(2)
        GLES30.glVertexAttribPointer(aUv, 2, GLES30.GL_FLOAT, false, 16, watermarkQuad)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)  // bitmapa má předem vynásobenou alfu
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisableVertexAttribArray(aPos)
        GLES30.glDisableVertexAttribArray(aUv)
    }

    /** Vykreslí [block] do framebufferu w×h a vrátí výsledek jako narovnanou bitmapu. */
    private fun renderOffscreen(w: Int, h: Int, block: () -> Unit): Bitmap {
        val fbo = IntArray(1); val tex = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glGenFramebuffers(1, fbo, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, tex[0], 0)
        GLES30.glViewport(0, 0, w, h)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        block()
        val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glDeleteFramebuffers(1, fbo, 0)
        GLES30.glDeleteTextures(1, tex, 0)
        val raw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { buf.position(0); copyPixelsFromBuffer(buf) }
        // glReadPixels čte odspodu nahoru.
        return Bitmap.createBitmap(raw, 0, 0, w, h, Matrix().apply { postScale(1f, -1f) }, false)
    }

    /** Nastaví (nebo zruší) obličej táty pro masku. */
    fun setProfile(profile: FaceProfile?) = handler.post { makeCurrent(); mask.setProfile(profile) }

    /**
     * Narovnaný snímek kamery bez zrcadlení v rozlišení náhledu (pro fotku táty do profilu).
     * Callback běží na GL vlákně; null, když ještě není snímek.
     */
    fun captureFrame(callback: (Bitmap?) -> Unit) = handler.post {
        if (!hasFrame || bufferW == 0) { callback(null); return@post }
        makeCurrent()
        val swap = rotation % 180 != 0
        val w = if (swap) bufferH else bufferW
        val h = if (swap) bufferW else bufferH
        callback(renderOffscreen(w, h) { drawCamera(DisplayMapping(1f, 1f, mirror = false)) })
    }

    private val predictedFace = FloatArray(478 * 3)

    /** Čas právě kresleného snímku kamery (pro předpověď pohybu bodů). */
    private val currentFrameNs: Long get() = cameraTexture?.timestamp ?: 0L

    /** Celá scéna podle režimu obrazu do výstupu w×h (obrazovka, enkodér i fotka). */
    private fun drawScene(w: Int, h: Int) {
        val mode = imageMode
        if (mode != lastMode) {
            if (mode == ImageMode.PUPPET) puppet.resetNeutral()
            lastMode = mode
        }
        when (mode) {
            ImageMode.PUPPET -> {
                val frame = faceSource()
                if (!mask.hasProfile) { drawCamera(DisplayMapping.cover(bufferW, bufferH, rotation, w, h, mirror)); return }
                val face = if (frame != null && frame.faces.isNotEmpty()) frame.predicted(0, currentFrameNs, predictedFace) else null
                puppet.draw(mask, face, frame?.imageAspect ?: 0f, w, h)
            }
            ImageMode.COMPARE -> {
                // Originál vlevo, úprava vpravo; každá polovina má vlastní ořez, aby byl obličej uprostřed.
                val half = w / 2
                GLES30.glViewport(0, 0, half, h)
                drawCamera(DisplayMapping.cover(bufferW, bufferH, rotation, half, h, mirror))
                GLES30.glViewport(half, 0, w - half, h)
                val m = DisplayMapping.cover(bufferW, bufferH, rotation, w - half, h, mirror)
                drawCamera(m)
                if (maskEnabled) drawMask(m)
                GLES30.glViewport(0, 0, w, h)
                // Dělicí čára.
                GLES30.glEnable(GLES30.GL_SCISSOR_TEST)
                GLES30.glScissor(half - 2, 0, 4, h)
                GLES30.glClearColor(1f, 1f, 1f, 0.9f)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
            }
            else -> {
                val m = DisplayMapping.cover(bufferW, bufferH, rotation, w, h, mirror)
                drawCamera(m)
                if (maskEnabled) drawMask(m, half = mode == ImageMode.HALF)
            }
        }
    }

    private fun drawMask(m: DisplayMapping, half: Boolean = false) {
        if (!mask.hasProfile) return
        val frame = faceSource() ?: return
        val (fx, fy) = analysisCorrection(frame)
        mask.shading = shadingEnabled
        // Táta na každém nalezeném obličeji (nastavení „počet obličejů“ 1–2, zadání kap. 2.1).
        for (i in frame.faces.indices) {
            val face = frame.predicted(i, currentFrameNs, predictedFace)
            val tilt = frame.matrices.getOrNull(i)?.let { FaceTopology.headTiltDegrees(it) } ?: 0f
            mask.draw(
                face, fx, fy, tilt, m, cameraMirror = m.mirror != textureMirrored,
                cameraTex = cameraTexId, texMatrix = texMatrix, shaderRotation = shaderRotation,
                kidSkin = kidSkinSource(), amount = maskAmount, dadEyes = dadEyes, half = half,
            )
        }
    }

    private fun analysisCorrection(frame: FaceFrame): Pair<Float, Float> {
        val swap = rotation % 180 != 0
        val previewAspect = if (bufferH > 0) (if (swap) bufferH.toFloat() / bufferW else bufferW.toFloat() / bufferH) else 0f
        return DisplayMapping.analysisToPreview(frame.imageAspect, previewAspect)
    }

    private fun drawMesh(m: DisplayMapping) {
        val frame = faceSource() ?: return
        if (frame.faces.isEmpty()) return
        // Snímek detekce může mít jiný poměr stran než náhled (obojí je středový výřez senzoru).
        val (fx, fy) = analysisCorrection(frame)
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
                    meshVertices
                        .put((face[a] - 0.5f) * fx + 0.5f).put((face[a + 1] - 0.5f) * fy + 0.5f)
                        .put((face[b] - 0.5f) * fx + 0.5f).put((face[b + 1] - 0.5f) * fy + 0.5f)
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

        const val WATERMARK_VS = """#version 300 es
in vec2 aPos;
in vec2 aUv;
out vec2 vUv;
void main() { gl_Position = vec4(aPos, 0.0, 1.0); vUv = aUv; }"""

        const val WATERMARK_FS = """#version 300 es
precision mediump float;
uniform sampler2D uTex;
in vec2 vUv;
out vec4 outColor;
void main() { outColor = texture(uTex, vUv); }"""

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
