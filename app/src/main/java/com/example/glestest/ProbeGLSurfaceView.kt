package com.example.glestest

import android.content.Context
import android.opengl.GLSurfaceView

class ProbeGLSurfaceView(context: Context) : GLSurfaceView(context) {
    var onReport: ((ProbeReport) -> Unit)? = null

    private val renderer = ProbeRenderer { report ->
        post {
            onReport?.invoke(report)
        }
    }

    init {
        setEGLContextClientVersion(3)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setPreviewMode(mode: PreviewMode) {
        queueEvent {
            renderer.setPreviewMode(mode)
        }
    }
}
