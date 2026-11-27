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
        recordingSession: RecordingSession?
    ): CompositionURIPair {
        val isTopCamera = config.topState.source is VideoSource.LiveCameraFront || config.topState.source is VideoSource.LiveCameraRear
        val isBottomCamera = config.bottomState.source is VideoSource.LiveCameraFront || config.bottomState.source is VideoSource.LiveCameraRear

        val isSessionValid = recordingSession != null && recordingSession.isValidForConfig(config)

        val topUri: Uri? = if (isTopCamera) {
            if (isSessionValid) recordingSession?.primaryCameraUri else null
        } else {
            (config.topState.source as? VideoSource.GalleryVideo)?.uri
        }

        val bottomUri: Uri? = if (isBottomCamera) {
            if (isSessionValid) {
                if (isTopCamera) recordingSession?.secondaryCameraUri else recordingSession?.primaryCameraUri
            } else null
        } else {
            (config.bottomState.source as? VideoSource.GalleryVideo)?.uri
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
