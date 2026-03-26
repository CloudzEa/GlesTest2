package com.example.glestest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.glestest.ui.theme.GLESTestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GLESTestTheme {
                ProbeScreen()
            }
        }
    }
}

@Composable
private fun ProbeScreen() {
    val context = LocalContext.current
    val results = remember { mutableStateListOf<ProbeResult>() }
    var deviceInfo by remember { mutableStateOf(DeviceInfo()) }
    var previewMode by remember { mutableStateOf(PreviewMode.STRICT_32) }
    var surfaceView by remember { mutableStateOf<ProbeGLSurfaceView?>(null) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "OpenGL ES 3.2 Probe",
                style = MaterialTheme.typography.titleLarge
            )

            Text(text = "Vendor: ${deviceInfo.vendor}")
            Text(text = "Renderer: ${deviceInfo.renderer}")
            Text(text = "Version: ${deviceInfo.version}")
            Text(text = "GLSL: ${deviceInfo.glslVersion}")
            Text(text = "API Level: ${deviceInfo.major}.${deviceInfo.minor}")
            Text(
                text = if (deviceInfo.major > 3 || (deviceInfo.major == 3 && deviceInfo.minor >= 2)) {
                    "Strict ES 3.2 mode: READY"
                } else {
                    "Strict ES 3.2 mode: NOT READY (context < 3.2)"
                }
            )
            Text(text = "渲染窗颜色规则：严格3.2成功=绿色，失败=红色")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        previewMode = PreviewMode.STRICT_32
                        surfaceView?.setPreviewMode(previewMode)
                    },
                    enabled = previewMode != PreviewMode.STRICT_32
                ) {
                    Text("严格3.2预览")
                }

                OutlinedButton(
                    onClick = {
                        previewMode = PreviewMode.CONTROL_31
                        surfaceView?.setPreviewMode(previewMode)
                    },
                    enabled = previewMode != PreviewMode.CONTROL_31
                ) {
                    Text("对照组ES3.1三角形")
                }
            }

            AndroidView(
                factory = {
                    ProbeGLSurfaceView(context).apply {
                        surfaceView = this
                        setPreviewMode(previewMode)
                        onReport = { report ->
                            deviceInfo = report.deviceInfo
                            results.clear()
                            results.addAll(report.results)
                        }
                    }
                },
                update = { view ->
                    surfaceView = view
                    view.setPreviewMode(previewMode)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results) { result ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(text = result.name, style = MaterialTheme.typography.titleMedium)
                            Text(text = "Status: ${if (result.supported) "PASS" else "FAIL"}")
                            Text(text = "glError: ${result.errorName} (${result.errorCode})")
                            Text(text = result.detail)
                        }
                    }
                }
            }
        }
    }
}