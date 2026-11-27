package com.gireesh.camviewsplit.model

import android.net.Uri

data class RecordingSession(
    val topSource: VideoSource,
    val bottomSource: VideoSource,
    val primaryCameraUri: Uri,
    val secondaryCameraUri: Uri? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isValidForConfig(config: DualVideoCompositionConfig): Boolean {
        val currentIsTopCam = config.topState.source is VideoSource.LiveCameraFront || config.topState.source is VideoSource.LiveCameraRear
        val currentIsBottomCam = config.bottomState.source is VideoSource.LiveCameraFront || config.bottomState.source is VideoSource.LiveCameraRear

        val sessionWasTopCam = topSource is VideoSource.LiveCameraFront || topSource is VideoSource.LiveCameraRear
        val sessionWasBottomCam = bottomSource is VideoSource.LiveCameraFront || bottomSource is VideoSource.LiveCameraRear

        if (currentIsTopCam != sessionWasTopCam) return false
        if (currentIsBottomCam != sessionWasBottomCam) return false

        if (currentIsTopCam && topSource != config.topState.source) return false
        if (currentIsBottomCam && bottomSource != config.bottomState.source) return false

        return true
    }
}
