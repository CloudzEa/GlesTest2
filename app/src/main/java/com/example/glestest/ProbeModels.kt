package com.example.glestest

data class DeviceInfo(
    val vendor: String = "unknown",
    val renderer: String = "unknown",
    val version: String = "unknown",
    val glslVersion: String = "unknown",
    val major: Int = -1,
    val minor: Int = -1
)

data class ProbeResult(
    val name: String,
    val supported: Boolean,
    val errorCode: Int,
    val errorName: String,
    val detail: String
)

data class ProbeReport(
    val deviceInfo: DeviceInfo,
    val results: List<ProbeResult>
)
