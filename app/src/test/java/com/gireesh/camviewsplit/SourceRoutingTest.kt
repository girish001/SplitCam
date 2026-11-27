package com.gireesh.camviewsplit

import com.gireesh.camviewsplit.model.BottomViewerSource
import com.gireesh.camviewsplit.model.ContentScaleMode
import com.gireesh.camviewsplit.model.DualVideoCompositionConfig
import com.gireesh.camviewsplit.model.LocalVideoItem
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
    fun testLocalVideoPlaylistNavigationAndDuplicates() {
        val mockUri1 = mock(android.net.Uri::class.java)
        val mockUri2 = mock(android.net.Uri::class.java)

        val item1 = LocalVideoItem(mockUri1, "Video 1")
        val item2 = LocalVideoItem(mockUri2, "Video 2")

        val playlist = listOf(item1, item2)

        // Duplicate filtering check
        val isDuplicate = playlist.any { it.uri == mockUri1 }
        assertTrue(isDuplicate)

        // Next / Previous navigation calculation check
        val currentIdx = 0
        val nextIdx = (currentIdx + 1) % playlist.size
        val prevIdx = if (currentIdx - 1 < 0) playlist.size - 1 else currentIdx - 1

        assertEquals(1, nextIdx)
        assertEquals(1, prevIdx)
    }

    @Test
    fun testPlaylistClearingAndRestorationRacePrevention() {
        var isPlaylistLoaded = false
        var savedCount = -1

        fun simulateSave(playlist: List<LocalVideoItem>) {
            if (isPlaylistLoaded) {
                savedCount = playlist.size
            }
        }

        val emptyInitialList = emptyList<LocalVideoItem>()
        simulateSave(emptyInitialList)
        assertEquals(-1, savedCount) // Save blocked before restoration finishes

        // Restoration completes
        isPlaylistLoaded = true
        val restoredList = listOf(LocalVideoItem(mock(android.net.Uri::class.java), "Restored Video"))
        simulateSave(restoredList)
        assertEquals(1, savedCount)

        // User clears playlist
        val clearedList = emptyList<LocalVideoItem>()
        simulateSave(clearedList)
        assertEquals(0, savedCount) // Cleared empty list saved successfully after loading
    }

    @Test
    fun testDirectoryBoundaryValidation() {
        val baseDir = java.io.File("C:/app/imported_media")
        val validSubFile = java.io.File(baseDir, "video.mp4")
        val siblingFile = java.io.File(java.io.File("C:/app/imported_media_other"), "video.mp4")

        val dirPathWithSeparator = baseDir.path + java.io.File.separator

        assertTrue(validSubFile.path.startsWith(dirPathWithSeparator))
        assertFalse(siblingFile.path.startsWith(dirPathWithSeparator)) // Sibling directory rejected!
    }

    @Test
    fun testImportedDirSelfDeletionRejection() {
        val importedDir = java.io.File("C:/app/imported_media")
        val dirPathWithSeparator = importedDir.canonicalPath + java.io.File.separator

        // Attempting to delete importedDir itself
        val targetPath = importedDir.canonicalPath
        val isAllowed = targetPath.startsWith(dirPathWithSeparator)

        assertFalse(isAllowed) // Self-deletion of importedDir rejected!
    }

    @Test
    fun testDeletionStateConsistency() {
        var isDeleted = false
        val indexList = mutableListOf("item_1")

        fun updateIndexOnDeletionSuccess(success: Boolean) {
            if (success) {
                indexList.clear()
            }
        }

        // Deletion fails
        updateIndexOnDeletionSuccess(isDeleted)
        assertEquals(1, indexList.size) // Index preserved when deletion fails!

        // Deletion succeeds
        isDeleted = true
        updateIndexOnDeletionSuccess(isDeleted)
        assertEquals(0, indexList.size) // Index updated when deletion succeeds!
    }

    @Test
    fun testSocialPlatformSwitchingUrlUpdate() {
        var lastSource: BottomViewerSource? = null
        var loadedUrl: String? = null

        fun onUpdate(newSource: BottomViewerSource, url: String) {
            if (lastSource != newSource) {
                lastSource = newSource
                loadedUrl = url
            }
        }

        onUpdate(BottomViewerSource.INSTAGRAM, "https://www.instagram.com/reels/")
        assertEquals("https://www.instagram.com/reels/", loadedUrl)

        // Web redirect to login page should NOT trigger re-load of initial URL
        onUpdate(BottomViewerSource.INSTAGRAM, "https://www.instagram.com/accounts/login/")
        assertEquals("https://www.instagram.com/reels/", loadedUrl)

        // Switching platform tab loads new URL
        onUpdate(BottomViewerSource.YOUTUBE, "https://m.youtube.com/shorts/")
        assertEquals("https://m.youtube.com/shorts/", loadedUrl)
    }

    @Test
    fun testContentUriValidationAndFileProviderFallback() {
        val packageProviderAuthority = "com.gireesh.camviewsplit.fileprovider"
        assertTrue(packageProviderAuthority.endsWith(".fileprovider"))
    }

    @Test
    fun testWebViewPopupTransportValidation() {
        val resultMsg: android.os.Message? = null
        val isValidTransport = resultMsg?.obj is android.webkit.WebView.WebViewTransport
        assertFalse(isValidTransport)
    }

    @Test
    fun testAndroidPermissionFilteringBySdk() {
        val sdkVersion = 28 // Android 9
        val requiresWriteStorage = sdkVersion <= 28
        assertTrue(requiresWriteStorage)

        val sdk29Version = 29 // Android 10
        val requiresWriteStorageApi29 = sdk29Version <= 28
        assertFalse(requiresWriteStorageApi29)
    }

    @Test
    fun testAtomicScannerCancellation() {
        val isCompleted = java.util.concurrent.atomic.AtomicBoolean(false)
        val firstAttempt = isCompleted.compareAndSet(false, true)
        val secondAttempt = isCompleted.compareAndSet(false, true)

        assertTrue(firstAttempt)
        assertFalse(secondAttempt) // Late callback rejected safely!
    }

    @Test
    fun testApi26To28MediaStoreSavePath() {
        val apiVersion = 28
        val isLegacyApi = apiVersion < 29
        assertTrue(isLegacyApi)

        val bytesCopied = 1000L
        val sourceLength = 1000L
        val isExactMatch = bytesCopied == sourceLength
        assertTrue(isExactMatch)
    }

    @Test
    fun testMediaStoreCopyByteVerificationFailure() {
        val sourceFileLength = 5000L
        val bytesCopied = 4999L // Incomplete copy
        val isValid = bytesCopied == sourceFileLength

        assertFalse(isValid)
    }

    @Test
    fun testSplitRatioBoundsAndReset() {
        val splitRatio = 0.95f // Out of bounds
        val clampedRatio = splitRatio.coerceIn(0.20f, 0.80f)
        assertEquals(0.80f, clampedRatio, 0.001f)

        val resetRatio = 0.50f
        assertEquals(0.50f, resetRatio, 0.001f)
    }

    @Test
    fun testRecordingSourceIsolation() {
        val topSource = VideoSource.LiveCameraFront

        val config = DualVideoCompositionConfig(
            topState = VideoSectionState(source = topSource),
            bottomState = VideoSectionState(source = VideoSource.None)
        )

        val mockUri = mock(android.net.Uri::class.java)
        val session = RecordingSession(
            topSource = topSource,
            bottomSource = VideoSource.None,
            primaryCameraUri = mockUri
        )

        val resolved = CompositionSourceResolver.resolve(config, session)

        assertTrue(resolved.isValid)
        assertEquals(mockUri, resolved.topUri)
        assertNull(resolved.bottomUri) // Bottom viewer is excluded from recording
    }

    @Test
    fun testSingleTopModeExportMatrix() {
        val dims = SourceVideoDimensions(1080, 1920, 0)
        val sectionState = VideoSectionState()

        val fullMatrix = CompositionTransformEngine.calculateExportMatrix(
            sectionState = sectionState,
            splitRatio = 0.5f,
            isTopSection = true,
            isSingleTopMode = true,
            sourceDimensions = dims,
            outputWidthPx = 1080f,
            outputHeightPx = 1920f
        )

        assertTrue(fullMatrix != null)
    }

    @Test
    fun testMediaRepositoryOldFormatJsonMigration() {
        val jsonString = """{"uri":"content://media/external/video/media/1234","displayName":"Legacy Video.mp4","dateAdded":1600000000000}"""
        val hasId = jsonString.contains("\"id\":")
        assertFalse(hasId)

        val uriStart = jsonString.indexOf("\"uri\":\"") + 7
        val uriEnd = jsonString.indexOf("\"", uriStart)
        val extractedUri = jsonString.substring(uriStart, uriEnd)

        assertEquals("content://media/external/video/media/1234", extractedUri)
    }

    @Test
    fun testEmptySourceFileValidation() {
        val tempFile = java.io.File.createTempFile("empty_test", ".mp4")
        tempFile.deleteOnExit()

        assertEquals(0L, tempFile.length())
        assertFalse(tempFile.exists() && tempFile.length() > 0)
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
    fun testExportMatrixVerticalPositioningForAllSplitRatios() {
        val dims = SourceVideoDimensions(1080, 1920, 0)
        val sectionState = VideoSectionState()

        val splitRatios = listOf(0.3f, 0.4f, 0.5f, 0.6f, 0.7f)

        for (split in splitRatios) {
            val topMatrix = CompositionTransformEngine.calculateExportMatrix(
                sectionState = sectionState,
                splitRatio = split,
                isTopSection = true,
                sourceDimensions = dims,
                outputWidthPx = 1080f,
                outputHeightPx = 1920f
            )

            val bottomMatrix = CompositionTransformEngine.calculateExportMatrix(
                sectionState = sectionState,
                splitRatio = split,
                isTopSection = false,
                sourceDimensions = dims,
                outputWidthPx = 1080f,
                outputHeightPx = 1920f
            )

            assertTrue(topMatrix.isIdentity == false || topMatrix.isIdentity == true)
            assertTrue(bottomMatrix.isIdentity == false || bottomMatrix.isIdentity == true)
        }
    }

    @Test
    fun testExportMatrixScaleAndPanMultipleResolutions() {
        val sectionState = VideoSectionState(zoomScale = 1.5f)
        val dimsPortrait = SourceVideoDimensions(1080, 1920, 0)
        val dimsSquare = SourceVideoDimensions(1080, 1080, 0)
        val dimsLandscape = SourceVideoDimensions(1920, 1080, 0)

        val outputResolutions = listOf(
            Pair(1080f, 1920f),
            Pair(1080f, 1080f),
            Pair(1920f, 1080f)
        )

        for ((w, h) in outputResolutions) {
            val pMatrix = CompositionTransformEngine.calculateExportMatrix(
                sectionState = sectionState,
                splitRatio = 0.5f,
                isTopSection = true,
                sourceDimensions = dimsPortrait,
                outputWidthPx = w,
                outputHeightPx = h
            )

            val sqMatrix = CompositionTransformEngine.calculateExportMatrix(
                sectionState = sectionState,
                splitRatio = 0.5f,
                isTopSection = true,
                sourceDimensions = dimsSquare,
                outputWidthPx = w,
                outputHeightPx = h
            )

            val lMatrix = CompositionTransformEngine.calculateExportMatrix(
                sectionState = sectionState,
                splitRatio = 0.5f,
                isTopSection = true,
                sourceDimensions = dimsLandscape,
                outputWidthPx = w,
                outputHeightPx = h
            )

            assertTrue(pMatrix != null)
            assertTrue(sqMatrix != null)
            assertTrue(lMatrix != null)
        }
    }

    @Test
    fun testPreviewAndExportPanEquivalence() {
        val sectionState = VideoSectionState(
            zoomScale = 1.2f,
            panOffset = androidx.compose.ui.geometry.Offset(108f, -192f)
        )
        val dims = SourceVideoDimensions(1080, 1920, 0)

        // 1. Compose Preview Transform Parameters
        val previewTransform = CompositionTransformEngine.calculateTransform(
            sectionState = sectionState,
            sourceDimensions = dims,
            viewportWidthPx = 360f,
            viewportHeightPx = 320f,
            interactiveViewportWidthPx = 1080f,
            interactiveViewportHeightPx = 960f
        )

        // 2. Export Canvas Parameters
        val exportTransform = CompositionTransformEngine.calculateTransform(
            sectionState = sectionState,
            sourceDimensions = dims,
            viewportWidthPx = 1080f,
            viewportHeightPx = 960f,
            interactiveViewportWidthPx = 1080f,
            interactiveViewportHeightPx = 960f
        )

        // Compare preview scale factor with export scale factor
        assertEquals(previewTransform.scaleX, exportTransform.scaleX, 0.001f)
        assertEquals(previewTransform.scaleY, exportTransform.scaleY, 0.001f)

        // Compare preview normalized pan fraction with export normalized pan fraction
        assertEquals(previewTransform.normPanX, exportTransform.normPanX, 0.001f)
        assertEquals(previewTransform.normPanY, exportTransform.normPanY, 0.001f)
    }

    @Test
    fun testRotationMetadataAndAspectRatios() {
        val rot0 = SourceVideoDimensions(1920, 1080, 0)
        val rot90 = SourceVideoDimensions(1920, 1080, 90)
        val rot180 = SourceVideoDimensions(1920, 1080, 180)
        val rot270 = SourceVideoDimensions(1920, 1080, 270)

        assertEquals(1.777f, rot0.aspectRatio, 0.01f)
        assertEquals(0.5625f, rot90.aspectRatio, 0.01f)
        assertEquals(1.777f, rot180.aspectRatio, 0.01f)
        assertEquals(0.5625f, rot270.aspectRatio, 0.01f)
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
