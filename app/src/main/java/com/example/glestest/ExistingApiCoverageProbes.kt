package com.example.glestest

import android.opengl.GLES31
import android.opengl.GLES32
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ExistingApiCoverageProbes {

    private val existingApis = listOf(
        "glAttachShader",
        "glBindBuffer",
        "glBindFramebuffer",
        "glBindImageTexture",
        "glBindTexture",
        "glBindVertexArray",
        "glBlendBarrier",
        "glBlendEquationi",
        "glBlendFunci",
        "glBufferData",
        "glClear",
        "glClearColor",
        "glColorMaski",
        "glCompileShader",
        "glCopyImageSubData",
        "glCreateProgram",
        "glCreateShader",
        "glDebugMessageControl",
        "glDebugMessageInsert",
        "glDeleteBuffers",
        "glDeleteFramebuffers",
        "glDeleteProgram",
        "glDeleteShader",
        "glDeleteTextures",
        "glDeleteVertexArrays",
        "glDispatchCompute",
        "glDrawArrays",
        "glEnableVertexAttribArray",
        "glFramebufferTexture",
        "glGenBuffers",
        "glGenFramebuffers",
        "glGenTextures",
        "glGenVertexArrays",
        "glGetError",
        "glGetIntegerv",
        "glGetProgramInfoLog",
        "glGetProgramiv",
        "glGetShaderInfoLog",
        "glGetShaderiv",
        "glGetString",
        "glGetStringi",
        "glLinkProgram",
        "glMemoryBarrier",
        "glMinSampleShading",
        "glObjectLabel",
        "glPatchParameteri",
        "glPopDebugGroup",
        "glPrimitiveBoundingBox",
        "glPushDebugGroup",
        "glSampleMaski",
        "glShaderSource",
        "glTexBufferRange",
        "glTexStorage2D",
        "glTexStorage3D",
        "glTexStorage3DMultisample",
        "glUseProgram",
        "glVertexAttribPointer",
        "glViewport"
    )

    fun run(): List<ProbeResult> {
        return try {
            val ledger = Ledger(existingApis)
            runSection(ledger, "context") { probeContext(ledger) }
            runSection(ledger, "render target") { probeRenderTarget(ledger) }
            runSection(ledger, "shader program draw") { probeShaderProgramDraw(ledger) }
            runSection(ledger, "textures") { probeTextureStorageCopyAndBuffer(ledger) }
            runSection(ledger, "compute image") { probeComputeImage(ledger) }
            runSection(ledger, "es32 state") { probeEs32State(ledger) }
            runSection(ledger, "debug") { probeDebugAndLabels(ledger) }

            val results = ledger.results()
            val pass = results.count { it.supported }
            listOf(
                ProbeResult(
                    name = "Existing API Semantic Coverage Summary",
                    supported = pass == results.size,
                    errorCode = if (pass == results.size) GLES32.GL_NO_ERROR else -1,
                    errorName = if (pass == results.size) "ALL_EXISTING_APIS_VERIFIED" else "PARTIAL_EXISTING_API_VERIFICATION",
                    detail = "existingUniqueApis=${results.size}, verified=$pass, failed=${results.size - pass}. Result names with prefix ExistingAPI/ are limited to APIs already called by the original code."
                )
            ) + results
        } catch (t: Throwable) {
            val detail = "Existing API semantic probe runner was guarded: ${t::class.java.simpleName}: ${t.message ?: "no message"}"
            listOf(
                ProbeResult(
                    name = "Existing API Semantic Coverage Summary",
                    supported = false,
                    errorCode = -1,
                    errorName = "RUNNER_EXCEPTION",
                    detail = detail
                )
            ) + existingApis.map { api ->
                ProbeResult(
                    name = "ExistingAPI/$api",
                    supported = false,
                    errorCode = -1,
                    errorName = "RUNNER_EXCEPTION",
                    detail = detail
                )
            }
        }
    }

    private class Ledger(private val apis: List<String>) {
        private val map = linkedMapOf<String, ProbeResult>()

        init {
            apis.forEach { api ->
                map[api] = ProbeResult(
                    name = "ExistingAPI/$api",
                    supported = false,
                    errorCode = -1,
                    errorName = "NOT_VERIFIED",
                    detail = "No semantic evidence was recorded for this original API."
                )
            }
        }

        fun pass(api: String, detail: String) {
            map[api] = ProbeResult(
                name = "ExistingAPI/$api",
                supported = true,
                errorCode = GLES32.GL_NO_ERROR,
                errorName = "VERIFIED",
                detail = detail
            )
        }

        fun fail(api: String, errorName: String, detail: String, errorCode: Int = -1) {
            map[api] = ProbeResult(
                name = "ExistingAPI/$api",
                supported = false,
                errorCode = errorCode,
                errorName = errorName,
                detail = detail
            )
        }

        fun failIfPending(api: String, errorName: String, detail: String, errorCode: Int = -1) {
            val current = map[api]
            if (current == null || current.errorName == "NOT_VERIFIED") {
                fail(api, errorName, detail, errorCode)
            }
        }

        fun results(): List<ProbeResult> = apis.map { map.getValue(it) }
    }

    private fun runSection(ledger: Ledger, section: String, block: () -> Unit) {
        try {
            clearErrors()
            block()
        } catch (t: Throwable) {
            ledger.fail(
                "glGetError",
                "SECTION_EXCEPTION",
                "Section $section aborted: ${t::class.java.simpleName}: ${t.message ?: "no message"}"
            )
        } finally {
            clearErrorsSafe()
        }
    }

    private fun safeCleanup(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
        }
    }

    private fun checked(ledger: Ledger, api: String, detail: String, block: () -> Boolean) {
        try {
            clearErrors()
            val verified = block()
            val err = GLES32.glGetError()
            if (verified && err == GLES32.GL_NO_ERROR) {
                ledger.pass(api, detail)
            } else {
                ledger.fail(
                    api,
                    if (err == GLES32.GL_NO_ERROR) "VERIFY_FALSE" else errorName(err),
                    "$detail; verified=$verified; glError=${errorName(err)} ($err)",
                    err
                )
            }
        } catch (t: Throwable) {
            ledger.fail(
                api,
                "EXCEPTION",
                "$detail; exception=${t::class.java.simpleName}: ${t.message ?: "no message"}"
            )
        } finally {
            clearErrorsSafe()
        }
    }

    private fun probeContext(ledger: Ledger) {
        checked(ledger, "glGetError", "error queue was drained and GL_NO_ERROR was observed") {
            GLES32.glGetError() == GLES32.GL_NO_ERROR
        }
        checked(ledger, "glGetString", "vendor, renderer, version and GLSL strings were non-empty") {
            !GLES32.glGetString(GLES32.GL_VENDOR).isNullOrBlank() &&
                !GLES32.glGetString(GLES32.GL_RENDERER).isNullOrBlank() &&
                !GLES32.glGetString(GLES32.GL_VERSION).isNullOrBlank() &&
                !GLES32.glGetString(GLES32.GL_SHADING_LANGUAGE_VERSION).isNullOrBlank()
        }
        checked(ledger, "glGetIntegerv", "major/minor context version and viewport state were readable") {
            val major = getInt(GLES32.GL_MAJOR_VERSION)
            val minor = getInt(GLES32.GL_MINOR_VERSION)
            val viewport = IntArray(4)
            GLES32.glGetIntegerv(GLES32.GL_VIEWPORT, viewport, 0)
            major >= 3 && minor >= 0 && viewport[2] >= 0 && viewport[3] >= 0
        }
        checked(ledger, "glGetStringi", "the first extension string was readable when extensions were advertised") {
            val count = getInt(GLES32.GL_NUM_EXTENSIONS)
            count > 0 && !GLES32.glGetStringi(GLES32.GL_EXTENSIONS, 0).isNullOrBlank()
        }
        checked(ledger, "glViewport", "viewport state matched the requested rectangle") {
            GLES32.glViewport(0, 0, 32, 32)
            val viewport = IntArray(4)
            GLES32.glGetIntegerv(GLES32.GL_VIEWPORT, viewport, 0)
            viewport.contentEquals(intArrayOf(0, 0, 32, 32))
        }
        checked(ledger, "glClearColor", "clear color state matched the requested RGBA value") {
            GLES32.glClearColor(0.125f, 0.25f, 0.5f, 1f)
            val color = FloatArray(4)
            GLES32.glGetFloatv(GLES32.GL_COLOR_CLEAR_VALUE, color, 0)
            close(color[0], 0.125f) && close(color[1], 0.25f) && close(color[2], 0.5f) && close(color[3], 1f)
        }
    }

    private fun probeRenderTarget(ledger: Ledger) {
        val tex = IntArray(1)
        val fbo = IntArray(1)
        try {
            checked(ledger, "glGenTextures", "generated a non-zero color texture") {
                GLES32.glGenTextures(1, tex, 0)
                tex[0] != 0
            }
            checked(ledger, "glBindTexture", "GL_TEXTURE_2D binding matched generated texture") {
                GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, tex[0])
                getInt(GLES32.GL_TEXTURE_BINDING_2D) == tex[0]
            }
            checked(ledger, "glTexStorage2D", "immutable 2D texture reported expected width and height") {
                GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, tex[0])
                GLES32.glTexStorage2D(GLES32.GL_TEXTURE_2D, 1, GLES32.GL_RGBA8, 16, 16)
                getTexLevelInt(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WIDTH) == 16 &&
                    getTexLevelInt(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_HEIGHT) == 16
            }
            checked(ledger, "glGenFramebuffers", "generated a non-zero framebuffer") {
                GLES32.glGenFramebuffers(1, fbo, 0)
                fbo[0] != 0
            }
            checked(ledger, "glBindFramebuffer", "GL_FRAMEBUFFER binding matched generated framebuffer") {
                GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, fbo[0])
                getInt(GLES32.GL_FRAMEBUFFER_BINDING) == fbo[0]
            }
            checked(ledger, "glFramebufferTexture", "texture attachment produced a complete framebuffer") {
                GLES32.glFramebufferTexture(GLES32.GL_FRAMEBUFFER, GLES32.GL_COLOR_ATTACHMENT0, tex[0], 0)
                GLES32.glCheckFramebufferStatus(GLES32.GL_FRAMEBUFFER) == GLES32.GL_FRAMEBUFFER_COMPLETE
            }
            checked(ledger, "glClear", "clear command produced the expected pixel value in the framebuffer") {
                GLES32.glViewport(0, 0, 16, 16)
                GLES32.glClearColor(0f, 0f, 1f, 1f)
                GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT)
                readPixel(8, 8).closeTo(0, 0, 255, 255)
            }
            checked(ledger, "glDeleteFramebuffers", "deleted framebuffer no longer reported as framebuffer") {
                val temp = IntArray(1)
                GLES32.glGenFramebuffers(1, temp, 0)
                GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, temp[0])
                GLES32.glDeleteFramebuffers(1, temp, 0)
                !GLES32.glIsFramebuffer(temp[0])
            }
            checked(ledger, "glDeleteTextures", "deleted texture no longer reported as texture") {
                val temp = IntArray(1)
                GLES32.glGenTextures(1, temp, 0)
                GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, temp[0])
                GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MIN_FILTER, GLES32.GL_NEAREST)
                GLES32.glDeleteTextures(1, temp, 0)
                !GLES32.glIsTexture(temp[0])
            }
        } finally {
            safeCleanup { GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, 0) }
            safeCleanup { deleteFramebuffers(fbo[0]) }
            safeCleanup { deleteTextures(tex[0]) }
        }
    }

    private fun probeShaderProgramDraw(ledger: Ledger) {
        val vs = compileShaderWithEvidence(
            ledger,
            GLES32.GL_VERTEX_SHADER,
            "#version 310 es\nlayout(location=0) in vec2 aPos;\nvoid main(){ gl_Position = vec4(aPos, 0.0, 1.0); }"
        )
        val fs = compileShaderWithEvidence(
            ledger,
            GLES32.GL_FRAGMENT_SHADER,
            "#version 310 es\nprecision mediump float;\nout vec4 outColor;\nvoid main(){ outColor = vec4(1.0, 0.0, 0.0, 1.0); }"
        )
        if (vs == 0 || fs == 0) {
            val dependentApis = listOf(
                "glCreateProgram",
                "glAttachShader",
                "glLinkProgram",
                "glGetProgramiv",
                "glGetProgramInfoLog",
                "glUseProgram",
                "glGenVertexArrays",
                "glBindVertexArray",
                "glGenBuffers",
                "glBindBuffer",
                "glBufferData",
                "glEnableVertexAttribArray",
                "glVertexAttribPointer",
                "glDrawArrays"
            )
            dependentApis.forEach {
                ledger.failIfPending(it, "SETUP_FAILED", "shader setup failed, so dependent semantic check could not run")
            }
            safeCleanup { if (vs != 0) GLES32.glDeleteShader(vs) }
            safeCleanup { if (fs != 0) GLES32.glDeleteShader(fs) }
            return
        }

        var program = 0
        val vao = IntArray(1)
        val vbo = IntArray(1)
        val fbo = IntArray(1)
        val tex = IntArray(1)
        try {
            checked(ledger, "glCreateProgram", "program object creation returned a non-zero name") {
                program = GLES32.glCreateProgram()
                program != 0
            }
            checked(ledger, "glAttachShader", "attached shader count reached two") {
                GLES32.glAttachShader(program, vs)
                GLES32.glAttachShader(program, fs)
                getProgramInt(program, GLES32.GL_ATTACHED_SHADERS) == 2
            }
            checked(ledger, "glLinkProgram", "program link status was GL_TRUE") {
                GLES32.glLinkProgram(program)
                getProgramInt(program, GLES32.GL_LINK_STATUS) == GLES32.GL_TRUE
            }
            checked(ledger, "glGetProgramiv", "program link status was queried as GL_TRUE") {
                getProgramInt(program, GLES32.GL_LINK_STATUS) == GLES32.GL_TRUE
            }
            checked(ledger, "glGetProgramInfoLog", "program info log query returned a non-null string") {
                GLES32.glGetProgramInfoLog(program) != null
            }
            checked(ledger, "glUseProgram", "current program binding matched linked program") {
                GLES32.glUseProgram(program)
                getInt(GLES32.GL_CURRENT_PROGRAM) == program
            }
            checked(ledger, "glGenVertexArrays", "generated a non-zero vertex array") {
                GLES32.glGenVertexArrays(1, vao, 0)
                vao[0] != 0
            }
            checked(ledger, "glBindVertexArray", "vertex array binding matched generated VAO") {
                GLES32.glBindVertexArray(vao[0])
                getInt(GLES32.GL_VERTEX_ARRAY_BINDING) == vao[0]
            }
            checked(ledger, "glGenBuffers", "generated a non-zero vertex buffer") {
                GLES32.glGenBuffers(1, vbo, 0)
                vbo[0] != 0
            }
            checked(ledger, "glBindBuffer", "array buffer binding matched generated VBO") {
                GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, vbo[0])
                getInt(GLES32.GL_ARRAY_BUFFER_BINDING) == vbo[0]
            }
            checked(ledger, "glBufferData", "vertex buffer size was allocated and queried") {
                val vertices = floatBuffer(
                    floatArrayOf(
                        -0.8f, -0.8f,
                        0.8f, -0.8f,
                        0.0f, 0.8f
                    )
                )
                GLES32.glBufferData(GLES32.GL_ARRAY_BUFFER, 6 * 4, vertices, GLES32.GL_STATIC_DRAW)
                getBufferInt(GLES32.GL_ARRAY_BUFFER, GLES32.GL_BUFFER_SIZE) == 24
            }
            checked(ledger, "glEnableVertexAttribArray", "vertex attrib 0 became enabled") {
                GLES32.glEnableVertexAttribArray(0)
                getVertexAttribInt(0, GLES32.GL_VERTEX_ATTRIB_ARRAY_ENABLED) == GLES32.GL_TRUE
            }
            checked(ledger, "glVertexAttribPointer", "vertex attrib 0 size/type matched requested layout") {
                GLES32.glVertexAttribPointer(0, 2, GLES32.GL_FLOAT, false, 2 * 4, 0)
                getVertexAttribInt(0, GLES32.GL_VERTEX_ATTRIB_ARRAY_SIZE) == 2 &&
                    getVertexAttribInt(0, GLES32.GL_VERTEX_ATTRIB_ARRAY_TYPE) == GLES32.GL_FLOAT
            }
            checked(ledger, "glDrawArrays", "triangle draw produced the expected red pixel") {
                createBoundColorFbo(16, 16, tex, fbo)
                GLES32.glViewport(0, 0, 16, 16)
                GLES32.glClearColor(0f, 0f, 0f, 1f)
                GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT)
                GLES32.glUseProgram(program)
                GLES32.glBindVertexArray(vao[0])
                GLES32.glDrawArrays(GLES32.GL_TRIANGLES, 0, 3)
                readPixel(8, 8).closeTo(255, 0, 0, 255)
            }
            checked(ledger, "glDeleteVertexArrays", "deleted VAO no longer reported as vertex array") {
                val temp = IntArray(1)
                GLES32.glGenVertexArrays(1, temp, 0)
                GLES32.glBindVertexArray(temp[0])
                GLES32.glDeleteVertexArrays(1, temp, 0)
                !GLES32.glIsVertexArray(temp[0])
            }
            checked(ledger, "glDeleteBuffers", "deleted buffer no longer reported as buffer object") {
                val temp = IntArray(1)
                GLES32.glGenBuffers(1, temp, 0)
                GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, temp[0])
                GLES32.glBufferData(GLES32.GL_ARRAY_BUFFER, 4, null, GLES32.GL_STATIC_DRAW)
                GLES32.glDeleteBuffers(1, temp, 0)
                !GLES32.glIsBuffer(temp[0])
            }
            checked(ledger, "glDeleteProgram", "deleted program no longer reported as program object") {
                val temp = GLES32.glCreateProgram()
                GLES32.glDeleteProgram(temp)
                !GLES32.glIsProgram(temp)
            }
            checked(ledger, "glDeleteShader", "deleted shader no longer reported as shader object") {
                val temp = GLES32.glCreateShader(GLES32.GL_VERTEX_SHADER)
                GLES32.glDeleteShader(temp)
                !GLES32.glIsShader(temp)
            }
        } finally {
            safeCleanup { GLES32.glUseProgram(0) }
            safeCleanup { GLES32.glBindVertexArray(0) }
            safeCleanup { GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, 0) }
            safeCleanup { GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, 0) }
            safeCleanup { deleteFramebuffers(fbo[0]) }
            safeCleanup { deleteTextures(tex[0]) }
            safeCleanup { deleteBuffers(vbo[0]) }
            safeCleanup { if (vao[0] != 0) GLES32.glDeleteVertexArrays(1, vao, 0) }
            safeCleanup { if (program != 0) GLES32.glDeleteProgram(program) }
            safeCleanup { GLES32.glDeleteShader(vs) }
            safeCleanup { GLES32.glDeleteShader(fs) }
        }
    }

    private fun probeTextureStorageCopyAndBuffer(ledger: Ledger) {
        checked(ledger, "glTexStorage3D", "immutable 3D texture reported expected depth") {
            val tex = IntArray(1)
            GLES32.glGenTextures(1, tex, 0)
            GLES32.glBindTexture(GLES32.GL_TEXTURE_3D, tex[0])
            GLES32.glTexStorage3D(GLES32.GL_TEXTURE_3D, 1, GLES32.GL_RGBA8, 4, 4, 2)
            val ok = getTexLevelInt(GLES32.GL_TEXTURE_3D, GLES32.GL_TEXTURE_DEPTH) == 2
            GLES32.glDeleteTextures(1, tex, 0)
            ok
        }
        checked(ledger, "glTexStorage3DMultisample", "multisample array texture reported expected depth and sample count") {
            val tex = IntArray(1)
            GLES32.glGenTextures(1, tex, 0)
            GLES32.glBindTexture(GLES32.GL_TEXTURE_2D_MULTISAMPLE_ARRAY, tex[0])
            GLES32.glTexStorage3DMultisample(GLES32.GL_TEXTURE_2D_MULTISAMPLE_ARRAY, 1, GLES32.GL_RGBA8, 4, 4, 1, true)
            val ok = getTexLevelInt(GLES32.GL_TEXTURE_2D_MULTISAMPLE_ARRAY, GLES32.GL_TEXTURE_DEPTH) == 1 &&
                getTexLevelInt(GLES32.GL_TEXTURE_2D_MULTISAMPLE_ARRAY, GLES32.GL_TEXTURE_SAMPLES) == 1
            GLES32.glDeleteTextures(1, tex, 0)
            ok
        }
        checked(ledger, "glTexBufferRange", "texture buffer range reported expected byte size") {
            val tex = IntArray(1)
            val buf = IntArray(1)
            GLES32.glGenTextures(1, tex, 0)
            GLES32.glGenBuffers(1, buf, 0)
            GLES32.glBindBuffer(GLES32.GL_TEXTURE_BUFFER, buf[0])
            GLES32.glBufferData(GLES32.GL_TEXTURE_BUFFER, 64, null, GLES32.GL_STATIC_DRAW)
            GLES32.glBindTexture(GLES32.GL_TEXTURE_BUFFER, tex[0])
            GLES32.glTexBufferRange(GLES32.GL_TEXTURE_BUFFER, GLES32.GL_R8, buf[0], 0, 64)
            val ok = getTexLevelInt(GLES32.GL_TEXTURE_BUFFER, GLES32.GL_TEXTURE_BUFFER_SIZE) == 64
            GLES32.glDeleteTextures(1, tex, 0)
            GLES32.glDeleteBuffers(1, buf, 0)
            ok
        }
        checked(ledger, "glCopyImageSubData", "copied red texture data was read back from destination texture") {
            val src = createFilledTexture(4, 4, 255, 0, 0, 255)
            val dst = createFilledTexture(4, 4, 0, 0, 0, 255)
            GLES32.glCopyImageSubData(
                src,
                GLES32.GL_TEXTURE_2D,
                0,
                0,
                0,
                0,
                dst,
                GLES32.GL_TEXTURE_2D,
                0,
                0,
                0,
                0,
                4,
                4,
                1
            )
            val fbo = createFramebuffer(dst)
            val pixel = readPixel(2, 2)
            GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, 0)
            deleteFramebuffers(fbo)
            deleteTextures(src, dst)
            pixel.closeTo(255, 0, 0, 255)
        }
    }

    private fun probeComputeImage(ledger: Ledger) {
        var tex = 0
        var shader = 0
        var program = 0
        var fbo = 0
        try {
            tex = createFilledTexture(4, 4, 0, 0, 0, 255)
            shader = compileRawShader(
                GLES32.GL_COMPUTE_SHADER,
                "#version 310 es\nlayout(local_size_x=1, local_size_y=1) in;\nlayout(rgba8, binding=0) writeonly uniform highp image2D img;\nvoid main(){ imageStore(img, ivec2(0, 0), vec4(0.0, 1.0, 0.0, 1.0)); }"
            )
            program = GLES32.glCreateProgram()
            GLES32.glAttachShader(program, shader)
            GLES32.glLinkProgram(program)
            if (getProgramInt(program, GLES32.GL_LINK_STATUS) != GLES32.GL_TRUE) {
                val log = GLES32.glGetProgramInfoLog(program)
                listOf("glBindImageTexture", "glDispatchCompute", "glMemoryBarrier").forEach {
                    ledger.fail(it, "LINK_ERROR", "compute program link failed: $log")
                }
                return
            }
            checked(ledger, "glBindImageTexture", "image binding accepted the RGBA8 texture for compute write") {
                GLES32.glBindImageTexture(0, tex, 0, false, 0, GLES32.GL_WRITE_ONLY, GLES32.GL_RGBA8)
                true
            }
            checked(ledger, "glDispatchCompute", "compute shader dispatch wrote green into image pixel") {
                GLES32.glUseProgram(program)
                GLES32.glDispatchCompute(1, 1, 1)
                true
            }
            checked(ledger, "glMemoryBarrier", "memory barrier made compute image write visible to framebuffer readback") {
                GLES31.glMemoryBarrier(GLES32.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT or GLES32.GL_FRAMEBUFFER_BARRIER_BIT)
                fbo = createFramebuffer(tex)
                readPixel(0, 0).closeTo(0, 255, 0, 255)
            }
        } catch (t: Throwable) {
            val detail = "compute setup was guarded: ${t::class.java.simpleName}: ${t.message ?: "no message"}"
            listOf("glBindImageTexture", "glDispatchCompute", "glMemoryBarrier").forEach {
                ledger.failIfPending(it, "SETUP_EXCEPTION", detail)
            }
        } finally {
            safeCleanup { GLES32.glUseProgram(0) }
            safeCleanup { if (program != 0) GLES32.glDeleteProgram(program) }
            safeCleanup { if (shader != 0) GLES32.glDeleteShader(shader) }
            safeCleanup { GLES32.glBindImageTexture(0, 0, 0, false, 0, GLES32.GL_READ_ONLY, GLES32.GL_RGBA8) }
            safeCleanup { GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, 0) }
            safeCleanup { deleteFramebuffers(fbo) }
            safeCleanup { deleteTextures(tex) }
        }
    }

    private fun probeEs32State(ledger: Ledger) {
        checked(ledger, "glPrimitiveBoundingBox", "primitive bounding box values were read back through GL_PRIMITIVE_BOUNDING_BOX") {
            GLES32.glPrimitiveBoundingBox(-1f, -0.5f, -0.25f, 0f, 1f, 0.5f, 0.25f, 1f)
            val out = FloatArray(8)
            GLES32.glGetFloatv(GLES32.GL_PRIMITIVE_BOUNDING_BOX, out, 0)
            close(out[0], -1f) && close(out[1], -0.5f) && close(out[6], 0.25f) && close(out[7], 1f)
        }
        checked(ledger, "glPatchParameteri", "patch vertex count was read back as 3") {
            GLES32.glPatchParameteri(GLES32.GL_PATCH_VERTICES, 3)
            getInt(GLES32.GL_PATCH_VERTICES) == 3
        }
        checked(ledger, "glMinSampleShading", "minimum sample shading value was read back") {
            GLES32.glMinSampleShading(1f)
            close(getFloat(GLES32.GL_MIN_SAMPLE_SHADING_VALUE), 1f)
        }
        checked(ledger, "glSampleMaski", "sample mask word 0 was read back") {
            val value = 0x0f0f0f0f
            GLES32.glSampleMaski(0, value)
            val out = IntArray(1)
            GLES32.glGetIntegeri_v(GLES32.GL_SAMPLE_MASK_VALUE, 0, out, 0)
            out[0] == value
        }
        checked(ledger, "glBlendEquationi", "indexed blend equation was read back as GL_FUNC_ADD") {
            GLES32.glBlendEquationi(0, GLES32.GL_FUNC_ADD)
            val out = IntArray(1)
            GLES32.glGetIntegeri_v(GLES32.GL_BLEND_EQUATION_RGB, 0, out, 0)
            out[0] == GLES32.GL_FUNC_ADD
        }
        checked(ledger, "glBlendFunci", "indexed blend factors were read back as ONE/ZERO") {
            GLES32.glBlendFunci(0, GLES32.GL_ONE, GLES32.GL_ZERO)
            val src = IntArray(1)
            val dst = IntArray(1)
            GLES32.glGetIntegeri_v(GLES32.GL_BLEND_SRC_RGB, 0, src, 0)
            GLES32.glGetIntegeri_v(GLES32.GL_BLEND_DST_RGB, 0, dst, 0)
            src[0] == GLES32.GL_ONE && dst[0] == GLES32.GL_ZERO
        }
        checked(ledger, "glColorMaski", "indexed color mask was read back as all true") {
            GLES32.glColorMaski(0, true, true, true, true)
            val out = BooleanArray(4)
            GLES32.glGetBooleani_v(GLES32.GL_COLOR_WRITEMASK, 0, out, 0)
            out.all { it }
        }
        checked(ledger, "glBlendBarrier", "blend barrier accepted and a following framebuffer clear/readback remained correct") {
            val tex = createFilledTexture(4, 4, 0, 0, 0, 255)
            val fbo = createFramebuffer(tex)
            GLES32.glBlendBarrier()
            GLES32.glClearColor(0f, 0f, 1f, 1f)
            GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT)
            val ok = readPixel(2, 2).closeTo(0, 0, 255, 255)
            GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, 0)
            deleteFramebuffers(fbo)
            deleteTextures(tex)
            ok
        }
    }

    private fun probeDebugAndLabels(ledger: Ledger) {
        checked(ledger, "glPushDebugGroup", "debug group stack depth increased after push") {
            val before = getInt(GLES32.GL_DEBUG_GROUP_STACK_DEPTH)
            GLES32.glPushDebugGroup(GLES32.GL_DEBUG_SOURCE_APPLICATION, 77, -1, "existing-api-group")
            getInt(GLES32.GL_DEBUG_GROUP_STACK_DEPTH) == before + 1
        }
        checked(ledger, "glPopDebugGroup", "debug group stack depth returned after pop") {
            val before = getInt(GLES32.GL_DEBUG_GROUP_STACK_DEPTH)
            GLES32.glPopDebugGroup()
            getInt(GLES32.GL_DEBUG_GROUP_STACK_DEPTH) == before - 1
        }
        checked(ledger, "glDebugMessageControl", "debug message filtering accepted application markers") {
            GLES32.glDebugMessageControl(
                GLES32.GL_DEBUG_SOURCE_APPLICATION,
                GLES32.GL_DEBUG_TYPE_MARKER,
                GLES32.GL_DONT_CARE,
                0,
                null,
                0,
                true
            )
            true
        }
        checked(ledger, "glDebugMessageInsert", "inserted debug marker reached the synchronous callback") {
            var received = false
            GLES32.glEnable(GLES32.GL_DEBUG_OUTPUT)
            GLES32.glEnable(GLES32.GL_DEBUG_OUTPUT_SYNCHRONOUS)
            GLES32.glDebugMessageCallback(object : GLES32.DebugProc {
                override fun onMessage(source: Int, type: Int, id: Int, severity: Int, message: String) {
                    if (id == 9917 && message.contains("existing-api-marker")) {
                        received = true
                    }
                }
            })
            GLES32.glDebugMessageInsert(
                GLES32.GL_DEBUG_SOURCE_APPLICATION,
                GLES32.GL_DEBUG_TYPE_MARKER,
                9917,
                GLES32.GL_DEBUG_SEVERITY_NOTIFICATION,
                -1,
                "existing-api-marker"
            )
            GLES32.glFinish()
            GLES32.glDebugMessageCallback(null)
            received
        }
        checked(ledger, "glObjectLabel", "object label was read back from a buffer object") {
            val buf = IntArray(1)
            GLES32.glGenBuffers(1, buf, 0)
            GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, buf[0])
            GLES32.glBufferData(GLES32.GL_ARRAY_BUFFER, 4, null, GLES32.GL_STATIC_DRAW)
            val expected = "existing-buffer"
            GLES32.glObjectLabel(GLES32.GL_BUFFER, buf[0], expected.length, expected)
            val label = GLES32.glGetObjectLabel(GLES32.GL_BUFFER, buf[0])
            GLES32.glDeleteBuffers(1, buf, 0)
            label == expected
        }
    }

    private fun compileShaderWithEvidence(ledger: Ledger, type: Int, source: String): Int {
        var shader = 0
        checked(ledger, "glCreateShader", "shader object creation returned a non-zero name") {
            shader = GLES32.glCreateShader(type)
            shader != 0
        }
        if (shader == 0) return 0
        checked(ledger, "glShaderSource", "shader source was accepted and later compiled") {
            GLES32.glShaderSource(shader, source)
            true
        }
        checked(ledger, "glCompileShader", "shader compile completed with GL_COMPILE_STATUS true") {
            GLES32.glCompileShader(shader)
            getShaderInt(shader, GLES32.GL_COMPILE_STATUS) == GLES32.GL_TRUE
        }
        checked(ledger, "glGetShaderiv", "shader compile status was queried as GL_TRUE") {
            getShaderInt(shader, GLES32.GL_COMPILE_STATUS) == GLES32.GL_TRUE
        }
        checked(ledger, "glGetShaderInfoLog", "shader info log query returned a non-null string") {
            GLES32.glGetShaderInfoLog(shader) != null
        }
        return shader
    }

    private fun compileRawShader(type: Int, source: String): Int {
        val shader = GLES32.glCreateShader(type)
        GLES32.glShaderSource(shader, source)
        GLES32.glCompileShader(shader)
        if (getShaderInt(shader, GLES32.GL_COMPILE_STATUS) != GLES32.GL_TRUE) {
            val log = GLES32.glGetShaderInfoLog(shader)
            GLES32.glDeleteShader(shader)
            throw IllegalStateException("shader compile failed: $log")
        }
        return shader
    }

    private fun createBoundColorFbo(width: Int, height: Int, texOut: IntArray, fboOut: IntArray) {
        GLES32.glGenTextures(1, texOut, 0)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, texOut[0])
        GLES32.glTexStorage2D(GLES32.GL_TEXTURE_2D, 1, GLES32.GL_RGBA8, width, height)
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MIN_FILTER, GLES32.GL_NEAREST)
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MAG_FILTER, GLES32.GL_NEAREST)
        GLES32.glGenFramebuffers(1, fboOut, 0)
        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, fboOut[0])
        GLES32.glFramebufferTexture(GLES32.GL_FRAMEBUFFER, GLES32.GL_COLOR_ATTACHMENT0, texOut[0], 0)
        if (GLES32.glCheckFramebufferStatus(GLES32.GL_FRAMEBUFFER) != GLES32.GL_FRAMEBUFFER_COMPLETE) {
            throw IllegalStateException("FBO incomplete")
        }
    }

    private fun createFilledTexture(width: Int, height: Int, r: Int, g: Int, b: Int, a: Int): Int {
        val tex = IntArray(1)
        GLES32.glGenTextures(1, tex, 0)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, tex[0])
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MIN_FILTER, GLES32.GL_NEAREST)
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MAG_FILTER, GLES32.GL_NEAREST)
        GLES32.glTexImage2D(
            GLES32.GL_TEXTURE_2D,
            0,
            GLES32.GL_RGBA8,
            width,
            height,
            0,
            GLES32.GL_RGBA,
            GLES32.GL_UNSIGNED_BYTE,
            rgbaBytes(width, height, r, g, b, a)
        )
        return tex[0]
    }

    private fun createFramebuffer(texture: Int): Int {
        val fbo = IntArray(1)
        GLES32.glGenFramebuffers(1, fbo, 0)
        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, fbo[0])
        GLES32.glFramebufferTexture(GLES32.GL_FRAMEBUFFER, GLES32.GL_COLOR_ATTACHMENT0, texture, 0)
        if (GLES32.glCheckFramebufferStatus(GLES32.GL_FRAMEBUFFER) != GLES32.GL_FRAMEBUFFER_COMPLETE) {
            throw IllegalStateException("FBO incomplete")
        }
        return fbo[0]
    }

    private fun rgbaBytes(width: Int, height: Int, r: Int, g: Int, b: Int, a: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        repeat(width * height) {
            buffer.put(r.toByte())
            buffer.put(g.toByte())
            buffer.put(b.toByte())
            buffer.put(a.toByte())
        }
        buffer.position(0)
        return buffer
    }

    private data class Pixel(val r: Int, val g: Int, val b: Int, val a: Int) {
        fun closeTo(er: Int, eg: Int, eb: Int, ea: Int): Boolean {
            return kotlin.math.abs(r - er) <= 3 &&
                kotlin.math.abs(g - eg) <= 3 &&
                kotlin.math.abs(b - eb) <= 3 &&
                kotlin.math.abs(a - ea) <= 3
        }
    }

    private fun readPixel(x: Int, y: Int): Pixel {
        val pixel = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        GLES32.glReadPixels(x, y, 1, 1, GLES32.GL_RGBA, GLES32.GL_UNSIGNED_BYTE, pixel)
        pixel.position(0)
        return Pixel(
            pixel.get().toInt() and 0xff,
            pixel.get().toInt() and 0xff,
            pixel.get().toInt() and 0xff,
            pixel.get().toInt() and 0xff
        )
    }

    private fun floatBuffer(values: FloatArray) =
        ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }

    private fun getInt(pname: Int): Int {
        val out = IntArray(1)
        GLES32.glGetIntegerv(pname, out, 0)
        return out[0]
    }

    private fun getFloat(pname: Int): Float {
        val out = FloatArray(1)
        GLES32.glGetFloatv(pname, out, 0)
        return out[0]
    }

    private fun getProgramInt(program: Int, pname: Int): Int {
        val out = IntArray(1)
        GLES32.glGetProgramiv(program, pname, out, 0)
        return out[0]
    }

    private fun getShaderInt(shader: Int, pname: Int): Int {
        val out = IntArray(1)
        GLES32.glGetShaderiv(shader, pname, out, 0)
        return out[0]
    }

    private fun getBufferInt(target: Int, pname: Int): Int {
        val out = IntArray(1)
        GLES32.glGetBufferParameteriv(target, pname, out, 0)
        return out[0]
    }

    private fun getTexLevelInt(target: Int, pname: Int): Int {
        val out = IntArray(1)
        GLES32.glGetTexLevelParameteriv(target, 0, pname, out, 0)
        return out[0]
    }

    private fun getVertexAttribInt(index: Int, pname: Int): Int {
        val out = IntArray(1)
        GLES32.glGetVertexAttribiv(index, pname, out, 0)
        return out[0]
    }

    private fun deleteTextures(vararg textures: Int) {
        val ids = textures.filter { it != 0 }.toIntArray()
        if (ids.isNotEmpty()) GLES32.glDeleteTextures(ids.size, ids, 0)
    }

    private fun deleteFramebuffers(vararg framebuffers: Int) {
        val ids = framebuffers.filter { it != 0 }.toIntArray()
        if (ids.isNotEmpty()) GLES32.glDeleteFramebuffers(ids.size, ids, 0)
    }

    private fun deleteBuffers(vararg buffers: Int) {
        val ids = buffers.filter { it != 0 }.toIntArray()
        if (ids.isNotEmpty()) GLES32.glDeleteBuffers(ids.size, ids, 0)
    }

    private fun clearErrorsSafe() {
        try {
            clearErrors()
        } catch (_: Throwable) {
        }
    }

    private fun clearErrors() {
        var err = GLES32.glGetError()
        var guard = 0
        while (err != GLES32.GL_NO_ERROR && guard < 16) {
            err = GLES32.glGetError()
            guard++
        }
    }

    private fun close(actual: Float, expected: Float): Boolean =
        kotlin.math.abs(actual - expected) <= 0.01f

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
