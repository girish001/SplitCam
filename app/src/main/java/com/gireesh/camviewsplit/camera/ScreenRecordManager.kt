package com.gireesh.camviewsplit.camera

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.util.Log
import com.gireesh.camviewsplit.model.RecordMode
import com.gireesh.camviewsplit.service.ScreenRecordingService
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

class ScreenRecordManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val TAG = "ScreenRecordManager"

    private var recordingService: ScreenRecordingService? = null
    private var isBound = false
    private var timerJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0L)
    val recordingDurationSeconds: StateFlow<Long> = _recordingDurationSeconds.asStateFlow()

    private val _recordMode = MutableStateFlow(RecordMode.SPLIT_SCREEN)
    val recordMode: StateFlow<RecordMode> = _recordMode.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as ScreenRecordingService.LocalBinder
            recordingService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            recordingService = null
            isBound = false
        }
    }

    fun setRecordMode(mode: RecordMode) {
        if (!_isRecording.value) {
            _recordMode.value = mode
        }
    }

    fun bindService() {
        if (!isBound) {
            val intent = Intent(context, ScreenRecordingService::class.java)
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindService() {
        if (isBound) {
            try {
                context.unbindService(connection)
            } catch (e: Exception) {
                Log.w(TAG, "Exception unbinding service", e)
            }
            isBound = false
        }
    }

    fun startScreenRecording(
        resultCode: Int,
        resultData: Intent,
        onStart: () -> Unit,
        onError: (String) -> Unit
    ) {
        val service = recordingService ?: run {
            onError("Recording service is not ready. Please try again.")
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val displayName = "SplitCam_SPLIT_$timestamp.mp4"
        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        val outputFile = File(moviesDir, displayName)

        service.startRecording(
            resultCode = resultCode,
            resultData = resultData,
            outputFile = outputFile,
            onStart = {
                _isRecording.value = true
                _recordingDurationSeconds.value = 0L
                startTimer()
                onStart()
            },
            onError = { err ->
                stopTimer()
                _isRecording.value = false
                onError(err)
            }
        )
    }

    fun stopScreenRecording(
        onSuccess: (Uri, String) -> Unit,
        onError: (String) -> Unit
    ) {
        val service = recordingService ?: run {
            onError("Recording service is not connected")
            return
        }

        service.stopRecording(
            onSuccess = { file ->
                stopTimer()
                _isRecording.value = false
                val displayName = file.name

                // Register file in MediaStore so it appears in device galleries
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf("video/mp4")
                ) { _, scanUri ->
                    val finalUri = scanUri ?: Uri.fromFile(file)
                    scope.launch(Dispatchers.Main) {
                        onSuccess(finalUri, displayName)
                    }
                }
            },
            onError = { err ->
                stopTimer()
                _isRecording.value = false
                onError(err)
            }
        )
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
        stopTimer()
        unbindService()
    }
}
