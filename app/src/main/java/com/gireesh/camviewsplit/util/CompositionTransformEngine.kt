package com.gireesh.camviewsplit.util

import android.content.Context
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.gireesh.camviewsplit.model.ContentScaleMode
import com.gireesh.camviewsplit.model.VideoSectionState

data class SourceVideoDimensions(
    val rawWidth: Int = 1080,
    val rawHeight: Int = 1920,
    val rotationDegrees: Int = 0
) {
    val effectiveWidth: Float
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) rawHeight.toFloat() else rawWidth.toFloat()

    val effectiveHeight: Float
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) rawWidth.toFloat() else rawHeight.toFloat()

    val aspectRatio: Float
        get() = (effectiveWidth / effectiveHeight.coerceAtLeast(1f)).coerceIn(0.1f, 10.0f)
}

data class SectionTransformResult(
    val scaleX: Float,
    val scaleY: Float,
    val normPanX: Float,
    val normPanY: Float,
    val composePanX: Float,
    val composePanY: Float
)

object CompositionTransformEngine {

    fun retrieveMediaDimensions(context: Context, uri: Uri?): SourceVideoDimensions {
        if (uri == null) return SourceVideoDimensions(1080, 1920, 0)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

            val w = wStr?.toIntOrNull() ?: 1080
            val h = hStr?.toIntOrNull() ?: 1920
            val r = rStr?.toIntOrNull() ?: 0

            SourceVideoDimensions(w, h, r)
        } catch (_: Exception) {
            SourceVideoDimensions(1080, 1920, 0)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /**
     * Calculates the exact, unified scale and pan parameters for a section.
     */
    fun calculateTransform(
        sectionState: VideoSectionState,
        sourceDimensions: SourceVideoDimensions,
        viewportWidthPx: Float,
        viewportHeightPx: Float
    ): SectionTransformResult {
        val srcAR = sourceDimensions.aspectRatio
        val secAR = (viewportWidthPx / viewportHeightPx.coerceAtLeast(1f)).coerceIn(0.1f, 10.0f)

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

        val clampedPanX = sectionState.panOffset.x.coerceIn(-viewportWidthPx * 1.5f, viewportWidthPx * 1.5f)
        val clampedPanY = sectionState.panOffset.y.coerceIn(-viewportHeightPx * 1.5f, viewportHeightPx * 1.5f)

        val normPanX = (clampedPanX / viewportWidthPx.coerceAtLeast(1f)) * 2.0f
        val normPanY = (-clampedPanY / viewportHeightPx.coerceAtLeast(1f)) * 2.0f

        return SectionTransformResult(
            scaleX = finalScaleX,
            scaleY = finalScaleY,
            normPanX = normPanX.coerceIn(-2.0f, 2.0f),
            normPanY = normPanY.coerceIn(-2.0f, 2.0f),
            composePanX = clampedPanX,
            composePanY = clampedPanY
        )
    }

    /**
     * Calculates the exact Matrix transformation for Media3 Transformer / OpenGL NDC space.
     */
    fun calculateExportMatrix(
        sectionState: VideoSectionState,
        sectionRatio: Float,
        isTopSection: Boolean,
        sourceDimensions: SourceVideoDimensions,
        outputWidthPx: Float = 1080f,
        outputHeightPx: Float = 1920f
    ): Matrix {
        val matrix = Matrix()
        val sectionHeightPx = outputHeightPx * sectionRatio.coerceIn(0.15f, 0.85f)

        val transform = calculateTransform(
            sectionState = sectionState,
            sourceDimensions = sourceDimensions,
            viewportWidthPx = outputWidthPx,
            viewportHeightPx = sectionHeightPx
        )

        matrix.postScale(transform.scaleX, transform.scaleY * sectionRatio)

        val translateNDC_Y = if (isTopSection) {
            (1.0f - sectionRatio) + transform.normPanY
        } else {
            -sectionRatio + transform.normPanY
        }

        matrix.postTranslate(transform.normPanX, translateNDC_Y)
        return matrix
    }
}
