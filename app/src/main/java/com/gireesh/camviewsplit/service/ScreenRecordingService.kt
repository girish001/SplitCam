package com.gireesh.camviewsplit.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.gireesh.camviewsplit.R
import java.io.File

class ScreenRecordingService : Service() {

    private val TAG = "ScreenRecordingService"
    private val NOTIFICATION_ID = 4001
    private val CHANNEL_ID = "splitcam_recording_channel"

    private val binder = LocalBinder()
    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var currentOutputFile: File? = null

    var isRecording = false
        private set

    inner class LocalBinder : Binder() {
        fun getService(): ScreenRecordingService = this@ScreenRecordingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    fun startRecording(
        resultCode: Int,
        resultData: Intent,
        outputFile: File,
        onStart: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (isRecording) {
            onError("Screen recording is already active")
            return
        }

        try {
            // 1. Start foreground service first (Strict requirement on Android 14+)
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            // 2. Obtain MediaProjection
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

            if (mediaProjection == null) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                onError("Failed to obtain screen projection")
                return
            }

            // 3. Register required callback (Mandatory on Android 14+)
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection onStop triggered")
                    if (isRecording) {
                        stopRecordingInternal()
                    }
                }
            }, Handler(Looper.getMainLooper()))

            // 4. Calculate screen dimensions
            val metrics = getScreenBounds()
            var width = metrics.width()
            var height = metrics.height()

            // Dimensions must be even for H.264
            if (width % 2 != 0) width -= 1
            if (height % 2 != 0) height -= 1
            val densityDpi = resources.displayMetrics.densityDpi

            currentOutputFile = outputFile

            // 5. Setup MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

                setVideoSize(width, height)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(6_000_000) // 6 Mbps for sharp split preview
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44100)

                setOutputFile(outputFile.absolutePath)
                prepare()
            }

            // 6. Create VirtualDisplay
            val surface = mediaRecorder?.surface
            if (surface == null) {
                stopRecordingInternal()
                onError("Could not allocate recorder surface")
                return
            }

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "SplitCamDisplay",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null
            )

            mediaRecorder?.start()
            isRecording = true
            onStart()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start screen recording", e)
            stopRecordingInternal()
            onError("Recording initialization failed: ${e.localizedMessage}")
        }
    }

    fun stopRecording(
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        val file = currentOutputFile
        if (!isRecording || file == null) {
            onError("No active recording found")
            return
        }

        try {
            stopRecordingInternal()
            if (file.exists() && file.length() > 0) {
                onSuccess(file)
            } else {
                onError("Recorded video file was empty")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finalizing screen recording", e)
            onError("Error finalizing recording: ${e.localizedMessage}")
        }
    }

    private fun stopRecordingInternal() {
        isRecording = false
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Exception while stopping MediaRecorder", e)
        }
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Exception releasing MediaRecorder", e)
        }
        mediaRecorder = null

        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Exception releasing VirtualDisplay", e)
        }
        virtualDisplay = null

        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Exception stopping MediaProjection", e)
        }
        mediaProjection = null

        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun getScreenBounds(): Rect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = getSystemService(WindowManager::class.java)
            wm.currentWindowMetrics.bounds
        } else {
            @Suppress("DEPRECATION")
            val display = (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
            val size = android.graphics.Point()
            @Suppress("DEPRECATION")
            display.getRealSize(size)
            Rect(0, 0, size.x, size.y)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SplitCam Recording")
            .setContentText("Recording camera face, split screen, and audio...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SplitCam Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing notification while recording split screen video"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopRecordingInternal()
        super.onDestroy()
    }
}
