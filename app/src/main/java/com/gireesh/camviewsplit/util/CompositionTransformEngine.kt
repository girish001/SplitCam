package com.gireesh.camviewsplit.util

import android.graphics.Matrix
import com.gireesh.camviewsplit.model.ContentScaleMode
import com.gireesh.camviewsplit.model.VideoSectionState

object CompositionTransformEngine {

    /**
     * Calculates the exact matrix transformation for a video section in NDC space.
     */
    fun calculateSectionMatrix(
        sectionState: VideoSectionState,
        sectionRatio: Float,
        isTopSection: Boolean,
        viewportWidthPx: Float = 1080f,
        viewportHeightPx: Float = 1920f
    ): Matrix {
        val matrix = Matrix()

        val fitScaleFactor = if (sectionState.contentScaleMode == ContentScaleMode.FIT) 0.88f else 1.0f
        val zoomScale = (sectionState.zoomScale * fitScaleFactor).coerceAtLeast(0.1f)

        // Normalize gesture pixel pan to NDC space [-1.0, 1.0]
        val normPanX = (sectionState.panOffset.x / viewportWidthPx) * 2.0f
        val normPanY = (-sectionState.panOffset.y / viewportHeightPx) * 2.0f

        val clampedPanX = normPanX.coerceIn(-1.5f, 1.5f)
        val clampedPanY = normPanY.coerceIn(-1.5f, 1.5f)

        matrix.postScale(zoomScale, zoomScale * sectionRatio)

        val translateY = if (isTopSection) {
            (1.0f - sectionRatio) + clampedPanY
        } else {
            -sectionRatio + clampedPanY
        }

        matrix.postTranslate(clampedPanX, translateY)
        return matrix
    }
}
