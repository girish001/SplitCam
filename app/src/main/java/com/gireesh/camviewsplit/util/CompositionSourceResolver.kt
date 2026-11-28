package com.gireesh.camviewsplit.util

import android.net.Uri
import com.gireesh.camviewsplit.model.DualVideoCompositionConfig
import com.gireesh.camviewsplit.model.RecordingSession
import com.gireesh.camviewsplit.model.VideoSource

data class CompositionURIPair(
    val topUri: Uri?,
    val bottomUri: Uri?,
    val isValid: Boolean,
    val errorMessage: String? = null
)

object CompositionSourceResolver {

    fun resolve(
        config: DualVideoCompositionConfig,
        recordingSession: RecordingSession?,
        isSocialViewerMode: Boolean = false
    ): CompositionURIPair {
        val effectiveConfig = if (isSocialViewerMode) {
            config.copy(bottomState = config.bottomState.copy(source = VideoSource.None))
        } else {
            config
        }

        val isTopCamera = effectiveConfig.topState.source is VideoSource.LiveCameraFront || effectiveConfig.topState.source is VideoSource.LiveCameraRear
        val isBottomCamera = effectiveConfig.bottomState.source is VideoSource.LiveCameraFront || effectiveConfig.bottomState.source is VideoSource.LiveCameraRear

        val isSessionValid = recordingSession != null && recordingSession.isValidForConfig(effectiveConfig)

        val topUri: Uri? = if (isTopCamera) {
            if (isSessionValid) recordingSession?.primaryCameraUri else null
        } else {
            (effectiveConfig.topState.source as? VideoSource.GalleryVideo)?.uri
        }

        val bottomUri: Uri? = if (isBottomCamera) {
            if (isSessionValid) {
                if (isTopCamera) recordingSession?.secondaryCameraUri else recordingSession?.primaryCameraUri
            } else null
        } else {
            (effectiveConfig.bottomState.source as? VideoSource.GalleryVideo)?.uri
        }

        if (isTopCamera && topUri == null) {
            return CompositionURIPair(
                topUri = null,
                bottomUri = bottomUri,
                isValid = false,
                errorMessage = "Live camera configured for Top section, but no active recording exists for this camera configuration. Please record first."
            )
        }

        if (isBottomCamera && bottomUri == null) {
            return CompositionURIPair(
                topUri = topUri,
                bottomUri = null,
                isValid = false,
                errorMessage = "Live camera configured for Bottom section, but no active recording exists for this camera configuration. Please record first."
            )
        }

        if (topUri == null && bottomUri == null) {
            return CompositionURIPair(
                topUri = null,
                bottomUri = null,
                isValid = false,
                errorMessage = "No video source configured for Top or Bottom section."
            )
        }

        return CompositionURIPair(
            topUri = topUri,
            bottomUri = bottomUri,
            isValid = true,
            errorMessage = null
        )
    }
}
