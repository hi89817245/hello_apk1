package com.example.hello_apk1

import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val channelName = "com.example.hello_apk1/camera"
    private var methodChannel: MethodChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName).also { channel ->
            channel.setMethodCallHandler { call, result ->
                when (call.method) {
                    "startCaptureSession" -> {
                        val args = call.arguments as? Map<*, *>
                        val uid = args?.get("uid") as? String
                        val config = args?.get("config") as? Map<*, *>
                        if (uid.isNullOrBlank() || config == null) {
                            result.error("invalid_arguments", "缺少必要參數", null)
                            return@setMethodCallHandler
                        }
                        val payload = CaptureConfig.fromMap(config)
                        val intent = Intent(this, CameraActivity::class.java).apply {
                            putExtra(CameraActivity.EXTRA_UID, uid.trim())
                            putExtra(CameraActivity.EXTRA_ZOOM_RATIO, payload.zoomRatio)
                            putExtra(CameraActivity.EXTRA_STABILIZE_SECONDS, payload.stabilizeSeconds)
                            putExtra(CameraActivity.EXTRA_SUCCESS_VOICE, payload.successVoice)
                            putExtra(CameraActivity.EXTRA_PREVIEW_SECONDS, payload.previewSeconds)
                            putExtra(CameraActivity.EXTRA_STORAGE_PATH, payload.storagePath)
                            putExtra(CameraActivity.EXTRA_ALLOW_MULTIPLE, payload.allowMultiple)
                            putExtra(CameraActivity.EXTRA_ASPECT_RATIO, payload.aspectRatio)
                            putExtra(CameraActivity.EXTRA_OVERLAY_SCALE, payload.overlayScale)
                            putExtra(CameraActivity.EXTRA_DETECTION_VARIANCE, payload.detectionVariance)
                            putExtra(CameraActivity.EXTRA_ALLOW_RETAKE, payload.allowRetake)
                        }
                        startActivity(intent)
                        result.success(null)
                    }

                    else -> result.notImplemented()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        methodChannel?.setMethodCallHandler(null)
        methodChannel = null
    }

    private data class CaptureConfig(
        val zoomRatio: Float,
        val stabilizeSeconds: Float,
        val successVoice: String,
        val previewSeconds: Float,
        val storagePath: String,
        val allowMultiple: Boolean,
        val aspectRatio: String,
        val overlayScale: Float,
        val detectionVariance: Double,
        val allowRetake: Boolean,
    ) {
        companion object {
            fun fromMap(map: Map<*, *>): CaptureConfig {
                val zoom = (map["pama1"] as? Number)?.toFloat() ?: 3f
                val stabilize = (map["pama2"] as? Number)?.toFloat() ?: 0.5f
                val voice = (map["pama3"] as? String)?.ifBlank { "好" } ?: "好"
                val preview = (map["pama4"] as? Number)?.toFloat() ?: 10f
                val path = (map["pama5"] as? String)?.ifBlank { "Pictures/賽鴿虹膜建檔" } ?: "Pictures/賽鴿虹膜建檔"
                val allowMultiple = when ((map["pama6"] as? String)?.lowercase()) {
                    "n", "no", "false" -> false
                    else -> true
                }
                val ratio = (map["pama7"] as? String)?.ifBlank { "1:1" } ?: "1:1"
                val overlay = (map["pama8"] as? Number)?.toFloat() ?: 0.78f
                val variance = (map["pama9"] as? Number)?.toDouble() ?: 1500.0
                val allowRetake = (map["pama10"] as? String)?.lowercase() == "y"
                return CaptureConfig(
                    zoomRatio = zoom.coerceIn(1f, 10f),
                    stabilizeSeconds = stabilize.coerceIn(0.2f, 5f),
                    successVoice = voice,
                    previewSeconds = preview.coerceIn(1f, 60f),
                    storagePath = path,
                    allowMultiple = allowMultiple,
                    aspectRatio = ratio,
                    overlayScale = overlay.coerceIn(0.4f, 0.95f),
                    detectionVariance = variance.coerceIn(300.0, 4000.0),
                    allowRetake = allowRetake,
                )
            }
        }
    }
}
