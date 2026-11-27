package com.gireesh.camviewsplit

import com.gireesh.camviewsplit.model.DualVideoCompositionConfig
import com.gireesh.camviewsplit.model.VideoSectionState
import com.gireesh.camviewsplit.model.VideoSource
import com.gireesh.camviewsplit.util.ResolutionValidator
import com.gireesh.camviewsplit.util.SocialPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun testCameraAndCameraURIMapping() {
        val top = VideoSectionState(source = VideoSource.LiveCameraFront)
        val bottom = VideoSectionState(source = VideoSource.LiveCameraRear)
        val config = DualVideoCompositionConfig(topState = top, bottomState = bottom)

        val isTopCam = isCameraSource(config.topState.source)
        val isBottomCam = isCameraSource(config.bottomState.source)

        val primaryCamUri = "file:///cache/primary.mp4"
        val secondaryCamUri = "file:///cache/secondary.mp4"

        val topUri = if (isTopCam) primaryCamUri else null
        val bottomUri = if (isBottomCam) secondaryCamUri else null

        assertEquals("file:///cache/primary.mp4", topUri)
        assertEquals("file:///cache/secondary.mp4", bottomUri)
    }

    @Test
    fun testMissingCameraRecordingURIMapping() {
        val top = VideoSectionState(source = VideoSource.LiveCameraFront)
        val bottom = VideoSectionState(source = VideoSource.None)
        val config = DualVideoCompositionConfig(topState = top, bottomState = bottom)

        val isTopCam = isCameraSource(config.topState.source)

        val primaryCamUri: String? = null

        val topUri = if (isTopCam) primaryCamUri else null

        assertNull(topUri)
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

    @Test
    fun testResolutionValidatorPresetDimensions() {
        val portrait = ResolutionValidator.validateAndNormalize(1080, 1920)
        assertTrue(portrait.isValid)
        assertEquals(1080, portrait.width)
        assertEquals(1920, portrait.height)
        assertEquals("9:16 (Portrait)", portrait.aspectRatioLabel)

        val square = ResolutionValidator.validateAndNormalize(1080, 1080)
        assertTrue(square.isValid)
        assertEquals(1080, square.width)
        assertEquals(1080, square.height)
        assertEquals("1:1 (Square)", square.aspectRatioLabel)

        val landscape = ResolutionValidator.validateAndNormalize(1920, 1080)
        assertTrue(landscape.isValid)
        assertEquals(1920, landscape.width)
        assertEquals(1080, landscape.height)
        assertEquals("16:9 (Landscape)", landscape.aspectRatioLabel)
    }

    @Test
    fun testResolutionValidatorEvenNumberNormalization() {
        val oddRes = ResolutionValidator.validateAndNormalize(1079, 1919)
        assertTrue(oddRes.isValid)
        assertEquals(1078, oddRes.width)
        assertEquals(1918, oddRes.height)
    }

    @Test
    fun testResolutionValidatorInvalidDimensions() {
        val tooSmall = ResolutionValidator.validateAndNormalize(50, 1920)
        assertFalse(tooSmall.isValid)
        assertTrue(tooSmall.errorMessage!!.contains("at least 128px"))

        val tooLarge = ResolutionValidator.validateAndNormalize(5000, 1920)
        assertFalse(tooLarge.isValid)
        assertTrue(tooLarge.errorMessage!!.contains("cannot exceed 4096px"))
    }

    @Test
    fun testSocialPlatformMetadata() {
        assertEquals("Instagram", SocialPlatform.INSTAGRAM.displayName)
        assertEquals("com.instagram.android", SocialPlatform.INSTAGRAM.packageName)
        assertEquals("Facebook", SocialPlatform.FACEBOOK.displayName)
        assertEquals("YouTube", SocialPlatform.YOUTUBE.displayName)
    }
}
