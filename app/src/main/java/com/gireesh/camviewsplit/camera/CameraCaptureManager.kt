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
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class CameraCaptureManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val TAG = "CameraCaptureManager"

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var timerJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0L)
    val recordingDurationSeconds: StateFlow<Long> = _recordingDurationSeconds.asStateFlow()

    private val _lensFacing = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val lensFacing: StateFlow<Int> = _lensFacing.asStateFlow()

    private val _isFlashOn = MutableStateFlow(false)
    val isFlashOn: StateFlow<Boolean> = _isFlashOn.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(lifecycleOwner, previewView)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize CameraProvider", e)
                _errorMessage.value = "Failed to start camera: ${e.localizedMessage}"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        val provider = cameraProvider ?: return

        try {
            provider.unbindAll()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(_lensFacing.value)
                .build()

            // Preview use case
            preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // ImageCapture use case
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            // VideoCapture use case
            val qualitySelector = QualitySelector.from(
                Quality.HIGHEST,
                FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
            )
            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                videoCapture
            )

            // Re-apply torch state if back camera
            if (_lensFacing.value == CameraSelector.LENS_FACING_BACK) {
                camera?.cameraControl?.enableTorch(_isFlashOn.value)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
            _errorMessage.value = "Camera binding failed: ${e.localizedMessage}"
        }
    }

    fun toggleCameraFacing(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        if (_isRecording.value) {
            _errorMessage.value = "Cannot switch camera while recording"
            return
        }
        _lensFacing.value = if (_lensFacing.value == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        _isFlashOn.value = false
        bindCameraUseCases(lifecycleOwner, previewView)
    }

    fun toggleFlash() {
        if (_lensFacing.value != CameraSelector.LENS_FACING_BACK) {
            _errorMessage.value = "Flash is only available for rear camera"
            return
        }
        val newState = !_isFlashOn.value
        _isFlashOn.value = newState
        camera?.cameraControl?.enableTorch(newState)
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
        val displayName = "CamViewSplit_IMG_$timestamp.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CamViewSplit/")
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
        onSuccess: (Uri, String) -> Unit,
        onError: (String) -> Unit
    ) {
        val capture = videoCapture ?: run {
            onError("Video recorder is not ready")
            return
        }

        if (activeRecording != null || _isRecording.value) {
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val displayName = "CamViewSplit_VID_$timestamp.mp4"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CamViewSplit/")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        var pendingRecording = capture.output.prepareRecording(context, mediaStoreOutputOptions)

        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)

        if (hasAudioPermission) {
            try {
                pendingRecording = pendingRecording.withAudioEnabled()
            } catch (e: Exception) {
                Log.w(TAG, "Could not enable audio recording", e)
            }
        }

        try {
            activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        _isRecording.value = true
                        _recordingDurationSeconds.value = 0L
                        startTimer()
                    }
                    is VideoRecordEvent.Finalize -> {
                        stopTimer()
                        _isRecording.value = false
                        activeRecording?.close()
                        activeRecording = null

                        val outputUri = event.outputResults.outputUri
                        val hasValidUri = outputUri != Uri.EMPTY

                        if (!event.hasError()) {
                            onSuccess(outputUri, displayName)
                        } else if (hasValidUri && event.error != VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA) {
                            onSuccess(outputUri, displayName)
                        } else {
                            val friendlyMsg = when (event.error) {
                                VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA ->
                                    "Recording was too short. Please record for at least 2-3 seconds."
                                VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE ->
                                    "Insufficient storage to save video."
                                VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED ->
                                    "Video file size limit reached."
                                VideoRecordEvent.Finalize.ERROR_INVALID_OUTPUT_OPTIONS ->
                                    "Storage error: Could not access video destination."
                                VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR ->
                                    "Recorder error: Audio or video encoder failure."
                                VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE ->
                                    "Camera source stopped."
                                else ->
                                    event.cause?.message ?: "Recording stopped with code ${event.error}"
                            }
                            Log.e(TAG, "Video recording finalize error ${event.error}: $friendlyMsg", event.cause)
                            onError(friendlyMsg)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            _isRecording.value = false
            activeRecording = null
            onError("Failed to start recording: ${e.localizedMessage}")
        }
    }

    fun stopVideoRecording() {
        val recording = activeRecording ?: return
        try {
            recording.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Exception while stopping recording", e)
        }
        // Keep activeRecording until VideoRecordEvent.Finalize arrives to avoid premature garbage collection
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
