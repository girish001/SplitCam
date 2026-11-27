package com.gireesh.camviewsplit

import com.gireesh.camviewsplit.model.DualVideoCompositionConfig
import com.gireesh.camviewsplit.model.VideoSectionState
import com.gireesh.camviewsplit.model.VideoSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceRoutingTest {

    private fun isCameraSource(source: VideoSource): Boolean {
        return source is VideoSource.LiveCameraFront || source is VideoSource.LiveCameraRear
    }

    @Test
    fun testCameraAndCameraSourceRouting() {
        val top = VideoSectionState(source = VideoSource.LiveCameraFront)
        val bottom = VideoSectionState(source = VideoSource.LiveCameraRear)
        val config = DualVideoCompositionConfig(topState = top, bottomState = bottom)

        assertTrue(isCameraSource(config.topState.source))
        assertTrue(isCameraSource(config.bottomState.source))
    }

    @Test
    fun testCameraAndGallerySourceRouting() {
        val top = VideoSectionState(source = VideoSource.LiveCameraFront)
        val bottom = VideoSectionState(source = VideoSource.None)
        val config = DualVideoCompositionConfig(topState = top, bottomState = bottom)

        assertTrue(isCameraSource(config.topState.source))
        assertFalse(isCameraSource(config.bottomState.source))
    }

    @Test
    fun testGalleryAndCameraSourceRouting() {
        val top = VideoSectionState(source = VideoSource.None)
        val bottom = VideoSectionState(source = VideoSource.LiveCameraRear)
        val config = DualVideoCompositionConfig(topState = top, bottomState = bottom)

        assertFalse(isCameraSource(config.topState.source))
        assertTrue(isCameraSource(config.bottomState.source))
    }

    @Test
    fun testGalleryAndGallerySourceRouting() {
        val top = VideoSectionState(source = VideoSource.None)
        val bottom = VideoSectionState(source = VideoSource.None)
        val config = DualVideoCompositionConfig(topState = top, bottomState = bottom)

        assertFalse(isCameraSource(config.topState.source))
        assertFalse(isCameraSource(config.bottomState.source))
    }

    @Test
    fun testSwapAndResetCompositionConfig() {
        val top = VideoSectionState(source = VideoSource.LiveCameraFront, zoomScale = 2.0f)
        val bottom = VideoSectionState(source = VideoSource.LiveCameraRear, zoomScale = 1.5f)
        val config = DualVideoCompositionConfig(topState = top, bottomState = bottom)

        val swapped = config.swapTopAndBottom()
        assertEquals(VideoSource.LiveCameraRear, swapped.topState.source)
        assertEquals(VideoSource.LiveCameraFront, swapped.bottomState.source)

        val reset = swapped.resetAllAdjustments()
        assertEquals(1.0f, reset.topState.zoomScale, 0.01f)
        assertEquals(1.0f, reset.bottomState.zoomScale, 0.01f)
    }

    @Test
    fun testAudioMuteRouting() {
        val top = VideoSectionState(source = VideoSource.LiveCameraFront, isMuted = true)
        val bottom = VideoSectionState(source = VideoSource.LiveCameraRear, isMuted = false)
        val config = DualVideoCompositionConfig(topState = top, bottomState = bottom, isMicAudioEnabled = false)

        assertTrue(config.topState.isMuted)
        assertFalse(config.bottomState.isMuted)
        assertFalse(config.isMicAudioEnabled)
    }
}
