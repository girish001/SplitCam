package com.gireesh.camviewsplit.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.gireesh.camviewsplit.camera.CameraCaptureManager
import com.gireesh.camviewsplit.camera.ScreenRecordManager
import com.gireesh.camviewsplit.data.MediaRepository
import com.gireesh.camviewsplit.model.MediaItem
import com.gireesh.camviewsplit.model.RecordMode
import com.gireesh.camviewsplit.ui.components.CameraSection
import com.gireesh.camviewsplit.ui.components.GallerySection
import com.gireesh.camviewsplit.ui.components.PhotoViewer
import com.gireesh.camviewsplit.ui.components.VideoPlayerView
import com.gireesh.camviewsplit.ui.theme.CyanPrimary
import com.gireesh.camviewsplit.ui.theme.DarkBackground
import com.gireesh.camviewsplit.ui.theme.DarkBorder
import com.gireesh.camviewsplit.ui.theme.DarkSurface
import com.gireesh.camviewsplit.ui.theme.DarkSurfaceElevated
import com.gireesh.camviewsplit.ui.theme.RecRed
import com.gireesh.camviewsplit.ui.theme.TextPrimary
import com.gireesh.camviewsplit.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Repositories & Managers
    val mediaRepository = remember { MediaRepository(context) }
    val cameraCaptureManager = remember { CameraCaptureManager(context, scope) }
    val screenRecordManager = remember { ScreenRecordManager(context, scope) }

    val projectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    // Permissions check
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            hasCameraPermission = permissions[Manifest.permission.CAMERA] == true ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

            if (!hasCameraPermission) {
                Toast.makeText(context, "Camera permission is required for live preview", Toast.LENGTH_LONG).show()
            } else if (!hasAudioPermission) {
                Toast.makeText(context, "Microphone permission denied: Videos will be recorded without sound", Toast.LENGTH_LONG).show()
            }
        }
    )

    // MediaProjection screen capture intent launcher
    val projectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                screenRecordManager.startScreenRecording(
                    resultCode = result.resultCode,
                    resultData = result.data!!,
                    onStart = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Split screen recording started")
                        }
                    },
                    onError = { err ->
                        scope.launch {
                            snackbarHostState.showSnackbar("Recording error: $err")
                        }
                    }
                )
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar("Screen capture permission was cancelled")
                }
            }
        }
    )

    LaunchedEffect(Unit) {
        screenRecordManager.bindService()
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
    val cameraRecordingDuration by cameraCaptureManager.recordingDurationSeconds.collectAsState()
    val lensFacing by cameraCaptureManager.lensFacing.collectAsState()
    val isFlashOn by cameraCaptureManager.isFlashOn.collectAsState()
    val cameraError by cameraCaptureManager.errorMessage.collectAsState()

    val isScreenRecording by screenRecordManager.isRecording.collectAsState()
    val screenRecordingDuration by screenRecordManager.recordingDurationSeconds.collectAsState()
    val recordMode by screenRecordManager.recordMode.collectAsState()

    val isAnyRecording = if (recordMode == RecordMode.SPLIT_SCREEN) isScreenRecording else isCameraRecording
    val activeDuration = if (recordMode == RecordMode.SPLIT_SCREEN) screenRecordingDuration else cameraRecordingDuration

    val mediaList by mediaRepository.mediaItems.collectAsState()

    // Bottom section state
    var selectedVideo by remember { mutableStateOf<MediaItem?>(null) }
    var selectedPhoto by remember { mutableStateOf<MediaItem?>(null) }

    // Handle camera errors
    LaunchedEffect(cameraError) {
        cameraError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            cameraCaptureManager.clearError()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraCaptureManager.release()
            screenRecordManager.release()
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
                        text = "Camera Permission Required",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "SplitCam requires camera and microphone permissions to capture live video and photos.",
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
                        Text(
                            text = "Grant Permissions",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            // Permanent Vertical Split:
            // Top Section = 40% (0.40f)
            // Bottom Section = 60% (0.60f)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(DarkBackground)
            ) {
                // TOP SECTION — 40% OF SCREEN
                CameraSection(
                    cameraCaptureManager = cameraCaptureManager,
                    isRecording = isAnyRecording,
                    recordingDurationSeconds = activeDuration,
                    lensFacing = lensFacing,
                    isFlashOn = isFlashOn,
                    recordMode = recordMode,
                    onRecordModeChange = { screenRecordManager.setRecordMode(it) },
                    onPhotoCaptured = {
                        cameraCaptureManager.capturePhoto(
                            onSuccess = { uri, displayName ->
                                scope.launch {
                                    val item = mediaRepository.addCapturedMedia(
                                        uri = uri,
                                        isVideo = false,
                                        displayName = displayName
                                    )
                                    selectedVideo = null
                                    selectedPhoto = item
                                    snackbarHostState.showSnackbar("Photo captured and saved!")
                                }
                            },
                            onError = { err ->
                                scope.launch {
                                    snackbarHostState.showSnackbar("Error: $err")
                                }
                            }
                        )
                    },
                    onStartRecordClick = {
                        if (recordMode == RecordMode.SPLIT_SCREEN) {
                            try {
                                projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                            } catch (e: Exception) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Could not launch screen recorder: ${e.localizedMessage}")
                                }
                            }
                        } else {
                            cameraCaptureManager.startVideoRecording(
                                onSuccess = { uri, displayName ->
                                    scope.launch {
                                        try {
                                            val newItem = mediaRepository.addCapturedMedia(
                                                uri = uri,
                                                isVideo = true,
                                                displayName = displayName
                                            )
                                            selectedPhoto = null
                                            selectedVideo = newItem
                                            snackbarHostState.showSnackbar("Video saved! Playing below...")
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar("Video saved, but could not load player: ${e.message}")
                                        }
                                    }
                                },
                                onError = { err ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Recording error: $err")
                                    }
                                }
                            )
                        }
                    },
                    onStopRecordClick = {
                        if (recordMode == RecordMode.SPLIT_SCREEN) {
                            if (screenRecordingDuration >= 1) {
                                screenRecordManager.stopScreenRecording(
                                    onSuccess = { uri, displayName ->
                                        scope.launch {
                                            try {
                                                val newItem = mediaRepository.addCapturedMedia(
                                                    uri = uri,
                                                    isVideo = true,
                                                    displayName = displayName
                                                )
                                                selectedPhoto = null
                                                selectedVideo = newItem
                                                snackbarHostState.showSnackbar("Split screen video saved! Playing below...")
                                            } catch (e: Exception) {
                                                snackbarHostState.showSnackbar("Video saved, but could not load player: ${e.message}")
                                            }
                                        }
                                    },
                                    onError = { err ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Error stopping recording: $err")
                                        }
                                    }
                                )
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Please record for at least 2 seconds before stopping.")
                                }
                            }
                        } else {
                            if (cameraRecordingDuration >= 1) {
                                cameraCaptureManager.stopVideoRecording()
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Please record for at least 2 seconds before stopping.")
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.40f)
                )

                // Divider between top 40% and bottom 60%
                HorizontalDivider(
                    thickness = 2.dp,
                    color = if (isAnyRecording) RecRed.copy(alpha = 0.8f) else DarkBorder
                )

                // BOTTOM SECTION — 60% OF SCREEN
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.60f)
                        .background(DarkSurface)
                ) {
                    when {
                        // Inline Video Player View
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

                        // Photo Viewer View
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

                        // Local Media Gallery Grid
                        else -> {
                            GallerySection(
                                mediaList = mediaList,
                                onSelectMedia = { item ->
                                    if (item.isVideo) {
                                        selectedVideo = item
                                    } else {
                                        selectedPhoto = item
                                    }
                                },
                                onImportMedia = { uris ->
                                    scope.launch {
                                        mediaRepository.importMediaUris(uris)
                                        snackbarHostState.showSnackbar("Imported ${uris.size} media item(s)")
                                    }
                                },
                                onDeleteMedia = { item ->
                                    scope.launch {
                                        mediaRepository.deleteMediaItem(item)
                                        snackbarHostState.showSnackbar("Item deleted")
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
