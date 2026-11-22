package io.github.mpridal.jakotata.core.render

import android.opengl.GLES30
import io.github.mpridal.jakotata.core.face.FaceTopology
import io.github.mpridal.jakotata.core.face.PuppetMath
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

/**
 * Režim B – Loutka (zadání kap. 2.4): tátova fotka přes celou obrazovku, obličej deformovaný podle
 * mimiky dcery. Na začátku se zapamatuje neutrální výraz dcery (1 s). Vnitřek pusy = tmavá dutina.
 * Volat na GL vlákně.
 */
internal class PuppetPass(private val topo: MaskTopology) {
    private var texProgram = 0
    private var solidProgram = 0
    private val n = FaceTopology.MESH_VERTICES
    private val kidNorm = FloatArray(n * 2)
    private val neutralSum = FloatArray(n * 2)
    private val neutral = FloatArray(n * 2)
    private var neutralFrames = 0
    private val deformed = FloatArray(n * 2)
    private val positions = GlUtil.floatBuffer(n * 2)
    private val quad = GlUtil.floatBuffer(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
    private val quadAlpha = GlUtil.floatBuffer(floatArrayOf(1f, 1f, 1f, 1f))
    private val fan = GlUtil.floatBuffer((FaceTopology.INNER_LIPS.size + 2) * 2)
    private val indices: ShortBuffer = ByteBuffer.allocateDirect(topo.triangles.size * 2).order(ByteOrder.nativeOrder())
        .asShortBuffer().apply { topo.triangles.forEach { put(it.toShort()) }; position(0) }

    /** true, dokud se sbírá neutrální výraz („Udělej vážný obličej“). */
    val calibrating get() = neutralFrames < NEUTRAL_FRAMES

    fun init() {
        texProgram = GlUtil.program(TEX_VS, TEX_FS)
        solidProgram = GlUtil.program(TEX_VS, SOLID_FS)
    }

    fun resetNeutral() {
        neutralFrames = 0
        neutralSum.fill(0f)
    }

    /**
     * @param kid body dcery [x, y, z…] v narovnaném snímku detekce, [kidAspect] jeho šířka/výška; null = bez obličeje
     */
    fun draw(mask: MaskPass, kid: FloatArray?, kidAspect: Float, viewW: Int, viewH: Int) {
        val dad = mask.dadCoords ?: return
        val uv = mask.uvBuffer ?: return
        val alpha = mask.alphaMouthOnly ?: return
        // Čtvercový výřez fotky vyplní obrazovku (center crop).
        val a = viewW.toFloat() / viewH
        val sx = if (a < 1f) 1f / a else 1f
        val sy = if (a < 1f) 1f else a

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mask.dadTexture)
        drawTex(quad, quad, quadAlpha, sx, sy, GLES30.GL_TRIANGLE_STRIP, 4, blend = false)

        val def: FloatArray = if (kid != null && kidAspect > 0f) {
            PuppetMath.normalize(kid, kidAspect, topo.oval, n, kidNorm)
            if (calibrating) {
                for (i in kidNorm.indices) neutralSum[i] += kidNorm[i]
                if (++neutralFrames == NEUTRAL_FRAMES) for (i in neutral.indices) neutral[i] = neutralSum[i] / NEUTRAL_FRAMES
                return  // během kalibrace jen nehybná fotka
            }
            PuppetMath.deform(dad, topo.oval, kidNorm, neutral, n, out = deformed)
        } else return

        // Dutina úst pod sítí (síť má v obrysu vnitřních rtů díru).
        val lips = FaceTopology.INNER_LIPS
        var cx = 0f; var cy = 0f
        for (i in lips) { cx += def[i * 2]; cy += def[i * 2 + 1] }
        cx /= lips.size; cy /= lips.size
        fan.clear(); fan.put(cx).put(cy)
        for (i in lips) fan.put(def[i * 2]).put(def[i * 2 + 1])
        fan.put(def[lips[0] * 2]).put(def[lips[0] * 2 + 1])
        fan.position(0)
        GLES30.glUseProgram(solidProgram)
        GLES30.glUniform4f(GLES30.glGetUniformLocation(solidProgram, "uColor"), 0.20f, 0.06f, 0.06f, 1f)
        drawGeometry(solidProgram, fan, null, null, sx, sy, GLES30.GL_TRIANGLE_FAN, lips.size + 2, blend = false)

        positions.clear(); positions.put(def, 0, n * 2); positions.position(0)
        uv.position(0)
        drawTex(positions, uv, alpha, sx, sy, GLES30.GL_TRIANGLES, topo.triangles.size, blend = true, indexed = true)
    }

    private fun drawTex(
        pos: java.nio.FloatBuffer, uv: java.nio.FloatBuffer, alpha: java.nio.FloatBuffer,
        sx: Float, sy: Float, mode: Int, count: Int, blend: Boolean, indexed: Boolean = false,
    ) {
        GLES30.glUseProgram(texProgram)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(texProgram, "uTex"), 0)
        drawGeometry(texProgram, pos, uv, alpha, sx, sy, mode, count, blend, indexed)
    }

    private fun drawGeometry(
        program: Int, pos: java.nio.FloatBuffer, uv: java.nio.FloatBuffer?, alpha: java.nio.FloatBuffer?,
        sx: Float, sy: Float, mode: Int, count: Int, blend: Boolean, indexed: Boolean = false,
    ) {
        GLES30.glUniform2f(GLES30.glGetUniformLocation(program, "uScale"), sx, sy)
        val aPt = GLES30.glGetAttribLocation(program, "aPt")
        val aUv = GLES30.glGetAttribLocation(program, "aUv")
        val aAlpha = GLES30.glGetAttribLocation(program, "aAlpha")
        pos.position(0)
        GLES30.glEnableVertexAttribArray(aPt)
        GLES30.glVertexAttribPointer(aPt, 2, GLES30.GL_FLOAT, false, 0, pos)
        if (aUv >= 0) {
            if (uv != null) { uv.position(0); GLES30.glEnableVertexAttribArray(aUv); GLES30.glVertexAttribPointer(aUv, 2, GLES30.GL_FLOAT, false, 0, uv) }
            else { GLES30.glDisableVertexAttribArray(aUv); GLES30.glVertexAttrib2f(aUv, 0f, 0f) }
        }
        if (aAlpha >= 0) {
            if (alpha != null) { alpha.position(0); GLES30.glEnableVertexAttribArray(aAlpha); GLES30.glVertexAttribPointer(aAlpha, 1, GLES30.GL_FLOAT, false, 0, alpha) }
            else { GLES30.glDisableVertexAttribArray(aAlpha); GLES30.glVertexAttrib1f(aAlpha, 1f) }
        }
        if (blend) { GLES30.glEnable(GLES30.GL_BLEND); GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA) }
        if (indexed) { indices.position(0); GLES30.glDrawElements(mode, count, GLES30.GL_UNSIGNED_SHORT, indices) }
        else GLES30.glDrawArrays(mode, 0, count)
        if (blend) GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisableVertexAttribArray(aPt)
        if (aUv >= 0) GLES30.glDisableVertexAttribArray(aUv)
        if (aAlpha >= 0) GLES30.glDisableVertexAttribArray(aAlpha)
    }

    private companion object {
        const val NEUTRAL_FRAMES = 30  // ~1 s

        // Body v textuře táty (0–1, y dolů) → obrazovka s center crop.
        const val TEX_VS = """#version 300 es
in vec2 aPt;
in vec2 aUv;
in float aAlpha;
uniform vec2 uScale;
out vec2 vUv;
out float vAlpha;
void main() {
    vec2 p = (aPt - 0.5) * uScale + 0.5;
    gl_Position = vec4(p.x * 2.0 - 1.0, 1.0 - p.y * 2.0, 0.0, 1.0);
    vUv = aUv;
    vAlpha = aAlpha;
}"""

        const val TEX_FS = """#version 300 es
precision mediump float;
uniform sampler2D uTex;
in vec2 vUv;
in float vAlpha;
out vec4 outColor;
void main() { outColor = vec4(texture(uTex, vUv).rgb, vAlpha); }"""

        const val SOLID_FS = """#version 300 es
precision mediump float;
uniform vec4 uColor;
in vec2 vUv;
in float vAlpha;
out vec4 outColor;
void main() { outColor = uColor; }"""
    }
}
