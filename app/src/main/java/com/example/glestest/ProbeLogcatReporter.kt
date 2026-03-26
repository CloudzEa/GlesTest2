package com.example.glestest

import android.util.Log

object ProbeLogcatReporter {

    private const val TAG = "GLES32Probe"

    fun logReport(report: ProbeReport) {
        val passCount = report.results.count { it.supported }
        val failCount = report.results.size - passCount

        Log.i(TAG, "========== Probe Report Begin ==========")
        Log.i(TAG, "Device.vendor=${report.deviceInfo.vendor}")
        Log.i(TAG, "Device.renderer=${report.deviceInfo.renderer}")
        Log.i(TAG, "Device.version=${report.deviceInfo.version}")
        Log.i(TAG, "Device.glsl=${report.deviceInfo.glslVersion}")
        Log.i(TAG, "Device.apiLevel=${report.deviceInfo.major}.${report.deviceInfo.minor}")
        Log.i(TAG, "Summary.total=${report.results.size}, pass=$passCount, fail=$failCount")

        report.results.forEachIndexed { index, result ->
            Log.i(TAG, "[${index + 1}] ${result.name}")
            Log.i(TAG, "  status=${if (result.supported) "PASS" else "FAIL"}")
            Log.i(TAG, "  glError=${result.errorName} (${result.errorCode})")
            logLong("  detail=${result.detail}")
        }

        Log.i(TAG, "========== Probe Report End ==========")
    }

    private fun logLong(message: String) {
        if (message.length <= 3000) {
            Log.i(TAG, message)
            return
        }

        var start = 0
        while (start < message.length) {
            val end = (start + 3000).coerceAtMost(message.length)
            Log.i(TAG, message.substring(start, end))
            start = end
        }
    }
}
