package com.gireesh.camviewsplit.util

import android.graphics.Matrix
import com.gireesh.camviewsplit.model.ContentScaleMode
import com.gireesh.camviewsplit.model.VideoSectionState

data class SectionScaleResult(
    val scaleX: Float,
    val scaleY: Float,
    val normPanX: Float,
    val normPanY: Float
)

object CompositionTransformEngine {

    /**
     * Calculates the aspect-ratio scale factors and normalized pan offsets for a video section.
     */
    fun calculateScaleAndPan(
        sectionState: VideoSectionState,
        sourceWidth: Float = 1080f,
        sourceHeight: Float = 1920f,
        viewportWidth: Float = 1080f,
        viewportHeight: Float = 1920f
    ): SectionScaleResult {
        val srcAR = (sourceWidth / sourceHeight.coerceAtLeast(1f)).coerceIn(0.1f, 10.0f)
        val secAR = (viewportWidth / viewportHeight.coerceAtLeast(1f)).coerceIn(0.1f, 10.0f)

        val aspectScaleX: Float
        val aspectScaleY: Float

        if (sectionState.contentScaleMode == ContentScaleMode.FILL) {
            aspectScaleX = maxOf(1.0f, srcAR / secAR)
            aspectScaleY = maxOf(1.0f, secAR / srcAR)
        } else { // FIT
            aspectScaleX = minOf(1.0f, srcAR / secAR)
            aspectScaleY = minOf(1.0f, secAR / srcAR)
        }

        val zoom = sectionState.zoomScale.coerceIn(0.5f, 5.0f)
        val finalScaleX = aspectScaleX * zoom
        val finalScaleY = aspectScaleY * zoom

        val normPanX = (sectionState.panOffset.x / viewportWidth.coerceAtLeast(1f)).coerceIn(-1.0f, 1.0f)
        val normPanY = (sectionState.panOffset.y / viewportHeight.coerceAtLeast(1f)).coerceIn(-1.0f, 1.0f)

        return SectionScaleResult(
            scaleX = finalScaleX,
            scaleY = finalScaleY,
            normPanX = normPanX,
            normPanY = normPanY
        )
    }

    /**
     * Calculates the exact Matrix transformation for Media3 Transformer / OpenGL NDC space.
     */
    fun calculateExportMatrix(
        sectionState: VideoSectionState,
        sectionRatio: Float,
        isTopSection: Boolean,
        sourceWidth: Float = 1080f,
        sourceHeight: Float = 1920f,
        outputWidth: Float = 1080f,
        outputHeight: Float = 1920f
    ): Matrix {
        val matrix = Matrix()
        val sectionHeightPx = outputHeight * sectionRatio.coerceIn(0.15f, 0.85f)

        val params = calculateScaleAndPan(
            sectionState = sectionState,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            viewportWidth = outputWidth,
            viewportHeight = sectionHeightPx
        )

        matrix.postScale(params.scaleX, params.scaleY * sectionRatio)

        val translateNDC_X = params.normPanX * 2.0f
        val translateNDC_Y = if (isTopSection) {
            (1.0f - sectionRatio) - (params.normPanY * 2.0f)
        } else {
            -sectionRatio - (params.normPanY * 2.0f)
        }

        matrix.postTranslate(translateNDC_X, translateNDC_Y)
        return matrix
    }
}
