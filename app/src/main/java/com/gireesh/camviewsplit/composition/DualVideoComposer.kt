package com.gireesh.camviewsplit.composition

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.gireesh.camviewsplit.model.ContentScaleMode
import com.gireesh.camviewsplit.model.DualVideoCompositionConfig
import com.gireesh.camviewsplit.model.VideoSource
import com.gireesh.camviewsplit.util.CompositionTransformEngine
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

@OptIn(UnstableApi::class)
class DualVideoComposer(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val TAG = "DualVideoComposer"

    private var activeTransformer: Transformer? = null
    private var progressJob: Job? = null

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Composes top and bottom video sources according to [config] into [outputFile].
     * Guarantees zero UI elements, buttons, timers, or overlays in exported video.
     */
    fun exportComposition(
        topVideoUri: Uri?,
        bottomVideoUri: Uri?,
        config: DualVideoCompositionConfig,
        outputFile: File,
        outputWidth: Int = 1080,
        outputHeight: Int = 1920,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        if (_isExporting.value) {
            onError("Export is already in progress.")
            return
        }

        if (topVideoUri == null && bottomVideoUri == null) {
            onError("No video sources selected for composition.")
            return
        }

        try {
            val sequences = mutableListOf<EditedMediaItemSequence>()

            // Top Video Sequence
            if (topVideoUri != null) {
                val topMediaItem = MediaItem.fromUri(topVideoUri)
                val topEffectsList = mutableListOf<Effect>()

                val topState = config.topState
                val splitRatio = config.splitRatio

                val topMatrixTransform = MatrixTransformation { _ ->
                    CompositionTransformEngine.calculateExportMatrix(
                        sectionState = topState,
                        sectionRatio = splitRatio,
                        isTopSection = true,
                        outputWidth = outputWidth.toFloat(),
                        outputHeight = outputHeight.toFloat()
                    )
                }
                topEffectsList.add(topMatrixTransform)

                val isTopCamera = topState.source is VideoSource.LiveCameraFront || topState.source is VideoSource.LiveCameraRear
                val removeTopAudio = if (isTopCamera) {
                    topState.isMuted || !config.isMicAudioEnabled
                } else {
                    topState.isMuted
                }

                val topEditedItem = EditedMediaItem.Builder(topMediaItem)
                    .setRemoveAudio(removeTopAudio)
                    .setEffects(Effects(emptyList(), topEffectsList))
                    .build()

                sequences.add(EditedMediaItemSequence.Builder(topEditedItem).build())
            }

            // Bottom Video Sequence
            if (bottomVideoUri != null) {
                val bottomMediaItem = MediaItem.fromUri(bottomVideoUri)
                val bottomEffectsList = mutableListOf<Effect>()

                val bottomState = config.bottomState
                val splitRatio = config.splitRatio

                val bottomMatrixTransform = MatrixTransformation { _ ->
                    CompositionTransformEngine.calculateExportMatrix(
                        sectionState = bottomState,
                        sectionRatio = 1.0f - splitRatio,
                        isTopSection = false,
                        outputWidth = outputWidth.toFloat(),
                        outputHeight = outputHeight.toFloat()
                    )
                }
                bottomEffectsList.add(bottomMatrixTransform)

                val isBottomCamera = bottomState.source is VideoSource.LiveCameraFront || bottomState.source is VideoSource.LiveCameraRear
                val removeBottomAudio = if (isBottomCamera) {
                    bottomState.isMuted || !config.isMicAudioEnabled
                } else {
                    bottomState.isMuted
                }

                val bottomEditedItem = EditedMediaItem.Builder(bottomMediaItem)
                    .setRemoveAudio(removeBottomAudio)
                    .setEffects(Effects(emptyList(), bottomEffectsList))
                    .build()

                sequences.add(EditedMediaItemSequence.Builder(bottomEditedItem).build())
            }

            val compositionEffects = listOf<Effect>(
                Presentation.createForWidthAndHeight(outputWidth, outputHeight, Presentation.LAYOUT_SCALE_TO_FIT)
            )

            val composition = Composition.Builder(sequences)
                .setEffects(Effects(emptyList(), compositionEffects))
                .build()

            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    _isExporting.value = false
                    stopProgressTracking()
                    Log.d(TAG, "Dual video composition exported successfully to ${outputFile.absolutePath}")
                    scope.launch(Dispatchers.Main) {
                        onSuccess(outputFile)
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    _isExporting.value = false
                    stopProgressTracking()
                    val msg = exportException.localizedMessage ?: "Unknown export error"
                    Log.e(TAG, "Export failed: $msg", exportException)
                    _errorMessage.value = msg
                    scope.launch(Dispatchers.Main) {
                        onError(msg)
                    }
                }
            }

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(listener)
                .build()

            activeTransformer = transformer
            _isExporting.value = true
            _exportProgress.value = 0f

            transformer.start(composition, outputFile.absolutePath)
            startProgressTracking(transformer)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize composition export", e)
            _isExporting.value = false
            stopProgressTracking()
            onError("Export initialization failed: ${e.localizedMessage}")
        }
    }

    fun cancelExport() {
        activeTransformer?.cancel()
        activeTransformer = null
        _isExporting.value = false
        stopProgressTracking()
    }

    private fun startProgressTracking(transformer: Transformer) {
        stopProgressTracking()
        progressJob = scope.launch(Dispatchers.Main) {
            val progressHolder = ProgressHolder()
            while (isActive && _isExporting.value) {
                val state = transformer.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    _exportProgress.value = progressHolder.progress / 100f
                }
                delay(200)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }
}
