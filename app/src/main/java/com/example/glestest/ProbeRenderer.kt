package com.example.glestest

import android.opengl.GLES32
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

enum class PreviewMode {
    STRICT_32,
    CONTROL_31
}

class ProbeRenderer(
    private val onReportReady: (ProbeReport) -> Unit
) : GLSurfaceView.Renderer {

    private var reported = false
    private var previewMode: PreviewMode = PreviewMode.STRICT_32

    private val strictPreview = Strict32TrianglePreview()
    private val controlPreview = Control31TrianglePreview()

    private var strictInitResult: ProbeResult = ProbeResult(
        name = "Strict3.2 Triangle Preview Init",
        supported = false,
        errorCode = -1,
        errorName = "NOT_STARTED",
        detail = "preview init not started"
    )
    private var strictDrawResult: ProbeResult = ProbeResult(
        name = "Strict3.2 Triangle Preview Draw",
        supported = false,
        errorCode = -1,
        errorName = "NOT_STARTED",
        detail = "preview draw not started"
    )
    private var controlInitResult: ProbeResult = ProbeResult(
        name = "Control3.1 Triangle Preview Init",
        supported = false,
        errorCode = -1,
        errorName = "NOT_STARTED",
        detail = "control preview init not started"
    )
    private var controlDrawResult: ProbeResult = ProbeResult(
        name = "Control3.1 Triangle Preview Draw",
        supported = false,
        errorCode = -1,
        errorName = "NOT_STARTED",
        detail = "control preview draw not started"
    )

    fun setPreviewMode(mode: PreviewMode) {
        previewMode = mode
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        strictInitResult = try {
            strictPreview.init()
        } catch (_: Throwable) {
            strictInitResult = ProbeResult(
                name = "Strict3.2 Triangle Preview Init",
                supported = false,
                errorCode = -1,
                errorName = "EXCEPTION",
                detail = "preview init threw an exception"
            )
            strictInitResult
        }

        controlInitResult = try {
            controlPreview.init()
        } catch (_: Throwable) {
            controlInitResult = ProbeResult(
                name = "Control3.1 Triangle Preview Init",
                supported = false,
                errorCode = -1,
                errorName = "EXCEPTION",
                detail = "control preview init threw an exception"
            )
            controlInitResult
        }

        applyBackgroundColorForMode()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        try {
            GLES32.glViewport(0, 0, width, height)
        } catch (_: Throwable) {
            // Keep renderer alive even when the backend rejects viewport updates.
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        try {
            applyBackgroundColorForMode()
            GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT or GLES32.GL_DEPTH_BUFFER_BIT)

            when (previewMode) {
                PreviewMode.STRICT_32 -> drawStrictPreview()
                PreviewMode.CONTROL_31 -> drawControlPreview()
            }
        } catch (_: Throwable) {
            // Rendering clear failure should not crash probe reporting.
            if (previewMode == PreviewMode.STRICT_32) {
                strictDrawResult = ProbeResult(
                    name = "Strict3.2 Triangle Preview Draw",
                    supported = false,
                    errorCode = -1,
                    errorName = "EXCEPTION",
                    detail = "preview draw threw an exception"
                )
            } else {
                controlDrawResult = ProbeResult(
                    name = "Control3.1 Triangle Preview Draw",
                    supported = false,
                    errorCode = -1,
                    errorName = "EXCEPTION",
                    detail = "control preview draw threw an exception"
                )
            }
        }

        if (!reported) {
            reported = true
            val report = try {
                Gles32Probes.runAll()
            } catch (t: Throwable) {
                ProbeReport(
                    deviceInfo = DeviceInfo(),
                    results = listOf(
                        ProbeResult(
                            name = "RendererFatal",
                            supported = false,
                            errorCode = -1,
                            errorName = "FATAL_EXCEPTION",
                            detail = "Renderer guarded: ${t::class.java.simpleName}: ${t.message ?: "no message"}"
                        )
                    )
                )
            }

            val mergedReport = ProbeReport(
                deviceInfo = report.deviceInfo,
                results = listOf(
                    strictInitResult,
                    strictDrawResult,
                    controlInitResult,
                    controlDrawResult
                ) + report.results
            )
            try {
                ProbeLogcatReporter.logReport(mergedReport)
            } catch (_: Throwable) {
                // Logging failure should never impact UI reporting.
            }
            onReportReady(mergedReport)
        }
    }

    private fun drawStrictPreview() {
        if (strictInitResult.supported) {
            val drawErr = strictPreview.draw()
            strictDrawResult = ProbeResult(
                name = "Strict3.2 Triangle Preview Draw",
                supported = drawErr == GLES32.GL_NO_ERROR,
                errorCode = drawErr,
                errorName = errorName(drawErr),
                detail = "strict #version 320 es preview draw call executed"
            )
        } else {
            strictDrawResult = ProbeResult(
                name = "Strict3.2 Triangle Preview Draw",
                supported = false,
                errorCode = -1,
                errorName = "SKIPPED",
                detail = "preview draw skipped because strict init failed"
            )
        }
    }

    private fun drawControlPreview() {
        if (controlInitResult.supported) {
            val drawErr = controlPreview.draw()
            controlDrawResult = ProbeResult(
                name = "Control3.1 Triangle Preview Draw",
                supported = drawErr == GLES32.GL_NO_ERROR,
                errorCode = drawErr,
                errorName = errorName(drawErr),
                detail = "control #version 310 es preview draw call executed"
            )
        } else {
            controlDrawResult = ProbeResult(
                name = "Control3.1 Triangle Preview Draw",
                supported = false,
                errorCode = -1,
                errorName = "SKIPPED",
                detail = "control preview draw skipped because control init failed"
            )
        }
    }

    private fun applyBackgroundColorForMode() {
        try {
            when (previewMode) {
                PreviewMode.STRICT_32 -> {
                    if (strictInitResult.supported) {
                        // Strict 3.2 preview success: green background.
                        GLES32.glClearColor(0.06f, 0.26f, 0.09f, 1f)
                    } else {
                        // Strict 3.2 preview failure: red background.
                        GLES32.glClearColor(0.28f, 0.05f, 0.06f, 1f)
                    }
                }

                PreviewMode.CONTROL_31 -> {
                    if (controlInitResult.supported) {
                        GLES32.glClearColor(0.05f, 0.07f, 0.16f, 1f)
                    } else {
                        GLES32.glClearColor(0.26f, 0.16f, 0.05f, 1f)
                    }
                }
            }
        } catch (_: Throwable) {
            // Failing to apply clear color should not break probe flow.
        }
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
