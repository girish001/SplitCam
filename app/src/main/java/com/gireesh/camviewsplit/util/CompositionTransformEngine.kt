package com.gireesh.camviewsplit.util

import android.content.Context
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.gireesh.camviewsplit.model.ContentScaleMode
import com.gireesh.camviewsplit.model.VideoSectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SourceVideoDimensions(
    val rawWidth: Int = 1080,
    val rawHeight: Int = 1920,
    val rotationDegrees: Int = 0,
    val isValid: Boolean = true
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

    /**
     * Asynchronously retrieves video width, height, and rotation metadata on Dispatchers.IO.
     */
    suspend fun retrieveMediaDimensionsAsync(context: Context, uri: Uri?): SourceVideoDimensions {
        if (uri == null) return SourceVideoDimensions(1080, 1920, 0, isValid = false)

        return withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val rStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

                val w = wStr?.toIntOrNull() ?: 1080
                val h = hStr?.toIntOrNull() ?: 1920
                val r = rStr?.toIntOrNull() ?: 0

                val isValid = wStr != null && hStr != null && w > 0 && h > 0
                SourceVideoDimensions(w, h, r, isValid = isValid)
            } catch (_: Exception) {
                SourceVideoDimensions(1080, 1920, 0, isValid = false)
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Calculates unified scale factors and pan coordinates for Compose graphicsLayer and Media3.
     */
    fun calculateTransform(
        sectionState: VideoSectionState,
        sourceDimensions: SourceVideoDimensions,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        interactiveViewportWidthPx: Float = 1080f,
        interactiveViewportHeightPx: Float = 1920f
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

        // Calculate pan fractions relative to the interactive viewport
        val panFracX = (sectionState.panOffset.x / interactiveViewportWidthPx.coerceAtLeast(1f)).coerceIn(-1.5f, 1.5f)
        val panFracY = (sectionState.panOffset.y / interactiveViewportHeightPx.coerceAtLeast(1f)).coerceIn(-1.5f, 1.5f)

        val composePanX = panFracX * viewportWidthPx
        val composePanY = panFracY * viewportHeightPx

        val normPanX = panFracX * 2.0f
        val normPanY = -panFracY * 2.0f

        return SectionTransformResult(
            scaleX = finalScaleX,
            scaleY = finalScaleY,
            normPanX = normPanX.coerceIn(-2.0f, 2.0f),
            normPanY = normPanY.coerceIn(-2.0f, 2.0f),
            composePanX = composePanX,
            composePanY = composePanY
        )
    }

    /**
     * Calculates the exact Matrix transformation for Media3 Transformer / OpenGL NDC space.
     * Uses the original top split ratio [splitRatio] for exact vertical positioning of both sections.
     * When [isSingleTopMode] is true, top video expands to fill 100% of full output canvas.
     */
    fun calculateExportMatrix(
        sectionState: VideoSectionState,
        splitRatio: Float,
        isTopSection: Boolean,
        isSingleTopMode: Boolean = false,
        sourceDimensions: SourceVideoDimensions,
        outputWidthPx: Float = 1080f,
        outputHeightPx: Float = 1920f
    ): Matrix {
        val matrix = Matrix()

        if (isSingleTopMode && isTopSection) {
            val transform = calculateTransform(
                sectionState = sectionState,
                sourceDimensions = sourceDimensions,
                viewportWidthPx = outputWidthPx,
                viewportHeightPx = outputHeightPx,
                interactiveViewportWidthPx = outputWidthPx,
                interactiveViewportHeightPx = outputHeightPx
            )

            matrix.postScale(transform.scaleX, transform.scaleY)
            matrix.postTranslate(transform.normPanX, transform.normPanY)
            return matrix
        }

        val topRatio = splitRatio.coerceIn(0.15f, 0.85f)
        val sectionRatio = if (isTopSection) topRatio else (1.0f - topRatio)
        val sectionHeightPx = outputHeightPx * sectionRatio

        val transform = calculateTransform(
            sectionState = sectionState,
            sourceDimensions = sourceDimensions,
            viewportWidthPx = outputWidthPx,
            viewportHeightPx = sectionHeightPx,
            interactiveViewportWidthPx = outputWidthPx,
            interactiveViewportHeightPx = sectionHeightPx
        )

        matrix.postScale(transform.scaleX, transform.scaleY * sectionRatio)

        val translateNDC_Y = if (isTopSection) {
            (1.0f - topRatio) + transform.normPanY
        } else {
            -topRatio + transform.normPanY
        }

        matrix.postTranslate(transform.normPanX, translateNDC_Y)
        return matrix
    }
}
