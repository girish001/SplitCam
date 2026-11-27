package com.gireesh.camviewsplit.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.view.PreviewView
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.content.Intent
import com.gireesh.camviewsplit.camera.CameraCaptureManager
import com.gireesh.camviewsplit.composition.DualVideoComposer
import com.gireesh.camviewsplit.data.MediaRepository
import com.gireesh.camviewsplit.model.DualVideoCompositionConfig
import com.gireesh.camviewsplit.model.MediaItem
import com.gireesh.camviewsplit.model.VideoSectionState
import com.gireesh.camviewsplit.model.VideoSource
import com.gireesh.camviewsplit.ui.components.AudioControlPanel
import androidx.compose.material.icons.filled.Settings
import com.gireesh.camviewsplit.ui.components.ExportSettingsBottomSheet
import com.gireesh.camviewsplit.ui.components.GallerySection
import com.gireesh.camviewsplit.ui.components.InteractiveVideoViewport
import com.gireesh.camviewsplit.ui.components.LayoutPresetBar
import com.gireesh.camviewsplit.ui.components.PhotoViewer
import com.gireesh.camviewsplit.model.BottomViewerSource
import com.gireesh.camviewsplit.model.LocalVideoItem
import com.gireesh.camviewsplit.model.RecordingSession
import com.gireesh.camviewsplit.ui.components.viewer.AdjustableSplitLayout
import com.gireesh.camviewsplit.ui.components.viewer.BottomVideoViewer
import com.gireesh.camviewsplit.ui.components.SocialMediaPanel
import com.gireesh.camviewsplit.ui.components.VideoPlayerView
import com.gireesh.camviewsplit.util.CompositionSourceResolver
import com.gireesh.camviewsplit.ui.theme.CyanPrimary
import com.gireesh.camviewsplit.ui.theme.DarkBackground
import com.gireesh.camviewsplit.ui.theme.DarkBorder
import com.gireesh.camviewsplit.ui.theme.DarkSurface
import com.gireesh.camviewsplit.ui.theme.DarkSurfaceElevated
import com.gireesh.camviewsplit.ui.theme.RecRed
import com.gireesh.camviewsplit.ui.theme.TealAccent
import com.gireesh.camviewsplit.ui.theme.TextPrimary
import com.gireesh.camviewsplit.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Repositories & Managers
    val mediaRepository = remember { MediaRepository(context) }
    val cameraCaptureManager = remember { CameraCaptureManager(context, scope) }
    val dualVideoComposer = remember { DualVideoComposer(context, scope) }

    // SharedPreferences for Split Ratio Persistence
    val prefs = remember { context.getSharedPreferences("splitcam_prefs", Context.MODE_PRIVATE) }

    // Dual Video Composition Configuration (Restores saved splitRatio)
    var config by remember {
        val savedRatio = prefs.getFloat("split_ratio", 0.5f).coerceIn(0.20f, 0.80f)
        mutableStateOf(DualVideoCompositionConfig(splitRatio = savedRatio))
    }

    // Persist split ratio on change
    LaunchedEffect(config.splitRatio) {
        prefs.edit().putFloat("split_ratio", config.splitRatio).apply()
    }

    // Bottom Section Viewer States (Instagram | Facebook | YouTube | Local Storage)
    var activeBottomSource by remember { mutableStateOf(BottomViewerSource.LOCAL_STORAGE) }
    var localVideoPlaylist by remember { mutableStateOf<List<LocalVideoItem>>(emptyList()) }
    var currentLocalVideoIndex by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var isFullScreenBottomViewer by remember { mutableStateOf(false) }

    // Export Settings Bottom Sheet visibility state
    var showExportSettingsSheet by remember { mutableStateOf(false) }

    // Session Camera Recordings State
    var sessionCameraRecordings by remember { mutableStateOf<RecordingSession?>(null) }

    // PreviewView instances for top and bottom viewports
    val topPreviewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val bottomPreviewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Central Camera Source Coordinator Effect
    LaunchedEffect(config.topState.source, config.bottomState.source, lifecycleOwner) {
        cameraCaptureManager.syncCameraSources(
            lifecycleOwner = lifecycleOwner,
            topSource = config.topState.source,
            bottomSource = config.bottomState.source,
            topPreviewView = topPreviewView,
            bottomPreviewView = bottomPreviewView
        )
    }

    // Playlist restoration completion flag to prevent initial empty state race
    var isPlaylistLoaded by remember { mutableStateOf(false) }

    // Restore Local Video Playlist from persistence
    LaunchedEffect(Unit) {
        val (savedList, savedIdx) = mediaRepository.loadLocalPlaylist()
        if (savedList.isNotEmpty()) {
            localVideoPlaylist = savedList
            currentLocalVideoIndex = savedIdx
        }
        isPlaylistLoaded = true
    }

    // Persist Local Video Playlist ONLY AFTER initial restoration has completed
    LaunchedEffect(localVideoPlaylist, currentLocalVideoIndex, isPlaylistLoaded) {
        if (isPlaylistLoaded) {
            mediaRepository.saveLocalPlaylist(localVideoPlaylist, currentLocalVideoIndex)
        }
    }

    // Gallery media selection state (for picking video into top or bottom section)
    var targetSectionForPicker by remember { mutableStateOf<String?>(null) } // "TOP" or "BOTTOM"

    // Last Exported Composition URI for Social Sharing
    var lastExportedVideoUri by remember { mutableStateOf<Uri?>(null) }

    // System Video Picker Launcher (supports Gallery, Downloads, Files, and PC-transferred videos)
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null && targetSectionForPicker != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}

                scope.launch {
                    mediaRepository.importMediaUris(listOf(uri))
                    val importedItem = mediaRepository.mediaItems.value.firstOrNull()
                    val targetUri = importedItem?.uri ?: uri
                    val source = VideoSource.GalleryVideo(uri = targetUri, displayName = importedItem?.displayName ?: "Selected Video")
                    if (targetSectionForPicker == "TOP") {
                        config = config.copy(topState = config.topState.copy(source = source))
                    } else {
                        config = config.copy(bottomState = config.bottomState.copy(source = source))
                    }
                }
            }
            targetSectionForPicker = null
        }
    )

    // Permissions check
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            hasCameraPermission = permissions[Manifest.permission.CAMERA] == true ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasAudioPermission) {
            val permissionsToRequest = mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
        mediaRepository.loadAllMedia()
    }

    // Observing States
    val isCameraRecording by cameraCaptureManager.isRecording.collectAsState()
    val recordingDurationSeconds by cameraCaptureManager.recordingDurationSeconds.collectAsState()
    val cameraError by cameraCaptureManager.errorMessage.collectAsState()

    val isExportingComposition by dualVideoComposer.isExporting.collectAsState()
    val exportProgress by dualVideoComposer.exportProgress.collectAsState()
    val composerError by dualVideoComposer.errorMessage.collectAsState()

    val mediaList by mediaRepository.mediaItems.collectAsState()

    // Preview / Player overlay state
    var selectedVideo by remember { mutableStateOf<MediaItem?>(null) }
    var selectedPhoto by remember { mutableStateOf<MediaItem?>(null) }

    // Camera / Export Error Handlers
    LaunchedEffect(cameraError) {
        cameraError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            cameraCaptureManager.clearError()
        }
    }

    LaunchedEffect(composerError) {
        composerError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            dualVideoComposer.clearError()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraCaptureManager.release()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground
    ) { paddingValues ->
        if (!hasCameraPermission) {
            // Permission Request Screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(DarkBackground)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(DarkSurfaceElevated, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Camera & Mic Permission Required",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "SplitCam requires camera and microphone permissions to capture dual video compositions cleanly.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            val perms = mutableListOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                perms.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionLauncher.launch(perms.toTypedArray())
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPrimary,
                            contentColor = DarkBackground
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(text = "Grant Permissions", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(DarkBackground)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Top Controls: Layout Presets & Audio Controls
                    if (!isCameraRecording && !isExportingComposition && selectedVideo == null && selectedPhoto == null) {
                        LayoutPresetBar(
                            config = config,
                            onConfigChange = { newConfig -> config = newConfig }
                        )

                        AudioControlPanel(
                            config = config,
                            onConfigChange = { newConfig -> config = newConfig }
                        )
                    }

                    // Main Viewport Container (Top Video + Divider + Bottom Video)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(480.dp)
                    ) {
                        when {
                            selectedVideo != null -> {
                                VideoPlayerView(
                                    item = selectedVideo!!,
                                    onBack = { selectedVideo = null },
                                    onDelete = { item ->
                                        scope.launch {
                                            mediaRepository.deleteMediaItem(item)
                                            selectedVideo = null
                                            snackbarHostState.showSnackbar("Video deleted")
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            selectedPhoto != null -> {
                                PhotoViewer(
                                    item = selectedPhoto!!,
                                    onBack = { selectedPhoto = null },
                                    onDelete = { item ->
                                        scope.launch {
                                            mediaRepository.deleteMediaItem(item)
                                            selectedPhoto = null
                                            snackbarHostState.showSnackbar("Photo deleted")
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            else -> {
                                AdjustableSplitLayout(
                                    splitRatio = config.splitRatio,
                                    onSplitRatioChange = { newRatio -> config = config.copy(splitRatio = newRatio) },
                                    isRecording = isCameraRecording || isExportingComposition,
                                    isFullScreenBottom = isFullScreenBottomViewer,
                                    topContent = {
                                        InteractiveVideoViewport(
                                            title = "TOP",
                                            state = config.topState,
                                            previewView = topPreviewView,
                                            isRecording = isCameraRecording || isExportingComposition,
                                            onStateChange = { newState -> config = config.copy(topState = newState) },
                                            onSelectGalleryMediaClick = {
                                                targetSectionForPicker = "TOP"
                                                mediaPickerLauncher.launch("video/*")
                                            },
                                            onSwitchCameraClick = {
                                                val newSource = if (config.topState.source is VideoSource.LiveCameraFront) {
                                                    VideoSource.LiveCameraRear
                                                } else {
                                                    VideoSource.LiveCameraFront
                                                }
                                                config = config.copy(topState = config.topState.copy(source = newSource))
                                            },
                                            onRemoveSourceClick = {
                                                config = config.copy(topState = config.topState.copy(source = VideoSource.None))
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    },
                                    bottomContent = {
                                        BottomVideoViewer(
                                            activeSource = activeBottomSource,
                                            onSourceChange = { newSource -> activeBottomSource = newSource },
                                            localVideoList = localVideoPlaylist,
                                            currentLocalIndex = currentLocalVideoIndex,
                                            isFullScreen = isFullScreenBottomViewer,
                                            onLocalVideoListChange = { newList -> localVideoPlaylist = newList },
                                            onLocalIndexChange = { newIdx -> currentLocalVideoIndex = newIdx },
                                            onToggleFullScreenClick = { isFullScreenBottomViewer = !isFullScreenBottomViewer },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // Floating Bottom Controls Row (Photo, Record Clean Video, Gallery View)
                    if (selectedVideo == null && selectedPhoto == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurface)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Photo Capture Button
                                if (!isCameraRecording && !isExportingComposition) {
                                    IconButton(
                                        onClick = {
                                            cameraCaptureManager.capturePhoto(
                                                onSuccess = { uri, name ->
                                                    scope.launch {
                                                        val item = mediaRepository.addCapturedMedia(uri, false, name)
                                                        selectedPhoto = item
                                                        snackbarHostState.showSnackbar("Photo captured cleanly!")
                                                    }
                                                },
                                                onError = { err ->
                                                    scope.launch { snackbarHostState.showSnackbar("Photo error: $err") }
                                                }
                                            )
                                        },
                                        modifier = Modifier
                                            .size(52.dp)
                                            .background(DarkSurfaceElevated, CircleShape)
                                            .border(2.dp, CyanPrimary, CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhotoCamera,
                                            contentDescription = "Capture Photo",
                                            tint = CyanPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.size(52.dp))
                                }

                                // Clean Dual-Video Record / Stop Button
                                Surface(
                                    onClick = {
                                        val isTopCamera = config.topState.source is VideoSource.LiveCameraFront || config.topState.source is VideoSource.LiveCameraRear
                                        val isBottomCamera = config.bottomState.source is VideoSource.LiveCameraFront || config.bottomState.source is VideoSource.LiveCameraRear

                                        if (isCameraRecording) {
                                            // Stop CameraX Recording & Export Clean Dual-Video Composition
                                            cameraCaptureManager.stopVideoRecording()
                                        } else if (!isExportingComposition) {
                                            if (!isTopCamera && !isBottomCamera) {
                                                // Combination 4: Gallery Video + Gallery Video (Direct Export)
                                                val topGalleryUri = (config.topState.source as? VideoSource.GalleryVideo)?.uri
                                                val bottomGalleryUri = (config.bottomState.source as? VideoSource.GalleryVideo)?.uri

                                                if (topGalleryUri == null && bottomGalleryUri == null) {
                                                    scope.launch { snackbarHostState.showSnackbar("Please select at least one video or live camera source.") }
                                                    return@Surface
                                                }

                                                scope.launch {
                                                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
                                                    val cleanFileName = "SplitCam_CLEAN_$timestamp.mp4"
                                                    val moviesDir = context.getExternalFilesDir("Movies") ?: context.filesDir
                                                    val cleanOutputFile = File(moviesDir, cleanFileName)

                                                    dualVideoComposer.exportComposition(
                                                        topVideoUri = topGalleryUri,
                                                        bottomVideoUri = bottomGalleryUri,
                                                        config = config,
                                                        outputFile = cleanOutputFile,
                                                        onSuccess = { exportedFile ->
                                                            scope.launch(Dispatchers.IO) {
                                                                val savedUri = saveVideoToPublicGallery(context, exportedFile, cleanFileName)
                                                                if (savedUri != null) {
                                                                    val newItem = mediaRepository.addCapturedMedia(
                                                                        uri = savedUri,
                                                                        isVideo = true,
                                                                        displayName = cleanFileName
                                                                    )
                                                                    withContext(Dispatchers.Main) {
                                                                        lastExportedVideoUri = savedUri
                                                                        selectedVideo = newItem
                                                                        snackbarHostState.showSnackbar("Clean dual video saved to Movies/SplitCam/$cleanFileName")
                                                                    }
                                                                } else {
                                                                    withContext(Dispatchers.Main) {
                                                                        snackbarHostState.showSnackbar("Failed to save video to gallery.")
                                                                    }
                                                                }
                                                            }
                                                        },
                                                        onError = { err ->
                                                            scope.launch { snackbarHostState.showSnackbar("Export error: $err") }
                                                        }
                                                    )
                                                }
                                            } else {
                                                // Combinations 1, 2, 3: Live Camera(s) + Optional Gallery Video
                                                cameraCaptureManager.startVideoRecording(
                                                    onSuccess = { primaryCameraUri, secondaryCameraUri ->
                                                        sessionCameraRecordings = RecordingSession(
                                                            topSource = config.topState.source,
                                                            bottomSource = config.bottomState.source,
                                                            primaryCameraUri = primaryCameraUri,
                                                            secondaryCameraUri = secondaryCameraUri
                                                        )
                                                        val resolved = CompositionSourceResolver.resolve(config, sessionCameraRecordings)

                                                        scope.launch {
                                                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
                                                            val cleanFileName = "SplitCam_CLEAN_$timestamp.mp4"
                                                            val moviesDir = context.getExternalFilesDir("Movies") ?: context.filesDir
                                                            val cleanOutputFile = File(moviesDir, cleanFileName)

                                                            dualVideoComposer.exportComposition(
                                                                topVideoUri = resolved.topUri,
                                                                bottomVideoUri = resolved.bottomUri,
                                                                config = config,
                                                                outputFile = cleanOutputFile,
                                                                onSuccess = { exportedFile ->
                                                                    scope.launch(Dispatchers.IO) {
                                                                        val savedUri = saveVideoToPublicGallery(context, exportedFile, cleanFileName)
                                                                        if (savedUri != null) {
                                                                            val newItem = mediaRepository.addCapturedMedia(
                                                                                uri = savedUri,
                                                                                isVideo = true,
                                                                                displayName = cleanFileName
                                                                            )
                                                                            withContext(Dispatchers.Main) {
                                                                                lastExportedVideoUri = savedUri
                                                                                selectedVideo = newItem
                                                                                snackbarHostState.showSnackbar("Clean dual video saved to Movies/SplitCam/$cleanFileName")
                                                                            }
                                                                        }
                                                                    }
                                                                },
                                                                onError = { err ->
                                                                    scope.launch { snackbarHostState.showSnackbar("Export error: $err") }
                                                                }
                                                            )
                                                        }
                                                    },
                                                    onError = { err ->
                                                        scope.launch { snackbarHostState.showSnackbar("Recording error: $err") }
                                                    }
                                                )
                                            }
                                        }
                                    },
                                    shape = CircleShape,
                                    color = Color.Transparent,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .border(3.dp, RecRed, CircleShape)
                                        .padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                RecRed,
                                                if (isCameraRecording) RoundedCornerShape(12.dp) else CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isCameraRecording) Icons.Default.Stop else Icons.Default.Videocam,
                                            contentDescription = if (isCameraRecording) "Stop Recording" else "Record Dual Video",
                                            tint = Color.White,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }

                                // Video Size / Export Settings Button
                                IconButton(
                                    onClick = { showExportSettingsSheet = true },
                                    enabled = !isCameraRecording && !isExportingComposition,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(DarkSurfaceElevated, CircleShape)
                                        .border(1.dp, CyanPrimary.copy(alpha = 0.8f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Video Size & Export Settings",
                                        tint = CyanPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        // Resolve current live/gallery top and bottom URIs for Real Output Preview & Export
                        val liveResolvedURIs = remember(config, sessionCameraRecordings) {
                            CompositionSourceResolver.resolve(config, sessionCameraRecordings)
                        }

                // Export Settings Bottom Sheet Dialog
                if (showExportSettingsSheet) {
                    ExportSettingsBottomSheet(
                        topMediaUri = liveResolvedURIs.topUri,
                        bottomMediaUri = liveResolvedURIs.bottomUri,
                        config = config,
                        onConfigChange = { newConfig -> config = newConfig },
                        onExportWithDimensions = { customWidth, customHeight ->
                            val resolvedPair = CompositionSourceResolver.resolve(config, sessionCameraRecordings)

                            if (!resolvedPair.isValid) {
                                scope.launch { snackbarHostState.showSnackbar(resolvedPair.errorMessage ?: "Video sources not ready for export.") }
                                return@ExportSettingsBottomSheet
                            }

                            scope.launch {
                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
                                val cleanFileName = "SplitCam_${customWidth}x${customHeight}_$timestamp.mp4"
                                val moviesDir = context.getExternalFilesDir("Movies") ?: context.filesDir
                                val cleanOutputFile = File(moviesDir, cleanFileName)

                                dualVideoComposer.exportComposition(
                                    topVideoUri = resolvedPair.topUri,
                                    bottomVideoUri = resolvedPair.bottomUri,
                                    config = config,
                                    outputFile = cleanOutputFile,
                                    outputWidth = customWidth,
                                    outputHeight = customHeight,
                                    onSuccess = { exportedFile ->
                                        scope.launch(Dispatchers.IO) {
                                            val savedUri = saveVideoToPublicGallery(context, exportedFile, cleanFileName)
                                            if (savedUri != null) {
                                                val newItem = mediaRepository.addCapturedMedia(
                                                    uri = savedUri,
                                                    isVideo = true,
                                                    displayName = cleanFileName
                                                )
                                                withContext(Dispatchers.Main) {
                                                    lastExportedVideoUri = savedUri
                                                    selectedVideo = newItem
                                                    snackbarHostState.showSnackbar("Exported $customWidth × $customHeight video saved to Movies/SplitCam/")
                                                }
                                            }
                                        }
                                    },
                                    onError = { err ->
                                        scope.launch { snackbarHostState.showSnackbar("Export error: $err") }
                                    }
                                )
                            }
                        },
                        onDismiss = { showExportSettingsSheet = false }
                    )
                }
                    }
                }

                // Composition Exporting Overlay Dialog / Progress Indicator
                if (isExportingComposition) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                progress = { exportProgress },
                                color = CyanPrimary,
                                strokeWidth = 4.dp,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Exporting Clean Dual Video...",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${(exportProgress * 100).toInt()}% - Zero UI elements in exported file",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun saveVideoToPublicGallery(context: Context, sourceFile: File, displayName: String): Uri? {
    val sourceLength = sourceFile.length()
    if (!sourceFile.exists() || sourceLength <= 0L) {
        android.util.Log.e("MainScreen", "saveVideoToPublicGallery: sourceFile does not exist or is empty.")
        return null
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        // Android 8/9 (API 26-28): Runtime storage permission check
        val writePermissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        if (!writePermissionGranted) {
            android.util.Log.e("MainScreen", "saveVideoToPublicGallery (<API29): WRITE_EXTERNAL_STORAGE permission denied.")
            return null
        }

        try {
            val publicMoviesDir = File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES),
                "SplitCam"
            ).apply { if (!exists()) mkdirs() }
            val publicFile = File(publicMoviesDir, displayName)

            val bytesCopied = sourceFile.inputStream().use { input ->
                publicFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (bytesCopied != sourceLength || !publicFile.exists() || publicFile.length() != sourceLength) {
                android.util.Log.e("MainScreen", "saveVideoToPublicGallery (<API29): bytesCopied ($bytesCopied) != sourceLength ($sourceLength).")
                if (publicFile.exists()) publicFile.delete()
                return null
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.DATA, publicFile.absolutePath)
            }

            var resultUri: Uri? = null
            try {
                resultUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            } catch (e: Exception) {
                android.util.Log.e("MainScreen", "saveVideoToPublicGallery (<API29): insert failed: ${e.localizedMessage}")
            }

            if (resultUri == null) {
                val latch = java.util.concurrent.CountDownLatch(1)
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(publicFile.absolutePath),
                    arrayOf("video/mp4")
                ) { _, uri ->
                    if (uri != null && uri.scheme == "content") {
                        resultUri = uri
                    }
                    latch.countDown()
                }
                try {
                    latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
                } catch (_: Exception) {}
            }

            // Verify final URI is a valid content:// URI
            if (resultUri != null && resultUri?.scheme == "content") {
                val pfd = try { context.contentResolver.openFileDescriptor(resultUri!!, "r") } catch (_: Exception) { null }
                if (pfd != null) {
                    pfd.close()
                    return resultUri
                }
            }

            if (resultUri != null && resultUri?.scheme == "content") {
                return resultUri
            }

            // Failure path: Clean up file if no valid content:// URI was generated
            if (publicFile.exists()) publicFile.delete()
            return null
        } catch (e: Exception) {
            android.util.Log.e("MainScreen", "saveVideoToPublicGallery (<API29) failed: ${e.localizedMessage}", e)
            return null
        }
    }

    // Android 10+ (API 29+) IS_PENDING Workflow
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SplitCam/")
        put(MediaStore.Video.Media.IS_PENDING, 1)
    }

    val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
    if (uri == null) {
        android.util.Log.e("MainScreen", "saveVideoToPublicGallery (API29+): MediaStore insert returned null.")
        return null
    }

    var bytesCopied = 0L
    try {
        val outputStream = context.contentResolver.openOutputStream(uri) ?: run {
            android.util.Log.e("MainScreen", "saveVideoToPublicGallery (API29+): openOutputStream returned null.")
            context.contentResolver.delete(uri, null, null)
            return null
        }

        outputStream.use { output ->
            sourceFile.inputStream().use { input ->
                bytesCopied = input.copyTo(output)
            }
        }

        if (bytesCopied != sourceLength) {
            android.util.Log.e("MainScreen", "saveVideoToPublicGallery (API29+): bytesCopied ($bytesCopied) != sourceLength ($sourceLength).")
            context.contentResolver.delete(uri, null, null)
            return null
        }

        contentValues.clear()
        contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
        val updatedRows = context.contentResolver.update(uri, contentValues, null, null)
        if (updatedRows <= 0) {
            android.util.Log.e("MainScreen", "saveVideoToPublicGallery (API29+): IS_PENDING update returned 0 rows.")
            context.contentResolver.delete(uri, null, null)
            return null
        }

        return uri
    } catch (e: Exception) {
        android.util.Log.e("MainScreen", "saveVideoToPublicGallery (API29+) failed: ${e.localizedMessage}", e)
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (_: Exception) {}
        return null
    }
}
