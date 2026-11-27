package com.gireesh.camviewsplit.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.ConcurrentCamera.SingleCameraConfig
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.gireesh.camviewsplit.model.VideoSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class CameraCaptureManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val TAG = "CameraCaptureManager"

    private var cameraProvider: ProcessCameraProvider? = null
    private var primaryCamera: Camera? = null
    private var secondaryCamera: Camera? = null

    private var primaryPreview: Preview? = null
    private var secondaryPreview: Preview? = null
    private var imageCapture: ImageCapture? = null

    private var primaryVideoCapture: VideoCapture<Recorder>? = null
    private var secondaryVideoCapture: VideoCapture<Recorder>? = null

    private var primaryRecording: Recording? = null
    private var secondaryRecording: Recording? = null
    private var timerJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0L)
    val recordingDurationSeconds: StateFlow<Long> = _recordingDurationSeconds.asStateFlow()

    private val _isFlashOn = MutableStateFlow(false)
    val isFlashOn: StateFlow<Boolean> = _isFlashOn.asStateFlow()

    private val _isConcurrentCameraSupported = MutableStateFlow(false)
    val isConcurrentCameraSupported: StateFlow<Boolean> = _isConcurrentCameraSupported.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    fun initializeCameraProvider(onReady: () -> Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context.applicationContext)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                checkConcurrentCameraSupport()
                onReady()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize CameraProvider", e)
                _errorMessage.value = "Failed to start camera: ${e.localizedMessage}"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun isCameraPairSupported(topLens: Int, bottomLens: Int): Boolean {
        val provider = cameraProvider ?: return false
        return try {
            val concurrentInfoList: List<List<CameraInfo>> = provider.availableConcurrentCameraInfos
            for (cameraInfos in concurrentInfoList) {
                if (cameraInfos.size < 2) continue
                val facings = cameraInfos.map { it.lensFacing }
                if (topLens == bottomLens) {
                    if (facings.count { it == topLens } >= 2) return true
                } else {
                    if (facings.contains(topLens) && facings.contains(bottomLens)) return true
                }
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query availableConcurrentCameraInfos", e)
            false
        }
    }

    private fun checkConcurrentCameraSupport() {
        val provider = cameraProvider ?: return
        try {
            val concurrentInfoList: List<List<CameraInfo>> = provider.availableConcurrentCameraInfos
            var foundAnyPair = false

            for (cameraInfos in concurrentInfoList) {
                if (cameraInfos.size >= 2) {
                    foundAnyPair = true
                    break
                }
            }

            _isConcurrentCameraSupported.value = foundAnyPair
            Log.d(TAG, "Concurrent camera supported: $foundAnyPair")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check availableConcurrentCameraInfos", e)
            _isConcurrentCameraSupported.value = false
        }
    }

    /**
     * Central Coordinator for top and bottom viewport camera bindings.
     */
    fun syncCameraSources(
        lifecycleOwner: LifecycleOwner,
        topSource: VideoSource,
        bottomSource: VideoSource,
        topPreviewView: PreviewView?,
        bottomPreviewView: PreviewView?
    ) {
        if (cameraProvider == null) {
            initializeCameraProvider {
                syncCameraSourcesInternal(lifecycleOwner, topSource, bottomSource, topPreviewView, bottomPreviewView)
            }
        } else {
            syncCameraSourcesInternal(lifecycleOwner, topSource, bottomSource, topPreviewView, bottomPreviewView)
        }
    }

    private fun syncCameraSourcesInternal(
        lifecycleOwner: LifecycleOwner,
        topSource: VideoSource,
        bottomSource: VideoSource,
        topPreviewView: PreviewView?,
        bottomPreviewView: PreviewView?
    ) {
        val provider = cameraProvider ?: return

        val isTopCamera = topSource is VideoSource.LiveCameraFront || topSource is VideoSource.LiveCameraRear
        val isBottomCamera = bottomSource is VideoSource.LiveCameraFront || bottomSource is VideoSource.LiveCameraRear

        try {
            provider.unbindAll()
            primaryCamera = null
            secondaryCamera = null
            primaryVideoCapture = null
            secondaryVideoCapture = null

            if (isTopCamera && isBottomCamera) {
                val topLens = if (topSource is VideoSource.LiveCameraFront) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                val bottomLens = if (bottomSource is VideoSource.LiveCameraFront) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK

                if (!isCameraPairSupported(topLens, bottomLens)) {
                    _errorMessage.value = "The selected dual camera pair is not supported concurrently on this device hardware. Please select a Gallery Video for one section."
                    return
                }

                if (topPreviewView == null || bottomPreviewView == null) return

                val qualitySelector = QualitySelector.from(Quality.SD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))

                // Top Camera Config
                val topSelector = CameraSelector.Builder().requireLensFacing(topLens).build()
                primaryPreview = Preview.Builder().build().also { it.setSurfaceProvider(topPreviewView.surfaceProvider) }
                val topRecorder = Recorder.Builder().setQualitySelector(qualitySelector).build()
                primaryVideoCapture = VideoCapture.withOutput(topRecorder)
                val topGroup = UseCaseGroup.Builder().addUseCase(primaryPreview!!).addUseCase(primaryVideoCapture!!).build()
                val topConfig = SingleCameraConfig(topSelector, topGroup, lifecycleOwner)

                // Bottom Camera Config
                val bottomSelector = CameraSelector.Builder().requireLensFacing(bottomLens).build()
                secondaryPreview = Preview.Builder().build().also { it.setSurfaceProvider(bottomPreviewView.surfaceProvider) }
                val bottomRecorder = Recorder.Builder().setQualitySelector(qualitySelector).build()
                secondaryVideoCapture = VideoCapture.withOutput(bottomRecorder)
                val bottomGroup = UseCaseGroup.Builder().addUseCase(secondaryPreview!!).addUseCase(secondaryVideoCapture!!).build()
                val bottomConfig = SingleCameraConfig(bottomSelector, bottomGroup, lifecycleOwner)

                val concurrentCamera: ConcurrentCamera = provider.bindToLifecycle(listOf(topConfig, bottomConfig))
                primaryCamera = concurrentCamera.cameras.getOrNull(0)
                secondaryCamera = concurrentCamera.cameras.getOrNull(1)

                Log.d(TAG, "Bound dual concurrent cameras successfully: Top lens $topLens, Bottom lens $bottomLens")

            } else if (isTopCamera && topPreviewView != null) {
                val topLens = if (topSource is VideoSource.LiveCameraFront) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                val cameraSelector = CameraSelector.Builder().requireLensFacing(topLens).build()

                primaryPreview = Preview.Builder().build().also { it.setSurfaceProvider(topPreviewView.surfaceProvider) }
                imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()

                val qualitySelector = QualitySelector.from(Quality.HIGHEST, FallbackStrategy.higherQualityOrLowerThan(Quality.SD))
                val recorder = Recorder.Builder().setQualitySelector(qualitySelector).build()
                primaryVideoCapture = VideoCapture.withOutput(recorder)

                primaryCamera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, primaryPreview, imageCapture, primaryVideoCapture)

                if (topLens == CameraSelector.LENS_FACING_BACK) {
                    primaryCamera?.cameraControl?.enableTorch(_isFlashOn.value)
                }

            } else if (isBottomCamera && bottomPreviewView != null) {
                val bottomLens = if (bottomSource is VideoSource.LiveCameraFront) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                val cameraSelector = CameraSelector.Builder().requireLensFacing(bottomLens).build()

                primaryPreview = Preview.Builder().build().also { it.setSurfaceProvider(bottomPreviewView.surfaceProvider) }
                imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()

                val qualitySelector = QualitySelector.from(Quality.HIGHEST, FallbackStrategy.higherQualityOrLowerThan(Quality.SD))
                val recorder = Recorder.Builder().setQualitySelector(qualitySelector).build()
                primaryVideoCapture = VideoCapture.withOutput(recorder)

                primaryCamera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, primaryPreview, imageCapture, primaryVideoCapture)

                if (bottomLens == CameraSelector.LENS_FACING_BACK) {
                    primaryCamera?.cameraControl?.enableTorch(_isFlashOn.value)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Camera sync binding failed", e)
            _errorMessage.value = "Camera binding failed: ${e.localizedMessage}"
        }
    }

    fun startSingleCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        targetLensFacing: Int = CameraSelector.LENS_FACING_BACK
    ) {
        val source = if (targetLensFacing == CameraSelector.LENS_FACING_FRONT) VideoSource.LiveCameraFront else VideoSource.LiveCameraRear
        syncCameraSources(lifecycleOwner, source, VideoSource.None, previewView, null)
    }

    fun toggleCameraFacing(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        if (_isRecording.value) {
            _errorMessage.value = "Cannot switch camera while recording"
            return
        }
        _isFlashOn.value = false
        startSingleCamera(lifecycleOwner, previewView, CameraSelector.LENS_FACING_FRONT)
    }

    fun toggleFlash() {
        val newState = !_isFlashOn.value
        _isFlashOn.value = newState
        primaryCamera?.cameraControl?.enableTorch(newState)
    }

    fun capturePhoto(
        onSuccess: (Uri, String) -> Unit,
        onError: (String) -> Unit
    ) {
        val capture = imageCapture ?: run {
            onError("Camera is not ready")
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val displayName = "SplitCam_IMG_$timestamp.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SplitCam/")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = outputFileResults.savedUri
                    if (savedUri != null) {
                        onSuccess(savedUri, displayName)
                    } else {
                        onError("Saved photo URI was null")
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    onError("Photo capture failed: ${exception.message}")
                }
            }
        )
    }

    fun startVideoRecording(
        onSuccess: (primaryUri: Uri, secondaryUri: Uri?) -> Unit,
        onError: (String) -> Unit
    ) {
        val primaryCapture = primaryVideoCapture ?: run {
            onError("Camera video recorder is not ready")
            return
        }

        if (primaryRecording != null || _isRecording.value) {
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val primaryFile = File(context.cacheDir, "SplitCam_PRI_$timestamp.mp4")
        val secondaryFile = File(context.cacheDir, "SplitCam_SEC_$timestamp.mp4")

        var primaryResultUri: Uri? = null
        var secondaryResultUri: Uri? = null
        var primaryFinished = false
        var secondaryFinished = secondaryVideoCapture == null

        fun checkBothFinished() {
            if (primaryFinished && secondaryFinished) {
                stopTimer()
                _isRecording.value = false
                primaryRecording?.close()
                secondaryRecording?.close()
                primaryRecording = null
                secondaryRecording = null

                if (primaryResultUri != null) {
                    onSuccess(primaryResultUri!!, secondaryResultUri)
                } else {
                    onError("Failed to record camera video feed.")
                }
            }
        }

        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)

        var primaryPrep = primaryCapture.output.prepareRecording(context, FileOutputOptions.Builder(primaryFile).build())
        if (hasAudio) {
            try { primaryPrep = primaryPrep.withAudioEnabled() } catch (_: Exception) {}
        }

        try {
            primaryRecording = primaryPrep.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        _isRecording.value = true
                        _recordingDurationSeconds.value = 0L
                        startTimer()
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!event.hasError() && primaryFile.exists() && primaryFile.length() > 0) {
                            primaryResultUri = Uri.fromFile(primaryFile)
                        } else if (primaryFile.exists() && primaryFile.length() > 0 && event.error != VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA) {
                            primaryResultUri = Uri.fromFile(primaryFile)
                        }
                        primaryFinished = true
                        checkBothFinished()
                    }
                }
            }

            val secondaryCapture = secondaryVideoCapture
            if (secondaryCapture != null) {
                val secondaryPrep = secondaryCapture.output.prepareRecording(context, FileOutputOptions.Builder(secondaryFile).build())
                secondaryRecording = secondaryPrep.start(ContextCompat.getMainExecutor(context)) { event ->
                    when (event) {
                        is VideoRecordEvent.Finalize -> {
                            if (!event.hasError() && secondaryFile.exists() && secondaryFile.length() > 0) {
                                secondaryResultUri = Uri.fromFile(secondaryFile)
                            }
                            secondaryFinished = true
                            checkBothFinished()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start camera recording", e)
            _isRecording.value = false
            primaryRecording = null
            secondaryRecording = null
            onError("Failed to start recording: ${e.localizedMessage}")
        }
    }

    fun stopVideoRecording() {
        try {
            primaryRecording?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Exception stopping primary recording", e)
        }
        try {
            secondaryRecording?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Exception stopping secondary recording", e)
        }
    }

    private fun startTimer() {
        stopTimer()
        timerJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(1000)
                _recordingDurationSeconds.value += 1
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    fun release() {
        stopVideoRecording()
        cameraProvider?.unbindAll()
    }
}
