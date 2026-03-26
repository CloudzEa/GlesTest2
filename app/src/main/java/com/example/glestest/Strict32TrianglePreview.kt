package com.example.glestest

import android.opengl.GLES32
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Strict32TrianglePreview {

    private var program = 0
    private val vao = IntArray(1)
    private val vbo = IntArray(1)

    fun init(): ProbeResult {
        val vertexSource = "#version 320 es\nlayout(location = 0) in vec2 aPos;\nvoid main(){ gl_Position = vec4(aPos, 0.0, 1.0); }"
        val fragmentSource = "#version 320 es\nprecision mediump float;\nout vec4 outColor;\nvoid main(){ outColor = vec4(0.95, 0.20, 0.20, 1.0); }"

        val vs = compileShader(GLES32.GL_VERTEX_SHADER, vertexSource)
        if (vs.first == 0) {
            return ProbeResult(
                name = "Strict3.2 Triangle Preview Init",
                supported = false,
                errorCode = -1,
                errorName = "VS_COMPILE_ERROR",
                detail = "vertex shader compile failed: ${vs.second}"
            )
        }

        val fs = compileShader(GLES32.GL_FRAGMENT_SHADER, fragmentSource)
        if (fs.first == 0) {
            GLES32.glDeleteShader(vs.first)
            return ProbeResult(
                name = "Strict3.2 Triangle Preview Init",
                supported = false,
                errorCode = -1,
                errorName = "FS_COMPILE_ERROR",
                detail = "fragment shader compile failed: ${fs.second}"
            )
        }

        program = GLES32.glCreateProgram()
        if (program == 0) {
            GLES32.glDeleteShader(vs.first)
            GLES32.glDeleteShader(fs.first)
            return ProbeResult(
                name = "Strict3.2 Triangle Preview Init",
                supported = false,
                errorCode = -1,
                errorName = "PROGRAM_CREATE_FAILED",
                detail = "glCreateProgram returned 0"
            )
        }

        GLES32.glAttachShader(program, vs.first)
        GLES32.glAttachShader(program, fs.first)
        GLES32.glLinkProgram(program)

        val link = IntArray(1)
        GLES32.glGetProgramiv(program, GLES32.GL_LINK_STATUS, link, 0)
        GLES32.glDeleteShader(vs.first)
        GLES32.glDeleteShader(fs.first)

        if (link[0] != GLES32.GL_TRUE) {
            val log = GLES32.glGetProgramInfoLog(program)
            GLES32.glDeleteProgram(program)
            program = 0
            return ProbeResult(
                name = "Strict3.2 Triangle Preview Init",
                supported = false,
                errorCode = -1,
                errorName = "LINK_ERROR",
                detail = "program link failed: $log"
            )
        }

        val vertices = floatArrayOf(
            -0.6f, -0.6f,
            0.6f, -0.6f,
            0.0f, 0.6f
        )
        val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }

        GLES32.glGenVertexArrays(1, vao, 0)
        GLES32.glBindVertexArray(vao[0])

        GLES32.glGenBuffers(1, vbo, 0)
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, vbo[0])
        GLES32.glBufferData(
            GLES32.GL_ARRAY_BUFFER,
            vertices.size * 4,
            vertexBuffer,
            GLES32.GL_STATIC_DRAW
        )

        GLES32.glEnableVertexAttribArray(0)
        GLES32.glVertexAttribPointer(0, 2, GLES32.GL_FLOAT, false, 2 * 4, 0)

        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, 0)
        GLES32.glBindVertexArray(0)

        val initErr = GLES32.glGetError()
        return ProbeResult(
            name = "Strict3.2 Triangle Preview Init",
            supported = initErr == GLES32.GL_NO_ERROR,
            errorCode = initErr,
            errorName = errorName(initErr),
            detail = "strict #version 320 es triangle pipeline initialized"
        )
    }

    fun draw(): Int {
        if (program == 0 || vao[0] == 0) {
            return GLES32.GL_INVALID_OPERATION
        }

        GLES32.glUseProgram(program)
        GLES32.glBindVertexArray(vao[0])
        GLES32.glDrawArrays(GLES32.GL_TRIANGLES, 0, 3)
        GLES32.glBindVertexArray(0)
        GLES32.glUseProgram(0)
        return GLES32.glGetError()
    }

    private fun compileShader(type: Int, source: String): Pair<Int, String> {
        val shader = GLES32.glCreateShader(type)
        if (shader == 0) {
            return 0 to "glCreateShader returned 0"
        }

        GLES32.glShaderSource(shader, source)
        GLES32.glCompileShader(shader)

        val status = IntArray(1)
        GLES32.glGetShaderiv(shader, GLES32.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES32.GL_TRUE) {
            val log = GLES32.glGetShaderInfoLog(shader)
            GLES32.glDeleteShader(shader)
            return 0 to log
        }

        return shader to ""
    }

    private fun errorName(code: Int): String {
        return when (code) {
            GLES32.GL_NO_ERROR -> "GL_NO_ERROR"
            GLES32.GL_INVALID_ENUM -> "GL_INVALID_ENUM"
            GLES32.GL_INVALID_VALUE -> "GL_INVALID_VALUE"
            GLES32.GL_INVALID_OPERATION -> "GL_INVALID_OPERATION"
            GLES32.GL_INVALID_FRAMEBUFFER_OPERATION -> "GL_INVALID_FRAMEBUFFER_OPERATION"
            GLES32.GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY"
            else -> "UNKNOWN"
        }
    }
}
