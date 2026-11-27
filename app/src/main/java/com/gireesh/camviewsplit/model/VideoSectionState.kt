package com.gireesh.camviewsplit.model

import android.net.Uri
import androidx.compose.ui.geometry.Offset

sealed interface VideoSource {
    data object LiveCameraFront : VideoSource
    data object LiveCameraRear : VideoSource
    data class GalleryVideo(val uri: Uri, val displayName: String = "") : VideoSource
    data object None : VideoSource
}

enum class ContentScaleMode {
    FILL,
    FIT
}

data class VideoSectionState(
    val source: VideoSource = VideoSource.None,
    val contentScaleMode: ContentScaleMode = ContentScaleMode.FILL,
    val panOffset: Offset = Offset.Zero,
    val zoomScale: Float = 1.0f,
    val isMuted: Boolean = false
) {
    fun resetAdjustments(): VideoSectionState {
        return copy(
            contentScaleMode = ContentScaleMode.FILL,
            panOffset = Offset.Zero,
            zoomScale = 1.0f
        )
    }
}
