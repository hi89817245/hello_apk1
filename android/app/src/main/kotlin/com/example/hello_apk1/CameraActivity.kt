package com.example.hello_apk1

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.lang.ref.WeakReference
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.jvm.JvmStatic
import android.util.Size

class CameraActivity : ComponentActivity() {
    companion object {
        const val EXTRA_UID = "extra_uid"
        const val EXTRA_ZOOM_RATIO = "extra_zoom_ratio"
        const val EXTRA_STABILIZE_SECONDS = "extra_stabilize_seconds"
        const val EXTRA_SUCCESS_VOICE = "extra_success_voice"
        const val EXTRA_PREVIEW_SECONDS = "extra_preview_seconds"
        const val EXTRA_STORAGE_PATH = "extra_storage_path"
        const val EXTRA_ALLOW_MULTIPLE = "extra_allow_multiple"
        const val EXTRA_ASPECT_RATIO = "extra_aspect_ratio"
        const val EXTRA_OVERLAY_SCALE = "extra_overlay_scale"
        const val EXTRA_DETECTION_VARIANCE = "extra_detection_variance"
        const val EXTRA_ALLOW_RETAKE = "extra_allow_retake"
        const val EXTRA_VOICE_ENABLED = "extra_voice_enabled"
        const val EXTRA_VOICE_DELAY = "extra_voice_delay"
        private const val BLUR_THRESHOLD = 110.0
        private var activeInstance: WeakReference<CameraActivity>? = null

        @JvmStatic
        fun updateActiveConfig(config: Map<*, *>): Boolean {
            val snapshot = CaptureConfigSnapshot.fromMap(config) ?: return false
            val activity = activeInstance?.get() ?: return false
            activity.runOnUiThread { activity.applyConfigUpdate(snapshot) }
            return true
        }
    }

    private enum class DetectionState { NONE, TARGET, STABLE }

    private lateinit var previewView: PreviewView
    private lateinit var aimOverlay: AimOverlayView
    private lateinit var captureButton: ImageButton
    private lateinit var closeButton: ImageButton
    private lateinit var statusLabel: TextView
    private lateinit var photoPreview: ImageView

    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var activeCamera: Camera? = null
    private var boundCameraProvider: ProcessCameraProvider? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.CAMERA] ?: false
        if (granted) {
            startCameraSession()
        } else {
            Toast.makeText(this, "需要相機權限才能使用", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private var requestedUid: String = ""
    private var requestedZoomRatio: Float = 3f
    private var stabilizeDurationMs: Long = 1_200
    private var previewDurationMs: Long = 3_000
    private var successVoice: String = "完成"
    private var storagePath: String = "Pictures/PigeonEyeRecords"
    private var allowMultipleShots: Boolean = true
    private var requestedAspectRatio: String = "1:1"
    private var overlayScale: Float = 0.78f
    private var detectionVariance: Double = 1500.0
    private var allowRetake: Boolean = true
    private var voiceEnabled: Boolean = true
    private var voiceDelayMs: Long = 0

    private var autoCaptureJob: Job? = null
    private var previewJob: Job? = null
    private var isCapturing: Boolean = false
    private var isPreviewing: Boolean = false
    private var hasCapturedAtLeastOnce: Boolean = false

    private var tts: TextToSpeech? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private var detectionState: DetectionState = DetectionState.NONE
    private var stableSince: Long? = null
    private var sessionStartTime: Long = 0L
    private var failureCount: Int = 0
    private var assistanceNotified: Boolean = false
    private var lastSpokenState: DetectionState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeInstance = WeakReference(this)
        setContentView(R.layout.activity_camera)
        previewView = findViewById(R.id.previewView)
        aimOverlay = findViewById(R.id.aimOverlay)
        captureButton = findViewById(R.id.captureButton)
        closeButton = findViewById(R.id.closeButton)
        statusLabel = findViewById(R.id.statusLabel)
        photoPreview = findViewById(R.id.photoPreview)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        initTextToSpeech()

        applyIntent(intent)

        captureButton.setOnClickListener { capturePhoto(autoTriggered = false) }
        closeButton.setOnClickListener { finish() }

        if (hasAllPermissions()) {
            startCameraSession()
        } else {
            requestPermissions()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        activeInstance = WeakReference(this)
        setIntent(intent)
        applyIntent(intent, fromNewIntent = true)
        restartSession()
    }

    override fun onDestroy() {
        if (activeInstance?.get() === this) {
            activeInstance = null
        }
        super.onDestroy()
        autoCaptureJob?.cancel()
        previewJob?.cancel()
        imageAnalysis?.clearAnalyzer()
        enableTorch(false)
        abandonAudioFocus()
        tts?.stop()
        tts?.shutdown()
        tts = null
        imageCapture = null
        activeCamera = null
    }

    private fun initTextToSpeech() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.TAIWAN
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                  tts?.setAudioAttributes(
                      AudioAttributes.Builder()
                          .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                          .build()
                  )
                }
                requestLoudAudio()
                speak("賽鴿虹膜建檔系統已啟動，請感應鴿環開始作業。", flush = true)
            }
        }
        tts?.setSpeechRate(0.9f)
        tts?.setPitch(1.0f)
    }

    private fun requestLoudAudio() {
        val manager = audioManager ?: return
        val stream = AudioManager.STREAM_MUSIC
        val maxVolume = manager.getStreamMaxVolume(stream)
        val targetVolume = (maxVolume * 0.9f).toInt().coerceAtLeast(1)
        manager.setStreamVolume(stream, targetVolume, AudioManager.FLAG_PLAY_SOUND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { }
                .build()
            manager.requestAudioFocus(audioFocusRequest!!)
        } else {
            manager.requestAudioFocus(null, stream, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
        } else {
            manager.abandonAudioFocus(null)
        }
    }

    private fun speak(message: String, flush: Boolean = false) {
        if (!voiceEnabled || message.isBlank()) return
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val performSpeak = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts?.speak(message, queueMode, null, System.currentTimeMillis().toString())
            } else {
                @Suppress("DEPRECATION")
                tts?.speak(message, queueMode, null)
            }
        }
        if (voiceDelayMs <= 0L) {
            performSpeak()
        } else {
            lifecycleScope.launch {
                delay(voiceDelayMs)
                if (voiceEnabled) {
                    performSpeak()
                }
            }
        }
    }

    private fun applyIntent(intent: Intent, fromNewIntent: Boolean = false) {
        requestedUid = intent.getStringExtra(EXTRA_UID)?.trim().orEmpty()
        requestedZoomRatio = intent.getFloatExtra(EXTRA_ZOOM_RATIO, 3f).coerceIn(1f, 10f)
        stabilizeDurationMs = (intent.getFloatExtra(EXTRA_STABILIZE_SECONDS, 1.2f) * 1000).toLong().coerceIn(200, 5000)
        previewDurationMs = (intent.getFloatExtra(EXTRA_PREVIEW_SECONDS, 3f) * 1000).toLong().coerceIn(1000, 60000)
        successVoice = intent.getStringExtra(EXTRA_SUCCESS_VOICE).orEmpty().ifBlank { "完成" }
        storagePath = intent.getStringExtra(EXTRA_STORAGE_PATH).orEmpty().ifBlank { "Pictures/PigeonEyeRecords" }
        allowMultipleShots = intent.getBooleanExtra(EXTRA_ALLOW_MULTIPLE, true)
        requestedAspectRatio = intent.getStringExtra(EXTRA_ASPECT_RATIO).orEmpty().ifBlank { "1:1" }
        overlayScale = intent.getFloatExtra(EXTRA_OVERLAY_SCALE, 0.78f).coerceIn(0.4f, 0.95f)
        detectionVariance = intent.getDoubleExtra(EXTRA_DETECTION_VARIANCE, 1500.0).coerceIn(300.0, 4000.0)
        allowRetake = intent.getBooleanExtra(EXTRA_ALLOW_RETAKE, true)
        voiceEnabled = intent.getBooleanExtra(EXTRA_VOICE_ENABLED, true)
        voiceDelayMs = (intent.getDoubleExtra(EXTRA_VOICE_DELAY, 0.0).coerceAtLeast(0.0) * 1000).toLong()

        aimOverlay.setOverlayScale(overlayScale)
        resetDetectionState(initial = true)

        if (requestedUid.isBlank()) {
            Toast.makeText(this, "必須提供 UID 才能繼續", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (!fromNewIntent) {
            statusLabel.text = "UID: $requestedUid\n請將賽鴿眼睛置於瞄準框中央"
        }
    }

    private fun resetDetectionState(initial: Boolean) {
        detectionState = DetectionState.NONE
        stableSince = null
        sessionStartTime = System.currentTimeMillis()
        failureCount = 0
        assistanceNotified = false
        lastSpokenState = null
        aimOverlay.visibility = View.VISIBLE
        photoPreview.visibility = View.GONE
        aimOverlay.setState(AimOverlayView.AimState.IDLE)
        findViewById<View>(R.id.greenFrame)?.visibility = View.GONE
        if (initial) {
            speak("請將賽鴿眼睛對準螢幕中央的瞄準框。", flush = false)
        }
    }

    private fun hasAllPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startCameraSession() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = runCatching { providerFuture.get() }.getOrNull()
            if (provider != null) {
                boundCameraProvider = provider
                bindUseCases(provider)
            } else {
                speak("無法啟動相機，請聯絡工作人員。", flush = true)
                Toast.makeText(this, "無法取得相機畫面", Toast.LENGTH_LONG).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindUseCases(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val teleCandidate = selectTelephotoCandidate()
        val selector = teleCandidate?.let { buildTelephotoSelector(cameraProvider, it.first) }
            ?: CameraSelector.DEFAULT_BACK_CAMERA

        val capture = createImageCapture()
        val analysis = createImageAnalysis()

        try {
            cameraProvider.unbindAll()
            imageCapture = capture
            imageAnalysis = analysis
            activeCamera = cameraProvider.bindToLifecycle(this, selector, preview, capture, analysis)
            boundCameraProvider = cameraProvider
        } catch (error: Exception) {
            speak("相機初始化失敗，請聯絡工作人員。", flush = true)
            Toast.makeText(this, "啟動相機失敗：${error.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        enableTorch(true)
        adjustZoom(teleCandidate)
        updateStatusPreparing(teleCandidate != null)
    }

    private fun adjustZoom(teleCandidate: Pair<String, Float>?) {
        val camera = activeCamera ?: return
        val zoomStateLiveData = camera.cameraInfo.zoomState
        val baseDesired = requestedZoomRatio.coerceAtLeast(1f)
        val desiredRatio = teleCandidate?.let { baseDesired } ?: baseDesired
        fun applyZoom(state: androidx.camera.core.ZoomState) {
            val bounded = desiredRatio.coerceIn(state.minZoomRatio, state.maxZoomRatio)
            camera.cameraControl.setZoomRatio(bounded)
        }
        val currentState = zoomStateLiveData.value
        if (currentState != null) {
            applyZoom(currentState)
        } else {
            val observer = object : androidx.lifecycle.Observer<androidx.camera.core.ZoomState> {
                override fun onChanged(state: androidx.camera.core.ZoomState) {
                    applyZoom(state)
                    zoomStateLiveData.removeObserver(this)
                }
            }
            zoomStateLiveData.observe(this, observer)
        }
    }

    private fun createImageCapture(): ImageCapture {
        val builder = ImageCapture.Builder()
            .setJpegQuality(100)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        computeTargetResolution()?.let { builder.setTargetResolution(it) }
        return builder.build()
    }

    private fun createImageAnalysis(): ImageAnalysis {
        return ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build().also { analysis ->
                analysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { image ->
                    analyzeImage(image)
                }
            }
    }

    private fun computeTargetResolution(): Size? {
        return when (requestedAspectRatio) {
            "1:1" -> Size(2048, 2048)
            "4:3" -> Size(4032, 3024)
            "16:9" -> Size(3840, 2160)
            else -> null
        }
    }

    private fun analyzeImage(image: ImageProxy) {
        if (isCapturing || isPreviewing) {
            image.close()
            return
        }
        val focusScore = computeFocusScore(image)
        val hasEye = focusScore >= detectionVariance
        val now = System.currentTimeMillis()
        runOnUiThread { updateDetectionState(hasEye, now) }
        image.close()
    }

    private fun computeFocusScore(image: ImageProxy): Double {
        val plane = image.planes.first()
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        buffer.rewind()
        val rowStride = plane.rowStride
        val width = image.width
        val height = image.height
        val roiSize = (min(width, height) * overlayScale).toInt().coerceAtLeast(32)
        val startX = ((width - roiSize) / 2).coerceAtLeast(1)
        val startY = ((height - roiSize) / 2).coerceAtLeast(1)
        val values = IntArray(roiSize * roiSize)
        for (y in 0 until roiSize) {
            val rowOffset = (startY + y) * rowStride
            for (x in 0 until roiSize) {
                val yy = bytes[rowOffset + startX + x].toInt() and 0xFF
                values[y * roiSize + x] = yy
            }
        }
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until roiSize - 1) {
            for (x in 1 until roiSize - 1) {
                val center = values[y * roiSize + x] * 4
                val laplacian = center - values[(y - 1) * roiSize + x] - values[(y + 1) * roiSize + x] - values[y * roiSize + x - 1] - values[y * roiSize + x + 1]
                sum += laplacian
                sumSq += laplacian * laplacian
                count++
            }
        }
        if (count == 0) return 0.0
        val mean = sum / count
        return (sumSq / count) - (mean * mean)
    }

    private fun updateDetectionState(hasEye: Boolean, now: Long) {
        if (!hasEye) {
            if (detectionState != DetectionState.NONE) {
                failureCount++
                speakState(DetectionState.NONE)
            }
            detectionState = DetectionState.NONE
            stableSince = null
            aimOverlay.setState(AimOverlayView.AimState.IDLE)
            findViewById<View>(R.id.greenFrame)?.visibility = View.GONE
        } else {
            when (detectionState) {
                DetectionState.NONE -> {
                    detectionState = DetectionState.TARGET
                    stableSince = now
                    aimOverlay.setState(AimOverlayView.AimState.TARGET)
                    speakState(DetectionState.TARGET)
                }
                DetectionState.TARGET -> {
                    if (stableSince == null) {
                        stableSince = now
                    }
                    val elapsed = now - (stableSince ?: now)
                    if (elapsed >= stabilizeDurationMs) {
                        detectionState = DetectionState.STABLE
                        aimOverlay.setState(AimOverlayView.AimState.STABLE)
                        speakState(DetectionState.STABLE)
                        triggerAutoCapture()
                    }
                }
                DetectionState.STABLE -> {
                    // wait for capture callback
                }
            }
        }
        checkAssistance(now)
    }

    private fun speakState(state: DetectionState) {
        if (lastSpokenState == state) return
        lastSpokenState = state
        when (state) {
            DetectionState.NONE -> speak("請將賽鴿眼睛對準瞄準框。", flush = false)
            DetectionState.TARGET -> speak("已偵測到眼睛，請保持穩定。", flush = false)
            DetectionState.STABLE -> speak("穩定完成，開始拍攝。", flush = false)
        }
    }

    private fun triggerAutoCapture() {
        if (isCapturing || isPreviewing) return
        findViewById<View>(R.id.greenFrame)?.visibility = View.VISIBLE
        capturePhoto(autoTriggered = true)
    }

    private fun checkAssistance(now: Long) {
        if (assistanceNotified) return
        val elapsed = now - sessionStartTime
        if (elapsed >= 30_000L || failureCount >= 3) {
            assistanceNotified = true
            speak("請作業人員協助。", flush = true)
            statusLabel.text = "UID: $requestedUid\n請作業人員協助"
        }
    }

    private fun restartSession() {
        autoCaptureJob?.cancel()
        previewJob?.cancel()
        photoPreview.visibility = View.GONE
        isCapturing = false
        isPreviewing = false
        hasCapturedAtLeastOnce = false
        resetDetectionState(initial = true)
        boundCameraProvider?.let { bindUseCases(it) } ?: startCameraSession()
    }

    private fun capturePhoto(autoTriggered: Boolean) {
        val capture = imageCapture ?: return
        if (isCapturing || isPreviewing) return
        isCapturing = true
        captureButton.isEnabled = false

        val timestamp = Date()
        val nameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.TAIWAN)
        val displayName = "${requestedUid}_${nameFormat.format(timestamp)}.jpg"
        val resolvedPath = resolveStoragePath()
        val watermarkTime = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.TAIWAN).format(timestamp)

        capture.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val bitmap = image.toBitmap() ?: throw IllegalStateException("無法解析影像資料")
                    val rotated = bitmap.rotate(image.imageInfo.rotationDegrees)
                    if (rotated != bitmap) {
                        bitmap.recycle()
                    }
                    val blurScore = evaluateBlur(rotated)
                    val watermarked = addWatermark(rotated, requestedUid, watermarkTime)
                    val savedUri = saveBitmapToGallery(watermarked, displayName, resolvedPath)
                        ?: throw IOException("無法儲存圖片")
                    runOnUiThread {
                        onCaptureSucceeded(displayName, savedUri, blurScore)
                    }
                } catch (e: Exception) {
                    val captureException = ImageCaptureException(
                        ImageCapture.ERROR_FILE_IO,
                        e.message ?: "影像處理失敗",
                        e
                    )
                    runOnUiThread {
                        onCaptureFailed(captureException)
                    }
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                runOnUiThread {
                    onCaptureFailed(exception)
                }
            }
        })
    }

    private fun onCaptureSucceeded(fileName: String, savedUri: Uri?, blurScore: Double) {
        hasCapturedAtLeastOnce = true
        isCapturing = false
        isPreviewing = true
        captureButton.isEnabled = allowMultipleShots || allowRetake
        aimOverlay.visibility = View.GONE
        findViewById<View>(R.id.greenFrame)?.visibility = View.GONE
        statusLabel.text = "UID: $requestedUid\n已完成拍攝：$fileName"
        if (savedUri != null) {
            photoPreview.setImageURI(savedUri)
            photoPreview.visibility = View.VISIBLE
        } else {
            photoPreview.visibility = View.GONE
        }
        speak(successVoice.ifBlank { "完成" }, flush = true)
        if (blurScore < BLUR_THRESHOLD) {
            speak("影像可能偏模糊，若需要請重新拍攝。", flush = false)
        }

        previewJob?.cancel()
        previewJob = lifecycleScope.launch {
            var remain = (previewDurationMs / 1000).toInt()
            while (remain > 0 && isActive) {
                statusLabel.text = "UID: $requestedUid\n預覽中（${remain} 秒）"
                delay(1000)
                remain--
            }
            statusLabel.text = "UID: $requestedUid\n預覽結束"
            photoPreview.visibility = View.GONE
            isPreviewing = false
            captureButton.isEnabled = allowMultipleShots || allowRetake
            if (allowMultipleShots || allowRetake) {
                resetDetectionState(initial = false)
            } else {
                finish()
            }
        }
    }

    private fun onCaptureFailed(exception: ImageCaptureException) {
        isCapturing = false
        isPreviewing = false
        captureButton.isEnabled = true
        aimOverlay.visibility = View.VISIBLE
        photoPreview.visibility = View.GONE
        findViewById<View>(R.id.greenFrame)?.visibility = View.GONE
        speak("拍照失敗，系統將重新嘗試。", flush = true)
        Toast.makeText(this, "拍攝失敗：${exception.message}", Toast.LENGTH_LONG).show()
        statusLabel.text = "UID: $requestedUid\n拍照失敗，將重新嘗試"
        resetDetectionState(initial = false)
    }

    private fun resolveStoragePath(): String {
        val base = storagePath.ifBlank { "Pictures/PigeonEyeRecords" }
        val normalized = if (base.startsWith("Pictures")) base else "Pictures/$base"
        return if (normalized.contains("%UID%", ignoreCase = true)) {
            normalized.replace("%UID%", requestedUid, ignoreCase = true)
        } else {
            if (normalized.endsWith(requestedUid)) normalized else "$normalized/$requestedUid"
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, fileName: String, relativePath: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveBitmapForQPlus(bitmap, fileName, relativePath)
        } else {
            saveBitmapForLegacy(bitmap, fileName, relativePath)
        }
    }

    private fun saveBitmapForQPlus(bitmap: Bitmap, fileName: String, relativePath: String): Uri? {
        val resolver = contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return null
        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                    throw IOException("JPEG 壓縮失敗")
                }
            }
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    private fun saveBitmapForLegacy(bitmap: Bitmap, fileName: String, relativePath: String): Uri? {
        val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
        val subPath = relativePath.removePrefix("Pictures/")
        val targetDir = File(picturesDir, subPath)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val targetFile = File(targetDir, fileName)
        FileOutputStream(targetFile).use { outputStream ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                throw IOException("JPEG 壓縮失敗")
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DATA, targetFile.absolutePath)
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun addWatermark(source: Bitmap, uid: String, dateTime: String): Bitmap {
        val mutable = if (source.isMutable) source else source.copy(Bitmap.Config.ARGB_8888, true)
        if (mutable != source) {
            source.recycle()
        }
        val canvas = Canvas(mutable)
        val density = resources.displayMetrics.density
        val margin = 16f * density
        val textSize = (mutable.width * 0.035f).coerceAtLeast(32f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
        }
        val shadowPaint = Paint(paint).apply {
            color = Color.argb(200, 0, 0, 0)
        }
        val baseline = mutable.height - margin

        paint.textAlign = Paint.Align.LEFT
        shadowPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(uid, margin + 2, baseline + 2, shadowPaint)
        canvas.drawText(uid, margin, baseline, paint)

        paint.textAlign = Paint.Align.RIGHT
        shadowPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(dateTime, mutable.width - margin + 2, baseline + 2, shadowPaint)
        canvas.drawText(dateTime, mutable.width - margin, baseline, paint)

        return mutable
    }

    private fun evaluateBlur(bitmap: Bitmap): Double {
        val sampleSize = 200
        val width = bitmap.width
        val height = bitmap.height
        val scale = sampleSize.toFloat() / max(width, height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (width * scale).toInt(), (height * scale).toInt(), true)
        } else {
            bitmap
        }
        val gray = IntArray(scaled.width * scaled.height)
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            gray[i] = ((0.299f * r) + (0.587f * g) + (0.114f * b)).toInt()
        }
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        val widthM1 = scaled.width - 1
        val heightM1 = scaled.height - 1
        for (y in 1 until heightM1) {
            for (x in 1 until widthM1) {
                val center = gray[y * scaled.width + x] * 4
                val north = gray[(y - 1) * scaled.width + x]
                val south = gray[(y + 1) * scaled.width + x]
                val east = gray[y * scaled.width + (x + 1)]
                val west = gray[y * scaled.width + (x - 1)]
                val laplacian = center - north - south - east - west
                sum += laplacian
                sumSq += laplacian * laplacian
                count++
            }
        }
        if (scaled !== bitmap) {
            scaled.recycle()
        }
        if (count == 0) return Double.MAX_VALUE
        val mean = sum / count
        return (sumSq / count) - (mean * mean)
    }

    private fun enableTorch(enable: Boolean) {
        activeCamera?.cameraControl?.enableTorch(enable)
    }

    private fun buildTelephotoSelector(
        cameraProvider: ProcessCameraProvider,
        cameraId: String
    ): CameraSelector? {
        val matches = cameraProvider.availableCameraInfos.filter { info ->
            Camera2CameraInfo.from(info).cameraId == cameraId
        }
        if (matches.isEmpty()) {
            return null
        }
        return CameraSelector.Builder().addCameraFilter { cameraInfos ->
            cameraInfos.filter { info ->
                Camera2CameraInfo.from(info).cameraId == cameraId
            }
        }.build()
    }

    private fun selectTelephotoCandidate(): Pair<String, Float>? {
        val cameraManager = getSystemService(CameraManager::class.java)
        var bestCameraId: String? = null
        var bestFocal = 0f
        val cameraIdList = try {
            cameraManager.cameraIdList
        } catch (e: Exception) {
            return null
        }
        for (cameraId in cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (lensFacing != CameraCharacteristics.LENS_FACING_BACK) continue
            val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: continue
            val maxFocal = focalLengths.maxOrNull() ?: continue
            if (maxFocal > bestFocal) {
                bestFocal = maxFocal
                bestCameraId = cameraId
            }
        }
        return bestCameraId?.let { it to bestFocal }
    }

    private fun applyConfigUpdate(config: CaptureConfigSnapshot) {
        requestedZoomRatio = config.zoomRatio.coerceIn(1f, 10f)
        stabilizeDurationMs = (config.stabilizeSeconds * 1000).toLong().coerceIn(200, 5000)
        previewDurationMs = (config.previewSeconds * 1000).toLong().coerceIn(1000, 60000)
        successVoice = config.successVoice.ifBlank { "完成" }
        storagePath = config.storagePath.ifBlank { "Pictures/PigeonEyeRecords" }
        allowMultipleShots = config.allowMultiple
        requestedAspectRatio = config.aspectRatio.ifBlank { "1:1" }
        overlayScale = config.overlayScale.coerceIn(0.4f, 0.95f)
        detectionVariance = config.detectionVariance.coerceIn(300.0, 4000.0)
        allowRetake = config.allowRetake
        voiceEnabled = config.voiceEnabled
        voiceDelayMs = (config.voiceDelaySeconds.coerceAtLeast(0.0) * 1000).toLong()

        aimOverlay.setOverlayScale(overlayScale)
        adjustZoom(null)
        if (!isCapturing && !isPreviewing) {
            aimOverlay.setState(AimOverlayView.AimState.IDLE)
            findViewById<View>(R.id.greenFrame)?.visibility = View.GONE
        }
        val hasTelephoto = selectTelephotoCandidate() != null
        updateStatusPreparing(hasTelephoto)
    }

    private data class CaptureConfigSnapshot(
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
        val voiceEnabled: Boolean,
        val voiceDelaySeconds: Double,
    ) {
        companion object {
            fun fromMap(map: Map<*, *>): CaptureConfigSnapshot? {
                val zoom = (map["pama1"] as? Number)?.toFloat() ?: 3f
                val stabilize = (map["pama2"] as? Number)?.toFloat() ?: 1.2f
                val voice = (map["pama3"] as? String)?.ifBlank { "完成" } ?: "完成"
                val preview = (map["pama4"] as? Number)?.toFloat() ?: 3f
                val path = (map["pama5"] as? String)?.ifBlank { "Pictures/PigeonEyeRecords" } ?: "Pictures/PigeonEyeRecords"
                val allowMultiple = when ((map["pama6"] as? String)?.lowercase()) {
                    "n", "no", "false" -> false
                    else -> true
                }
                val ratio = (map["pama7"] as? String)?.ifBlank { "1:1" } ?: "1:1"
                val overlay = (map["pama8"] as? Number)?.toFloat() ?: 0.78f
                val variance = (map["pama9"] as? Number)?.toDouble() ?: 1500.0
                val allowRetake = (map["pama10"] as? String)?.lowercase() == "y"
                val voiceEnabled = (map["pama11"] as? String)?.lowercase() != "n"
                val voiceDelay = (map["pama12"] as? Number)?.toDouble() ?: 0.0
                return CaptureConfigSnapshot(
                    zoomRatio = zoom,
                    stabilizeSeconds = stabilize,
                    successVoice = voice,
                    previewSeconds = preview,
                    storagePath = path,
                    allowMultiple = allowMultiple,
                    aspectRatio = ratio,
                    overlayScale = overlay,
                    detectionVariance = variance,
                    allowRetake = allowRetake,
                    voiceEnabled = voiceEnabled,
                    voiceDelaySeconds = voiceDelay.coerceIn(0.0, 5.0),
                )
            }
                val ratio = (map["pama7"] as? String)?.ifBlank { "1:1" } ?: "1:1"
                val overlay = (map["pama8"] as? Number)?.toFloat() ?: 0.78f
                val variance = (map["pama9"] as? Number)?.toDouble() ?: 1500.0
                val allowRetake = (map["pama10"] as? String)?.lowercase() == "y"
                val voiceEnabled = (map["pama11"] as? String)?.lowercase() != "n"
                val voiceDelay = (map["pama12"] as? Number)?.toDouble() ?: 0.0
                val voiceEnabled = (map["pama11"] as? String)?.lowercase() != "n"
                val voiceDelay = (map["pama12"] as? Number)?.toDouble() ?: 0.0
                return CaptureConfigSnapshot(
                    zoomRatio = zoom,
                    stabilizeSeconds = stabilize,
                    successVoice = voice,
                    previewSeconds = preview,
                    storagePath = path,
                    allowMultiple = allowMultiple,
                    aspectRatio = ratio,
                    overlayScale = overlay,
                    detectionVariance = variance,
                    voiceEnabled = voiceEnabled,
                    voiceDelaySeconds = voiceDelay.coerceIn(0.0, 5.0),
                    allowRetake = allowRetake,
                    voiceEnabled = voiceEnabled,
                    voiceDelaySeconds = voiceDelay.coerceIn(0.0, 5.0),
                )
            }
        }
    }

    private fun updateStatusPreparing(hasTelephoto: Boolean) {
        val zoomDesc = String.format(Locale.TAIWAN, "%.1fx", requestedZoomRatio)
        statusLabel.text = "UID: $requestedUid\n倍率已設定為 $zoomDesc"
    }

private fun ImageProxy.toBitmap(): Bitmap? {
        val buffer: ByteBuffer = planes.firstOrNull()?.buffer ?: return null
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        buffer.rewind()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun Bitmap.rotate(rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return this
        val matrix = android.graphics.Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
}





