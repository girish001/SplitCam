package com.gireesh.camviewsplit

import com.gireesh.camviewsplit.model.ContentScaleMode
import com.gireesh.camviewsplit.model.DualVideoCompositionConfig
import com.gireesh.camviewsplit.model.RecordingSession
import com.gireesh.camviewsplit.model.VideoSectionState
import com.gireesh.camviewsplit.model.VideoSource
import com.gireesh.camviewsplit.util.CompositionSourceResolver
import com.gireesh.camviewsplit.util.CompositionTransformEngine
import com.gireesh.camviewsplit.util.ResolutionValidator
import com.gireesh.camviewsplit.util.SocialPlatform
import com.gireesh.camviewsplit.util.SourceVideoDimensions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

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
    fun testProductionSourceResolverAllFourCombinations() {
        val mockCameraUri1 = mock(android.net.Uri::class.java)
        val mockCameraUri2 = mock(android.net.Uri::class.java)
        val mockGalleryUri1 = mock(android.net.Uri::class.java)
        val mockGalleryUri2 = mock(android.net.Uri::class.java)

        // 1. Camera + Camera
        val config1 = DualVideoCompositionConfig(
            topState = VideoSectionState(source = VideoSource.LiveCameraFront),
            bottomState = VideoSectionState(source = VideoSource.LiveCameraRear)
        )
        val session1 = RecordingSession(
            topSource = VideoSource.LiveCameraFront,
            bottomSource = VideoSource.LiveCameraRear,
            primaryCameraUri = mockCameraUri1,
            secondaryCameraUri = mockCameraUri2
        )
        val pair1 = CompositionSourceResolver.resolve(config1, session1)
        assertTrue(pair1.isValid)
        assertEquals(mockCameraUri1, pair1.topUri)
        assertEquals(mockCameraUri2, pair1.bottomUri)

        // 2. Camera + Gallery
        val config2 = DualVideoCompositionConfig(
            topState = VideoSectionState(source = VideoSource.LiveCameraFront),
            bottomState = VideoSectionState(source = VideoSource.GalleryVideo(mockGalleryUri2))
        )
        val session2 = RecordingSession(
            topSource = VideoSource.LiveCameraFront,
            bottomSource = VideoSource.None,
            primaryCameraUri = mockCameraUri1
        )
        val pair2 = CompositionSourceResolver.resolve(config2, session2)
        assertTrue(pair2.isValid)
        assertEquals(mockCameraUri1, pair2.topUri)
        assertEquals(mockGalleryUri2, pair2.bottomUri)

        // 3. Gallery + Camera
        val config3 = DualVideoCompositionConfig(
            topState = VideoSectionState(source = VideoSource.GalleryVideo(mockGalleryUri1)),
            bottomState = VideoSectionState(source = VideoSource.LiveCameraRear)
        )
        val session3 = RecordingSession(
            topSource = VideoSource.None,
            bottomSource = VideoSource.LiveCameraRear,
            primaryCameraUri = mockCameraUri2
        )
        val pair3 = CompositionSourceResolver.resolve(config3, session3)
        assertTrue(pair3.isValid)
        assertEquals(mockGalleryUri1, pair3.topUri)
        assertEquals(mockCameraUri2, pair3.bottomUri)

        // 4. Gallery + Gallery
        val config4 = DualVideoCompositionConfig(
            topState = VideoSectionState(source = VideoSource.GalleryVideo(mockGalleryUri1)),
            bottomState = VideoSectionState(source = VideoSource.GalleryVideo(mockGalleryUri2))
        )
        val pair4 = CompositionSourceResolver.resolve(config4, null)
        assertTrue(pair4.isValid)
        assertEquals(mockGalleryUri1, pair4.topUri)
        assertEquals(mockGalleryUri2, pair4.bottomUri)
    }

    @Test
    fun testStaleRecordingSessionInvalidation() {
        val mockCameraUri = mock(android.net.Uri::class.java)

        val initialConfig = DualVideoCompositionConfig(
            topState = VideoSectionState(source = VideoSource.LiveCameraFront),
            bottomState = VideoSectionState(source = VideoSource.None)
        )

        val session = RecordingSession(
            topSource = VideoSource.LiveCameraFront,
            bottomSource = VideoSource.None,
            primaryCameraUri = mockCameraUri
        )

        assertTrue(session.isValidForConfig(initialConfig))

        // User changes top camera source to LiveCameraRear
        val updatedConfig = initialConfig.copy(
            topState = VideoSectionState(source = VideoSource.LiveCameraRear)
        )

        assertFalse(session.isValidForConfig(updatedConfig))

        val resolved = CompositionSourceResolver.resolve(updatedConfig, session)
        assertFalse(resolved.isValid)
        assertNull(resolved.topUri)
    }

    @Test
    fun testCompositionTransformEngineFitAndFill() {
        val fitState = VideoSectionState(contentScaleMode = ContentScaleMode.FIT, zoomScale = 1.0f)
        val fillState = VideoSectionState(contentScaleMode = ContentScaleMode.FILL, zoomScale = 1.0f)

        val fitParams = CompositionTransformEngine.calculateTransform(
            sectionState = fitState,
            sourceDimensions = SourceVideoDimensions(1920, 1080, 0),
            viewportWidthPx = 1080f,
            viewportHeightPx = 960f
        )

        val fillParams = CompositionTransformEngine.calculateTransform(
            sectionState = fillState,
            sourceDimensions = SourceVideoDimensions(1920, 1080, 0),
            viewportWidthPx = 1080f,
            viewportHeightPx = 960f
        )

        assertTrue(fillParams.scaleX >= fitParams.scaleX)
        assertTrue(fillParams.scaleY >= fitParams.scaleY)
    }

    @Test
    fun testCompositionTransformEngineSourceAspectRatios() {
        val portraitDims = SourceVideoDimensions(1080, 1920, 0)
        val landscapeDims = SourceVideoDimensions(1920, 1080, 0)
        val squareDims = SourceVideoDimensions(1080, 1080, 0)
        val rotatedDims = SourceVideoDimensions(1920, 1080, 90)

        assertEquals(0.5625f, portraitDims.aspectRatio, 0.01f)
        assertEquals(1.777f, landscapeDims.aspectRatio, 0.01f)
        assertEquals(1.0f, squareDims.aspectRatio, 0.01f)
        assertEquals(0.5625f, rotatedDims.aspectRatio, 0.01f)

        val fitState = VideoSectionState(contentScaleMode = ContentScaleMode.FIT)

        val landParams = CompositionTransformEngine.calculateTransform(
            sectionState = fitState,
            sourceDimensions = landscapeDims,
            viewportWidthPx = 1080f,
            viewportHeightPx = 960f
        )
        assertTrue(landParams.scaleX <= 1.0f)
        assertTrue(landParams.scaleY <= 1.0f)
    }

    @Test
    fun testCompositionTransformEngineExtremePanClamping() {
        val extremePanState = VideoSectionState(
            panOffset = androidx.compose.ui.geometry.Offset(10000f, -10000f)
        )

        val params = CompositionTransformEngine.calculateTransform(
            sectionState = extremePanState,
            sourceDimensions = SourceVideoDimensions(1080, 1920, 0),
            viewportWidthPx = 1080f,
            viewportHeightPx = 960f
        )

        assertEquals(2.0f, params.normPanX, 0.01f)
        assertEquals(2.0f, params.normPanY, 0.01f)
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
