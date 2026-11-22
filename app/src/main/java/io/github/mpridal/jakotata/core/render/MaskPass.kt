package io.github.mpridal.jakotata.core.render

import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLUtils
import io.github.mpridal.jakotata.core.face.FaceTopology
import io.github.mpridal.jakotata.core.face.SkinStats
import io.github.mpridal.jakotata.core.profile.FaceProfile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.max

/** Indexy sítě pro masku – z [io.github.mpridal.jakotata.core.face.FaceTracker]. */
class MaskTopology(
    val triangles: IntArray,
    val oval: IntArray,
    val leftEye: IntArray,
    val rightEye: IntArray,
)

/**
 * Režim A – Maska (zadání kap. 2.3): tátova textura napnutá na síť obličeje dcery.
 * - průhlednost ve vrcholech: měkký okraj oválu, díry pro oči a vnitřek rtů (volba „Tátovy oči“ díry očí zavře),
 * - barevné sladění v Lab (průměr a rozptyl kůže táty → dcery),
 * - přenos stínování: nízkofrekvenční jas obličeje dcery vůči jeho průměru násobí tátovu texturu,
 * - „Kolik táty“ a zeslábnutí při natočení hlavy nad ~35°.
 * Všechny metody volat na GL vlákně.
 */
internal class MaskPass(private val topo: MaskTopology) {
    private var program = 0
    private var dadTex = 0
    private var dadSkin: SkinStats? = null
    private var uv: FloatBuffer? = null
    private var alphaHoles: FloatBuffer? = null
    private var alphaDadEyes: FloatBuffer? = null
    /** Pro Loutku: jen díra pro ústa (oči jsou tátovy z fotky). */
    var alphaMouthOnly: FloatBuffer? = null
        private set
    /** Body táty ve výřezu fotky [x, y, z…] – sdílí Loutka. */
    var dadCoords: FloatArray? = null
        private set
    /** x středu obličeje táty (hřbet nosu) v textuře – hranice pro Půl na půl. */
    private var midX = 0.5f
    private val positions = GlUtil.floatBuffer(FaceTopology.MESH_VERTICES * 2)
    private val indices: ShortBuffer = ByteBuffer.allocateDirect(topo.triangles.size * 2).order(ByteOrder.nativeOrder())
        .asShortBuffer().apply { topo.triangles.forEach { put(it.toShort()) }; position(0) }

    val hasProfile get() = dadTex != 0
    /** Přenos stínování (adaptivní kvalita ho může vypnout). */
    var shading = true
    val dadTexture get() = dadTex
    val uvBuffer get() = uv

    fun init() {
        program = GlUtil.program(VS, FS)
    }

    fun setProfile(profile: FaceProfile?) {
        if (dadTex != 0) { GLES30.glDeleteTextures(1, intArrayOf(dadTex), 0); dadTex = 0 }
        uv = null; alphaHoles = null; alphaDadEyes = null; alphaMouthOnly = null; dadSkin = null; dadCoords = null
        if (profile == null) return
        val t = IntArray(1)
        GLES30.glGenTextures(1, t, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, profile.texture, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        dadTex = t[0]
        val n = FaceTopology.MESH_VERTICES
        uv = GlUtil.floatBuffer(FloatArray(n * 2) { i -> profile.coords[(i / 2) * 3 + (i % 2)] })
        val lips = FaceTopology.INNER_LIPS
        alphaHoles = GlUtil.floatBuffer(FaceTopology.vertexAlpha(profile.coords, topo.oval, listOf(topo.leftEye, topo.rightEye, lips)))
        alphaDadEyes = GlUtil.floatBuffer(FaceTopology.vertexAlpha(profile.coords, topo.oval, listOf(lips)))
        alphaMouthOnly = alphaDadEyes
        dadCoords = profile.coords
        midX = profile.coords[FaceTopology.NOSE_BRIDGE * 3]
        dadSkin = profile.skin
    }

    /**
     * @param face body obličeje dcery v narovnaném obraze detekce [x, y, z, …]
     * @param fx, fy přepočet ze snímku detekce do snímku náhledu
     */
    fun draw(
        face: FloatArray, fx: Float, fy: Float, headTiltDeg: Float,
        mapping: DisplayMapping, cameraMirror: Boolean,
        cameraTex: Int, texMatrix: FloatArray, shaderRotation: Int,
        kidSkin: SkinStats?, amount: Float, dadEyes: Boolean,
        /** Půl na půl: táta jen na polovině obličeje, která je na obrazovce vlevo. */
        half: Boolean = false,
    ) {
        val uvBuf = uv ?: return
        val alphaBuf = (if (dadEyes) alphaDadEyes else alphaHoles) ?: return
        val dad = dadSkin ?: return
        val kid = kidSkin ?: dad
        // Plné při natočení do 25°, zmizí ve 40° (zadání: nad ~35° maska plynule zeslábne).
        val fade = 1f - ((headTiltDeg - 25f) / 15f).coerceIn(0f, 1f)
        val strength = amount * fade
        if (strength <= 0.001f) return

        positions.clear()
        for (i in 0 until FaceTopology.MESH_VERTICES) {
            positions.put((face[i * 3] - 0.5f) * fx + 0.5f)
            positions.put((face[i * 3 + 1] - 0.5f) * fy + 0.5f)
        }
        positions.position(0)

        GLES30.glUseProgram(program)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dadTex)
        GLES30.glUniform1i(loc("uDad"), 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTex)
        GLES30.glUniform1i(loc("uCam"), 1)
        GLES30.glUniformMatrix4fv(loc("uTexMatrix"), 1, false, texMatrix, 0)
        GLES30.glUniform1i(loc("uRotation"), shaderRotation)
        GLES30.glUniform1f(loc("uCamMirror"), if (cameraMirror) 1f else 0f)
        GLES30.glUniform2f(loc("uScale"), mapping.scaleX, mapping.scaleY)
        GLES30.glUniform1f(loc("uMirror"), if (mapping.mirror) 1f else 0f)
        GLES30.glUniform3f(loc("uDadMean"), dad.meanL, dad.meanA, dad.meanB)
        GLES30.glUniform3f(loc("uDadStd"), dad.stdL, dad.stdA, dad.stdB)
        GLES30.glUniform3f(loc("uKidMean"), kid.meanL, kid.meanA, kid.meanB)
        GLES30.glUniform3f(loc("uKidStd"), kid.stdL, kid.stdA, kid.stdB)
        GLES30.glUniform1f(loc("uKidLuma"), max(kid.luma, 0.05f))
        GLES30.glUniform1f(loc("uShade"), if (shading) SHADE_STRENGTH else 0f)
        GLES30.glUniform1f(loc("uAmount"), strength)
        // Levá polovina na obrazovce: u zrcadleného náhledu je to pravá strana obrázku (větší x).
        GLES30.glUniform1f(loc("uHalf"), if (!half) 0f else if (mapping.mirror) 2f else 1f)
        GLES30.glUniform1f(loc("uMidX"), midX)

        val aPt = GLES30.glGetAttribLocation(program, "aPt")
        val aUv = GLES30.glGetAttribLocation(program, "aUv")
        val aAlpha = GLES30.glGetAttribLocation(program, "aAlpha")
        GLES30.glEnableVertexAttribArray(aPt)
        GLES30.glVertexAttribPointer(aPt, 2, GLES30.GL_FLOAT, false, 0, positions)
        GLES30.glEnableVertexAttribArray(aUv)
        uvBuf.position(0)
        GLES30.glVertexAttribPointer(aUv, 2, GLES30.GL_FLOAT, false, 0, uvBuf)
        GLES30.glEnableVertexAttribArray(aAlpha)
        alphaBuf.position(0)
        GLES30.glVertexAttribPointer(aAlpha, 1, GLES30.GL_FLOAT, false, 0, alphaBuf)

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        indices.position(0)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, topo.triangles.size, GLES30.GL_UNSIGNED_SHORT, indices)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisableVertexAttribArray(aPt)
        GLES30.glDisableVertexAttribArray(aUv)
        GLES30.glDisableVertexAttribArray(aAlpha)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }

    private fun loc(name: String) = GLES30.glGetUniformLocation(program, name)

    private companion object {
        const val SHADE_STRENGTH = 0.55f

        const val VS = """#version 300 es
in vec2 aPt;
in vec2 aUv;
in float aAlpha;
uniform vec2 uScale;
uniform float uMirror;
uniform mat4 uTexMatrix;
uniform int uRotation;
uniform float uCamMirror;
out vec2 vUv;
out float vAlpha;
out vec2 vCam;
void main() {
    vec2 u = aPt;
    if (uMirror > 0.5) u.x = 1.0 - u.x;
    vec2 p = (u - 0.5) * uScale + 0.5;
    gl_Position = vec4(p.x * 2.0 - 1.0, 1.0 - p.y * 2.0, 0.0, 1.0);
    vUv = aUv;
    vAlpha = aAlpha;
    // Stejné mapování jako shader kamery: kde na textuře kamery leží tento bod obrazovky.
    vec2 c = (p - 0.5) / uScale + 0.5;
    if (uCamMirror > 0.5) c.x = 1.0 - c.x;
    vec2 b;
    if (uRotation == 90) b = vec2(c.y, 1.0 - c.x);
    else if (uRotation == 180) b = vec2(1.0 - c.x, 1.0 - c.y);
    else if (uRotation == 270) b = vec2(1.0 - c.y, c.x);
    else b = c;
    vCam = (uTexMatrix * vec4(b.x, 1.0 - b.y, 0.0, 1.0)).xy;
}"""

        const val FS = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;
uniform sampler2D uDad;
uniform samplerExternalOES uCam;
uniform vec3 uDadMean;
uniform vec3 uDadStd;
uniform vec3 uKidMean;
uniform vec3 uKidStd;
uniform float uKidLuma;
uniform float uShade;
uniform float uAmount;
uniform float uHalf;
uniform float uMidX;
in vec2 vUv;
in float vAlpha;
in vec2 vCam;
out vec4 outColor;

vec3 toLinear(vec3 c) { return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(0.04045, c)); }
vec3 toSrgb(vec3 c) { return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, c)); }
float f(float t) { return t > 0.008856 ? pow(t, 1.0 / 3.0) : 7.787 * t + 16.0 / 116.0; }
float fi(float t) { return t > 0.206893 ? t * t * t : (t - 16.0 / 116.0) / 7.787; }

vec3 rgb2lab(vec3 c) {
    vec3 l = toLinear(c);
    float x = dot(vec3(0.4124, 0.3576, 0.1805), l) / 0.95047;
    float y = dot(vec3(0.2126, 0.7152, 0.0722), l);
    float z = dot(vec3(0.0193, 0.1192, 0.9505), l) / 1.08883;
    float fx = f(x), fy = f(y), fz = f(z);
    return vec3(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz));
}

vec3 lab2rgb(vec3 lab) {
    float fy = (lab.x + 16.0) / 116.0;
    float fx = fy + lab.y / 500.0;
    float fz = fy - lab.z / 200.0;
    vec3 xyz = vec3(fi(fx) * 0.95047, fi(fy), fi(fz) * 1.08883);
    vec3 l = vec3(
        dot(vec3(3.2406, -1.5372, -0.4986), xyz),
        dot(vec3(-0.9689, 1.8758, 0.0415), xyz),
        dot(vec3(0.0557, -0.2040, 1.0570), xyz));
    return toSrgb(clamp(l, 0.0, 1.0));
}

float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }

void main() {
    vec3 dad = texture(uDad, vUv).rgb;
    // Barevné sladění v Lab, poměr rozptylů omezený proti přepalům.
    vec3 lab = rgb2lab(dad);
    vec3 ratio = clamp(uKidStd / uDadStd, vec3(0.5), vec3(2.0));
    lab = (lab - uDadMean) * ratio + uKidMean;
    vec3 c = lab2rgb(lab);
    // Přenos stínování: rozmazaný jas obličeje dcery (9 vzorků) vůči jejímu průměru.
    float r = 0.018;
    float s = 0.0;
    for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) {
        s += luma(texture(uCam, vCam + vec2(float(i), float(j)) * r).rgb);
    }
    float shade = clamp((s / 9.0) / uKidLuma, 0.75, 1.25);
    c *= mix(1.0, shade, uShade);
    float side = 1.0;
    if (uHalf > 1.5) side = smoothstep(uMidX - 0.012, uMidX + 0.012, vUv.x);
    else if (uHalf > 0.5) side = smoothstep(uMidX + 0.012, uMidX - 0.012, vUv.x);
    outColor = vec4(clamp(c, 0.0, 1.0), vAlpha * uAmount * side);
}"""
    }
}
