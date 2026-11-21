package io.github.mpridal.jakotata.core.render

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

internal object GlUtil {
    fun program(vertex: String, fragment: String): Int {
        val vs = shader(GLES30.GL_VERTEX_SHADER, vertex)
        val fs = shader(GLES30.GL_FRAGMENT_SHADER, fragment)
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, vs)
        GLES30.glAttachShader(p, fs)
        GLES30.glLinkProgram(p)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        check(ok[0] == GLES30.GL_TRUE) { "Program link failed: " + GLES30.glGetProgramInfoLog(p) }
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        return p
    }

    private fun shader(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        check(ok[0] == GLES30.GL_TRUE) { "Shader compile failed: " + GLES30.glGetShaderInfoLog(s) }
        return s
    }

    fun floatBuffer(capacity: Int): FloatBuffer =
        ByteBuffer.allocateDirect(capacity * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    fun floatBuffer(data: FloatArray): FloatBuffer = floatBuffer(data.size).apply { put(data); position(0) }
}
