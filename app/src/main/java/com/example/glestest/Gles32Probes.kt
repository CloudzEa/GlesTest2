package com.example.glestest

import android.opengl.GLES31
import android.opengl.GLES32
import java.nio.ByteBuffer
import java.nio.ByteOrder

object Gles32Probes {

    private const val REQUIRED_MAJOR = 3
    private const val REQUIRED_MINOR = 2

    private data class ExtensionEvidence(
        val totalExtensions: Int,
        val geometryMatches: List<String>,
        val tessellationMatches: List<String>
    )

    private data class ShaderCompileEvidence(
        val shaderId: Int,
        val compiled: Boolean,
        val log: String
    )

    fun runAll(): ProbeReport {
        return try {
            val deviceInfo = readDeviceInfo()
            val extensionEvidence = readExtensionEvidence()
            val results = mutableListOf<ProbeResult>()
            if (isAtLeastEs32(deviceInfo)) {
                results += ProbeResult(
                    name = "ES3.2 Environment Check",
                    supported = true,
                    errorCode = 0,
                    errorName = "GL_NO_ERROR",
                    detail = "Context ${deviceInfo.major}.${deviceInfo.minor} satisfies strict ES 3.2 requirement. Running full probe set."
                )
            } else {
                results += ProbeResult(
                    name = "ES3.2 Environment Check",
                    supported = false,
                    errorCode = -1,
                    errorName = "REQUIRES_ES_3_2",
                    detail = "Current context is ${deviceInfo.major}.${deviceInfo.minor}. Strict mode requires >= 3.2. Still running full probe set with crash-safe guards."
                )
            }

            results += extensionEvidenceResults(extensionEvidence)

            results += listOf(
                safeProbe("GL_GEOMETRY_SHADER (#version 320 es)") { probeGeometryShader320Compile() },
                safeProbe("GL_TESS_CONTROL_SHADER (#version 320 es)") { probeTessControlShader320Compile() },
                safeProbe("GL_TESS_EVALUATION_SHADER (#version 320 es)") { probeTessEvaluationShader320Compile() },
                safeProbe("Tessellation Pipeline Draw (compile+link+draw)") { probeTessellationPipelineDraw() },
                safeProbe("glPrimitiveBoundingBox") { probePrimitiveBoundingBox() },
                safeProbe("glPatchParameteri") { probePatchParameteri() },
                safeProbe("glMinSampleShading") { probeMinSampleShading() },
                safeProbe("glTexStorage3DMultisample") { probeTexStorage3DMultisample() },
                safeProbe("glTexBufferRange") { probeTexBufferRange() },
                safeProbe("glSampleMaski") { probeSampleMaski() },
                safeProbe("glTexStorage3D") { probeTexStorage3D() },
                safeProbe("glCopyImageSubData") { probeCopyImageSubData() },
                safeProbe("glDispatchCompute") { probeDispatchCompute() },
                safeProbe("glMemoryBarrier") { probeMemoryBarrier() },
                safeProbe("glBindImageTexture") { probeBindImageTexture() },
                safeProbe("glFramebufferTexture") { probeFramebufferTexture() },
                safeProbe("glDebugMessageControl") { probeDebugMessageControl() },
                safeProbe("glDebugMessageInsert") { probeDebugMessageInsert() },
                safeProbe("glPushDebugGroup+glPopDebugGroup") { probeDebugGroupPushPop() },
                safeProbe("glObjectLabel") { probeObjectLabel() },
                safeProbe("glBlendEquationi") { probeBlendEquationi() },
                safeProbe("glBlendFunci") { probeBlendFunci() },
                safeProbe("glColorMaski") { probeColorMaski() },
                safeProbe("glBlendBarrier") { probeBlendBarrier() }
            )

            results += buildEvidenceVerdict(deviceInfo, extensionEvidence, results)
            ProbeReport(deviceInfo = deviceInfo, results = results)
        } catch (t: Throwable) {
            ProbeReport(
                deviceInfo = DeviceInfo(),
                results = listOf(
                    ProbeResult(
                        name = "ProbeRunnerFatal",
                        supported = false,
                        errorCode = -1,
                        errorName = "FATAL_EXCEPTION",
                        detail = "Probe runner aborted safely: ${t::class.java.simpleName}: ${t.message ?: "no message"}"
                    )
                )
            )
        }
    }

    private fun safeProbe(name: String, block: () -> ProbeResult): ProbeResult {
        return try {
            clearErrorsSafe()
            block()
        } catch (t: Throwable) {
            ProbeResult(
                name = name,
                supported = false,
                errorCode = -1,
                errorName = "EXCEPTION",
                detail = "Exception guarded: ${t::class.java.simpleName}: ${t.message ?: "no message"}"
            )
        } finally {
            clearErrorsSafe()
        }
    }

    private fun clearErrorsSafe() {
        try {
            clearErrors()
        } catch (_: Throwable) {
            // Keep probe runner alive even if error draining itself fails on this backend.
        }
    }

    private fun readExtensionEvidence(): ExtensionEvidence {
        val extensions = enumerateExtensions()
        val geometryMatches = extensions.filter {
            it.contains("geometry_shader", ignoreCase = true) ||
                it == "GL_ANDROID_extension_pack_es31a"
        }.sorted()
        val tessellationMatches = extensions.filter {
            it.contains("tessellation_shader", ignoreCase = true) ||
                it == "GL_ANDROID_extension_pack_es31a"
        }.sorted()
        return ExtensionEvidence(
            totalExtensions = extensions.size,
            geometryMatches = geometryMatches,
            tessellationMatches = tessellationMatches
        )
    }

    private fun enumerateExtensions(): Set<String> {
        val extensions = linkedSetOf<String>()
        try {
            val count = IntArray(1)
            GLES32.glGetIntegerv(GLES32.GL_NUM_EXTENSIONS, count, 0)
            for (i in 0 until count[0]) {
                val ext = GLES32.glGetStringi(GLES32.GL_EXTENSIONS, i)
                if (!ext.isNullOrBlank()) {
                    extensions += ext
                }
            }
        } catch (_: Throwable) {
            // Fallback to space-separated extension string below.
        }

        if (extensions.isEmpty()) {
            val flat = GLES32.glGetString(GLES32.GL_EXTENSIONS)
            if (!flat.isNullOrBlank()) {
                flat.split(' ')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { extensions += it }
            }
        }

        return extensions
    }

    private fun extensionEvidenceResults(evidence: ExtensionEvidence): List<ProbeResult> {
        val geometryDetail = if (evidence.geometryMatches.isEmpty()) {
            "geometry extensions: none"
        } else {
            "geometry extensions: ${evidence.geometryMatches.joinToString()}"
        }
        val tessDetail = if (evidence.tessellationMatches.isEmpty()) {
            "tessellation extensions: none"
        } else {
            "tessellation extensions: ${evidence.tessellationMatches.joinToString()}"
        }

        return listOf(
            ProbeResult(
                name = "Extension Enumeration",
                supported = evidence.totalExtensions > 0,
                errorCode = if (evidence.totalExtensions > 0) 0 else -1,
                errorName = if (evidence.totalExtensions > 0) "GL_NO_ERROR" else "NO_EXTENSION_LIST",
                detail = "Enumerated ${evidence.totalExtensions} extensions from the current GL context"
            ),
            ProbeResult(
                name = "Geometry Extension Presence",
                supported = evidence.geometryMatches.isNotEmpty(),
                errorCode = if (evidence.geometryMatches.isNotEmpty()) 0 else -1,
                errorName = if (evidence.geometryMatches.isNotEmpty()) "FOUND" else "NOT_FOUND",
                detail = geometryDetail
            ),
            ProbeResult(
                name = "Tessellation Extension Presence",
                supported = evidence.tessellationMatches.isNotEmpty(),
                errorCode = if (evidence.tessellationMatches.isNotEmpty()) 0 else -1,
                errorName = if (evidence.tessellationMatches.isNotEmpty()) "FOUND" else "NOT_FOUND",
                detail = tessDetail
            )
        )
    }

    private fun isAtLeastEs32(info: DeviceInfo): Boolean {
        return info.major > REQUIRED_MAJOR || (info.major == REQUIRED_MAJOR && info.minor >= REQUIRED_MINOR)
    }

    private fun readDeviceInfo(): DeviceInfo {
        val major = IntArray(1)
        val minor = IntArray(1)
        GLES32.glGetIntegerv(GLES32.GL_MAJOR_VERSION, major, 0)
        GLES32.glGetIntegerv(GLES32.GL_MINOR_VERSION, minor, 0)
        return DeviceInfo(
            vendor = GLES32.glGetString(GLES32.GL_VENDOR) ?: "unknown",
            renderer = GLES32.glGetString(GLES32.GL_RENDERER) ?: "unknown",
            version = GLES32.glGetString(GLES32.GL_VERSION) ?: "unknown",
            glslVersion = GLES32.glGetString(GLES32.GL_SHADING_LANGUAGE_VERSION) ?: "unknown",
            major = major[0],
            minor = minor[0]
        )
    }

    private fun probeTexStorage3D(): ProbeResult {
        val tex = IntArray(1)
        GLES32.glGenTextures(1, tex, 0)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_3D, tex[0])
        clearErrors()
        GLES32.glTexStorage3D(GLES32.GL_TEXTURE_3D, 1, GLES32.GL_RGBA8, 4, 4, 4)
        val err = GLES32.glGetError()
        GLES32.glDeleteTextures(1, tex, 0)
        return buildResult("glTexStorage3D", err, "allocate 3D immutable texture")
    }

    private fun probeGeometryShader320Compile(): ProbeResult {
        val shader = GLES32.glCreateShader(GLES32.GL_GEOMETRY_SHADER)
        val source = "#version 320 es\nlayout(points) in;\nlayout(points, max_vertices = 1) out;\nvoid main(){ gl_Position = gl_in[0].gl_Position; EmitVertex(); EndPrimitive(); }"
        GLES32.glShaderSource(shader, source)
        GLES32.glCompileShader(shader)

        val compile = IntArray(1)
        GLES32.glGetShaderiv(shader, GLES32.GL_COMPILE_STATUS, compile, 0)
        if (compile[0] != GLES32.GL_TRUE) {
            val log = GLES32.glGetShaderInfoLog(shader)
            GLES32.glDeleteShader(shader)
            return ProbeResult(
                name = "GL_GEOMETRY_SHADER (#version 320 es)",
                supported = false,
                errorCode = -1,
                errorName = "COMPILE_ERROR",
                detail = "geometry shader compile failed: $log"
            )
        }

        GLES32.glDeleteShader(shader)
        return ProbeResult(
            name = "GL_GEOMETRY_SHADER (#version 320 es)",
            supported = true,
            errorCode = 0,
            errorName = "GL_NO_ERROR",
            detail = "geometry shader for ES 3.2 compiled successfully"
        )
    }

    private fun probeTessControlShader320Compile(): ProbeResult {
        val shader = GLES32.glCreateShader(GLES32.GL_TESS_CONTROL_SHADER)
        val source = "#version 320 es\nlayout(vertices = 3) out;\nvoid main(){ gl_out[gl_InvocationID].gl_Position = gl_in[gl_InvocationID].gl_Position; gl_TessLevelOuter[0]=1.0; gl_TessLevelOuter[1]=1.0; gl_TessLevelOuter[2]=1.0; gl_TessLevelInner[0]=1.0; }"
        GLES32.glShaderSource(shader, source)
        GLES32.glCompileShader(shader)

        val compile = IntArray(1)
        GLES32.glGetShaderiv(shader, GLES32.GL_COMPILE_STATUS, compile, 0)
        if (compile[0] != GLES32.GL_TRUE) {
            val log = GLES32.glGetShaderInfoLog(shader)
            GLES32.glDeleteShader(shader)
            return ProbeResult(
                name = "GL_TESS_CONTROL_SHADER (#version 320 es)",
                supported = false,
                errorCode = -1,
                errorName = "COMPILE_ERROR",
                detail = "tess control shader compile failed: $log"
            )
        }

        GLES32.glDeleteShader(shader)
        return ProbeResult(
            name = "GL_TESS_CONTROL_SHADER (#version 320 es)",
            supported = true,
            errorCode = 0,
            errorName = "GL_NO_ERROR",
            detail = "tess control shader for ES 3.2 compiled successfully"
        )
    }

    private fun probeTessEvaluationShader320Compile(): ProbeResult {
        val shader = GLES32.glCreateShader(GLES32.GL_TESS_EVALUATION_SHADER)
        val source = "#version 320 es\nlayout(triangles, equal_spacing, cw) in;\nvoid main(){ gl_Position = gl_in[0].gl_Position; }"
        GLES32.glShaderSource(shader, source)
        GLES32.glCompileShader(shader)

        val compile = IntArray(1)
        GLES32.glGetShaderiv(shader, GLES32.GL_COMPILE_STATUS, compile, 0)
        if (compile[0] != GLES32.GL_TRUE) {
            val log = GLES32.glGetShaderInfoLog(shader)
            GLES32.glDeleteShader(shader)
            return ProbeResult(
                name = "GL_TESS_EVALUATION_SHADER (#version 320 es)",
                supported = false,
                errorCode = -1,
                errorName = "COMPILE_ERROR",
                detail = "tess evaluation shader compile failed: $log"
            )
        }

        GLES32.glDeleteShader(shader)
        return ProbeResult(
            name = "GL_TESS_EVALUATION_SHADER (#version 320 es)",
            supported = true,
            errorCode = 0,
            errorName = "GL_NO_ERROR",
            detail = "tess evaluation shader for ES 3.2 compiled successfully"
        )
    }

    private fun probeTessellationPipelineDraw(): ProbeResult {
        val vertexSource = "#version 320 es\nlayout(location = 0) in vec2 aPos;\nvoid main(){ gl_Position = vec4(aPos, 0.0, 1.0); }"
        val tessControlSource = "#version 320 es\nlayout(vertices = 3) out;\nvoid main(){ gl_out[gl_InvocationID].gl_Position = gl_in[gl_InvocationID].gl_Position; gl_TessLevelOuter[0] = 1.0; gl_TessLevelOuter[1] = 1.0; gl_TessLevelOuter[2] = 1.0; gl_TessLevelInner[0] = 1.0; }"
        val tessEvalSource = "#version 320 es\nlayout(triangles, equal_spacing, cw) in;\nvoid main(){ gl_Position = gl_TessCoord.x * gl_in[0].gl_Position + gl_TessCoord.y * gl_in[1].gl_Position + gl_TessCoord.z * gl_in[2].gl_Position; }"
        val fragmentSource = "#version 320 es\nprecision mediump float;\nout vec4 outColor;\nvoid main(){ outColor = vec4(1.0, 0.0, 0.0, 1.0); }"

        var program = 0
        val vao = IntArray(1)
        val vbo = IntArray(1)
        val shaderIds = mutableListOf<Int>()

        try {
            val vs = compileShader(GLES32.GL_VERTEX_SHADER, vertexSource)
            if (!vs.compiled) {
                return ProbeResult(
                    name = "Tessellation Pipeline Draw (compile+link+draw)",
                    supported = false,
                    errorCode = -1,
                    errorName = "VS_COMPILE_ERROR",
                    detail = "vertex shader compile failed: ${vs.log}"
                )
            }
            shaderIds += vs.shaderId

            val tcs = compileShader(GLES32.GL_TESS_CONTROL_SHADER, tessControlSource)
            if (!tcs.compiled) {
                return ProbeResult(
                    name = "Tessellation Pipeline Draw (compile+link+draw)",
                    supported = false,
                    errorCode = -1,
                    errorName = "TCS_COMPILE_ERROR",
                    detail = "tess control shader compile failed: ${tcs.log}"
                )
            }
            shaderIds += tcs.shaderId

            val tes = compileShader(GLES32.GL_TESS_EVALUATION_SHADER, tessEvalSource)
            if (!tes.compiled) {
                return ProbeResult(
                    name = "Tessellation Pipeline Draw (compile+link+draw)",
                    supported = false,
                    errorCode = -1,
                    errorName = "TES_COMPILE_ERROR",
                    detail = "tess evaluation shader compile failed: ${tes.log}"
                )
            }
            shaderIds += tes.shaderId

            val fs = compileShader(GLES32.GL_FRAGMENT_SHADER, fragmentSource)
            if (!fs.compiled) {
                return ProbeResult(
                    name = "Tessellation Pipeline Draw (compile+link+draw)",
                    supported = false,
                    errorCode = -1,
                    errorName = "FS_COMPILE_ERROR",
                    detail = "fragment shader compile failed: ${fs.log}"
                )
            }
            shaderIds += fs.shaderId

            program = GLES32.glCreateProgram()
            shaderIds.forEach { GLES32.glAttachShader(program, it) }
            GLES32.glLinkProgram(program)

            val link = IntArray(1)
            GLES32.glGetProgramiv(program, GLES32.GL_LINK_STATUS, link, 0)
            if (link[0] != GLES32.GL_TRUE) {
                val log = GLES32.glGetProgramInfoLog(program)
                return ProbeResult(
                    name = "Tessellation Pipeline Draw (compile+link+draw)",
                    supported = false,
                    errorCode = -1,
                    errorName = "LINK_ERROR",
                    detail = "tess pipeline link failed: $log"
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

            clearErrors()
            GLES32.glUseProgram(program)
            GLES32.glPatchParameteri(GLES32.GL_PATCH_VERTICES, 3)
            GLES32.glDrawArrays(GLES32.GL_PATCHES, 0, 3)
            val err = GLES32.glGetError()
            return buildResult(
                "Tessellation Pipeline Draw (compile+link+draw)",
                err,
                "compiled, linked and executed glDrawArrays(GL_PATCHES, 0, 3)"
            )
        } finally {
            try {
                GLES32.glUseProgram(0)
            } catch (_: Throwable) {
            }
            try {
                GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, 0)
            } catch (_: Throwable) {
            }
            try {
                GLES32.glBindVertexArray(0)
            } catch (_: Throwable) {
            }
            try {
                if (vbo[0] != 0) {
                    GLES32.glDeleteBuffers(1, vbo, 0)
                }
            } catch (_: Throwable) {
            }
            try {
                if (vao[0] != 0) {
                    GLES32.glDeleteVertexArrays(1, vao, 0)
                }
            } catch (_: Throwable) {
            }
            try {
                if (program != 0) {
                    GLES32.glDeleteProgram(program)
                }
            } catch (_: Throwable) {
            }
            shaderIds.forEach { shaderId ->
                try {
                    GLES32.glDeleteShader(shaderId)
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun compileShader(type: Int, source: String): ShaderCompileEvidence {
        val shader = GLES32.glCreateShader(type)
        if (shader == 0) {
            return ShaderCompileEvidence(shaderId = 0, compiled = false, log = "glCreateShader returned 0")
        }

        GLES32.glShaderSource(shader, source)
        GLES32.glCompileShader(shader)
        val status = IntArray(1)
        GLES32.glGetShaderiv(shader, GLES32.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES32.GL_TRUE) {
            val log = GLES32.glGetShaderInfoLog(shader)
            GLES32.glDeleteShader(shader)
            return ShaderCompileEvidence(shaderId = 0, compiled = false, log = log)
        }

        return ShaderCompileEvidence(shaderId = shader, compiled = true, log = "")
    }

    private fun buildEvidenceVerdict(
        deviceInfo: DeviceInfo,
        extensionEvidence: ExtensionEvidence,
        probeResults: List<ProbeResult>
    ): ProbeResult {
        val geometryCompile = probeResults.firstOrNull { it.name == "GL_GEOMETRY_SHADER (#version 320 es)" }
        val tcsCompile = probeResults.firstOrNull { it.name == "GL_TESS_CONTROL_SHADER (#version 320 es)" }
        val tesCompile = probeResults.firstOrNull { it.name == "GL_TESS_EVALUATION_SHADER (#version 320 es)" }
        val tessDraw = probeResults.firstOrNull { it.name == "Tessellation Pipeline Draw (compile+link+draw)" }

        val drawPass = tessDraw?.supported == true
        val compilePassCount = listOf(geometryCompile, tcsCompile, tesCompile).count { it?.supported == true }
        val extHint = extensionEvidence.geometryMatches.isNotEmpty() || extensionEvidence.tessellationMatches.isNotEmpty()

        val errorName = when {
            drawPass -> "FINAL_POSITIVE"
            compilePassCount >= 2 -> "PARTIAL_SHADER_SUPPORT"
            extHint -> "EXTENSION_HINT_ONLY"
            else -> "FINAL_NEGATIVE"
        }

        val detail = "context=${deviceInfo.major}.${deviceInfo.minor}, geometryExt=${extensionEvidence.geometryMatches.isNotEmpty()}, tessExt=${extensionEvidence.tessellationMatches.isNotEmpty()}, shaderCompilePass=$compilePassCount/3, tessDrawPass=$drawPass"

        return ProbeResult(
            name = "ES3.2 Partial Support Final Verdict",
            supported = drawPass,
            errorCode = if (drawPass) 0 else -1,
            errorName = errorName,
            detail = detail
        )
    }

    private fun probePrimitiveBoundingBox(): ProbeResult {
        clearErrors()
        GLES32.glPrimitiveBoundingBox(-1f, -1f, -1f, 0f, 1f, 1f, 1f, 1f)
        val err = GLES32.glGetError()
        return buildResult("glPrimitiveBoundingBox", err, "set primitive bounding box (ES 3.2)")
    }

    private fun probePatchParameteri(): ProbeResult {
        clearErrors()
        GLES32.glPatchParameteri(GLES32.GL_PATCH_VERTICES, 3)
        val err = GLES32.glGetError()
        return buildResult("glPatchParameteri", err, "set patch vertices for tessellation (ES 3.2)")
    }

    private fun probeMinSampleShading(): ProbeResult {
        clearErrors()
        GLES32.glMinSampleShading(1.0f)
        val err = GLES32.glGetError()
        return buildResult("glMinSampleShading", err, "set minimum sample shading rate")
    }

    private fun probeTexStorage3DMultisample(): ProbeResult {
        val tex = IntArray(1)
        GLES32.glGenTextures(1, tex, 0)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D_MULTISAMPLE_ARRAY, tex[0])
        clearErrors()
        GLES32.glTexStorage3DMultisample(
            GLES32.GL_TEXTURE_2D_MULTISAMPLE_ARRAY,
            1,
            GLES32.GL_RGBA8,
            4,
            4,
            2,
            true
        )
        val err = GLES32.glGetError()
        GLES32.glDeleteTextures(1, tex, 0)
        return buildResult("glTexStorage3DMultisample", err, "allocate multisample 3D storage")
    }

    private fun probeTexBufferRange(): ProbeResult {
        val tex = IntArray(1)
        val buf = IntArray(1)
        GLES32.glGenTextures(1, tex, 0)
        GLES32.glGenBuffers(1, buf, 0)

        GLES32.glBindBuffer(GLES32.GL_TEXTURE_BUFFER, buf[0])
        GLES32.glBufferData(GLES32.GL_TEXTURE_BUFFER, 64, null, GLES32.GL_STATIC_DRAW)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_BUFFER, tex[0])

        clearErrors()
        GLES32.glTexBufferRange(GLES32.GL_TEXTURE_BUFFER, GLES32.GL_R8, buf[0], 0, 64)
        val err = GLES32.glGetError()

        GLES32.glDeleteTextures(1, tex, 0)
        GLES32.glDeleteBuffers(1, buf, 0)
        return buildResult("glTexBufferRange", err, "bind subrange of buffer as texture")
    }

    private fun probeSampleMaski(): ProbeResult {
        clearErrors()
        GLES32.glSampleMaski(0, -1)
        val err = GLES32.glGetError()
        return buildResult("glSampleMaski", err, "set sample mask for sample buffer 0")
    }

    private fun probeCopyImageSubData(): ProbeResult {
        val tex = IntArray(2)
        GLES32.glGenTextures(2, tex, 0)

        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, tex[0])
        GLES32.glTexStorage2D(GLES32.GL_TEXTURE_2D, 1, GLES32.GL_RGBA8, 4, 4)

        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, tex[1])
        GLES32.glTexStorage2D(GLES32.GL_TEXTURE_2D, 1, GLES32.GL_RGBA8, 4, 4)

        clearErrors()
        GLES32.glCopyImageSubData(
            tex[0], GLES32.GL_TEXTURE_2D, 0, 0, 0, 0,
            tex[1], GLES32.GL_TEXTURE_2D, 0, 0, 0, 0,
            4, 4, 1
        )
        val err = GLES32.glGetError()
        GLES32.glDeleteTextures(2, tex, 0)
        return buildResult("glCopyImageSubData", err, "copy texture region between two images")
    }

    private fun probeDispatchCompute(): ProbeResult {
        val shader = GLES32.glCreateShader(GLES32.GL_COMPUTE_SHADER)
        val source = "#version 310 es\nlayout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;\nvoid main(){}"
        GLES32.glShaderSource(shader, source)
        GLES32.glCompileShader(shader)

        val compile = IntArray(1)
        GLES32.glGetShaderiv(shader, GLES32.GL_COMPILE_STATUS, compile, 0)
        if (compile[0] != GLES32.GL_TRUE) {
            val log = GLES32.glGetShaderInfoLog(shader)
            GLES32.glDeleteShader(shader)
            return ProbeResult(
                name = "glDispatchCompute",
                supported = false,
                errorCode = -1,
                errorName = "COMPILE_ERROR",
                detail = "compute shader compile failed: $log"
            )
        }

        val program = GLES32.glCreateProgram()
        GLES32.glAttachShader(program, shader)
        GLES32.glLinkProgram(program)

        val link = IntArray(1)
        GLES32.glGetProgramiv(program, GLES32.GL_LINK_STATUS, link, 0)
        if (link[0] != GLES32.GL_TRUE) {
            val log = GLES32.glGetProgramInfoLog(program)
            GLES32.glDeleteProgram(program)
            GLES32.glDeleteShader(shader)
            return ProbeResult(
                name = "glDispatchCompute",
                supported = false,
                errorCode = -1,
                errorName = "LINK_ERROR",
                detail = "compute program link failed: $log"
            )
        }

        GLES32.glUseProgram(program)
        clearErrors()
        GLES32.glDispatchCompute(1, 1, 1)
        val err = GLES32.glGetError()

        GLES32.glUseProgram(0)
        GLES32.glDeleteProgram(program)
        GLES32.glDeleteShader(shader)
        return buildResult("glDispatchCompute", err, "dispatch one compute work group")
    }

    private fun probeMemoryBarrier(): ProbeResult {
        clearErrors()
        GLES31.glMemoryBarrier(GLES32.GL_ALL_BARRIER_BITS)
        val err = GLES32.glGetError()
        return buildResult("glMemoryBarrier", err, "issue full memory barrier")
    }

    private fun probeBindImageTexture(): ProbeResult {
        val tex = IntArray(1)
        GLES32.glGenTextures(1, tex, 0)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, tex[0])
        GLES32.glTexStorage2D(GLES32.GL_TEXTURE_2D, 1, GLES32.GL_RGBA8, 4, 4)

        clearErrors()
        GLES32.glBindImageTexture(0, tex[0], 0, false, 0, GLES32.GL_READ_WRITE, GLES32.GL_RGBA8)
        val err = GLES32.glGetError()

        GLES32.glDeleteTextures(1, tex, 0)
        return buildResult("glBindImageTexture", err, "bind texture as image unit")
    }

    private fun probeDebugMessageInsert(): ProbeResult {
        clearErrors()
        GLES32.glDebugMessageInsert(
            GLES32.GL_DEBUG_SOURCE_APPLICATION,
            GLES32.GL_DEBUG_TYPE_MARKER,
            1,
            GLES32.GL_DEBUG_SEVERITY_NOTIFICATION,
            5,
            "probe"
        )
        val err = GLES32.glGetError()
        return buildResult("glDebugMessageInsert", err, "insert one debug marker")
    }

    private fun probeDebugMessageControl(): ProbeResult {
        clearErrors()
        GLES32.glDebugMessageControl(
            GLES32.GL_DONT_CARE,
            GLES32.GL_DONT_CARE,
            GLES32.GL_DONT_CARE,
            0,
            IntArray(1),
            0,
            true
        )
        val err = GLES32.glGetError()
        return buildResult("glDebugMessageControl", err, "configure debug message filtering")
    }

    private fun probeDebugGroupPushPop(): ProbeResult {
        clearErrors()
        GLES32.glPushDebugGroup(
            GLES32.GL_DEBUG_SOURCE_APPLICATION,
            7,
            11,
            "probe-group"
        )
        GLES32.glPopDebugGroup()
        val err = GLES32.glGetError()
        return buildResult("glPushDebugGroup+glPopDebugGroup", err, "push and pop one debug group")
    }

    private fun probeObjectLabel(): ProbeResult {
        val buf = IntArray(1)
        GLES32.glGenBuffers(1, buf, 0)
        clearErrors()
        val label = "probe-buffer"
        GLES32.glObjectLabel(GLES32.GL_BUFFER, buf[0], label.length, label)
        val err = GLES32.glGetError()
        GLES32.glDeleteBuffers(1, buf, 0)
        return buildResult("glObjectLabel", err, "assign debug label to a GL buffer object")
    }

    private fun probeFramebufferTexture(): ProbeResult {
        val tex = IntArray(1)
        val fbo = IntArray(1)

        GLES32.glGenTextures(1, tex, 0)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, tex[0])
        GLES32.glTexStorage2D(GLES32.GL_TEXTURE_2D, 1, GLES32.GL_RGBA8, 4, 4)

        GLES32.glGenFramebuffers(1, fbo, 0)
        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, fbo[0])

        clearErrors()
        GLES32.glFramebufferTexture(GLES32.GL_FRAMEBUFFER, GLES32.GL_COLOR_ATTACHMENT0, tex[0], 0)
        val err = GLES32.glGetError()

        GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, 0)
        GLES32.glDeleteFramebuffers(1, fbo, 0)
        GLES32.glDeleteTextures(1, tex, 0)
        return buildResult("glFramebufferTexture", err, "attach texture to framebuffer with glFramebufferTexture")
    }

    private fun probeBlendEquationi(): ProbeResult {
        clearErrors()
        GLES32.glBlendEquationi(0, GLES32.GL_FUNC_ADD)
        val err = GLES32.glGetError()
        return buildResult("glBlendEquationi", err, "set per-draw-buffer blend equation")
    }

    private fun probeBlendFunci(): ProbeResult {
        clearErrors()
        GLES32.glBlendFunci(0, GLES32.GL_ONE, GLES32.GL_ZERO)
        val err = GLES32.glGetError()
        return buildResult("glBlendFunci", err, "set per-draw-buffer blend factors")
    }

    private fun probeColorMaski(): ProbeResult {
        clearErrors()
        GLES32.glColorMaski(0, true, true, true, true)
        val err = GLES32.glGetError()
        return buildResult("glColorMaski", err, "set per-draw-buffer color mask")
    }

    private fun probeBlendBarrier(): ProbeResult {
        clearErrors()
        GLES32.glBlendBarrier()
        val err = GLES32.glGetError()
        return buildResult("glBlendBarrier", err, "issue blend barrier")
    }

    private fun clearErrors() {
        var err = GLES32.glGetError()
        while (err != GLES32.GL_NO_ERROR) {
            err = GLES32.glGetError()
        }
    }

    private fun buildResult(name: String, errorCode: Int, detail: String): ProbeResult {
        return ProbeResult(
            name = name,
            supported = errorCode == GLES32.GL_NO_ERROR,
            errorCode = errorCode,
            errorName = errorName(errorCode),
            detail = detail
        )
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
