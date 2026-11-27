package com.gireesh.camviewsplit.model

enum class SplitLayoutPreset(val ratio: Float, val label: String) {
    FIFTY_FIFTY(0.50f, "50/50"),
    FORTY_SIXTY(0.40f, "40/60"),
    SIXTY_FORTY(0.60f, "60/40"),
    CUSTOM(0.50f, "Custom")
}

data class DualVideoCompositionConfig(
    val splitRatio: Float = 0.50f,
    val activePreset: SplitLayoutPreset = SplitLayoutPreset.FIFTY_FIFTY,
    val topState: VideoSectionState = VideoSectionState(source = VideoSource.LiveCameraFront),
    val bottomState: VideoSectionState = VideoSectionState(source = VideoSource.None),
    val isMicAudioEnabled: Boolean = true,
    val showDivider: Boolean = true
) {
    fun swapTopAndBottom(): DualVideoCompositionConfig {
        return copy(
            topState = bottomState,
            bottomState = topState
        )
    }

    fun resetAllAdjustments(): DualVideoCompositionConfig {
        return copy(
            topState = topState.resetAdjustments(),
            bottomState = bottomState.resetAdjustments()
        )
    }
}
