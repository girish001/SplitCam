package com.gireesh.camviewsplit.ui.components

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.gireesh.camviewsplit.model.ContentScaleMode
import com.gireesh.camviewsplit.model.VideoSectionState
import com.gireesh.camviewsplit.model.VideoSource
import com.gireesh.camviewsplit.ui.theme.CyanPrimary
import com.gireesh.camviewsplit.ui.theme.DarkBorder
import com.gireesh.camviewsplit.ui.theme.DarkSurface
import com.gireesh.camviewsplit.ui.theme.TealAccent

@Composable
fun InteractiveVideoViewport(
    title: String,
    state: VideoSectionState,
    previewView: PreviewView,
    isRecording: Boolean,
    onStateChange: (VideoSectionState) -> Unit,
    onSelectGalleryMediaClick: () -> Unit,
    onSwitchCameraClick: () -> Unit,
    onRemoveSourceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // ExoPlayer for imported gallery videos
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    val isCameraSource = state.source is VideoSource.LiveCameraFront || state.source is VideoSource.LiveCameraRear
    val isGallerySource = state.source is VideoSource.GalleryVideo

    // Manage ExoPlayer lifecycle when source is GalleryVideo
    DisposableEffect(state.source) {
        if (state.source is VideoSource.GalleryVideo) {
            val player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(state.source.uri))
                repeatMode = Player.REPEAT_MODE_ALL
                volume = if (state.isMuted) 0f else 1f
                prepare()
                playWhenReady = true
            }
            exoPlayer = player
        } else {
            exoPlayer?.release()
            exoPlayer = null
        }

        onDispose {
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    // Update ExoPlayer volume when mute state changes
    LaunchedEffect(state.isMuted) {
        exoPlayer?.volume = if (state.isMuted) 0f else 1f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black)
            .pointerInput(state, isRecording) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newZoom = (state.zoomScale * zoom).coerceIn(0.8f, 4.0f)
                    val newPan = state.panOffset + pan
                    onStateChange(
                        state.copy(
                            zoomScale = newZoom,
                            panOffset = newPan
                        )
                    )
                }
            }
    ) {
        // Video Viewport Content (Camera or Video Player or Placeholder)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = state.zoomScale,
                    scaleY = state.zoomScale,
                    translationX = state.panOffset.x,
                    translationY = state.panOffset.y
                )
        ) {
            when {
                isCameraSource -> {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                isGallerySource && exoPlayer != null -> {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                            }
                        },
                        update = { view ->
                            view.player = exoPlayer
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    // Placeholder when no video source is selected
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tap Gallery or Camera below to select $title source",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Overlay Toolbar Controls (Visible when not recording)
        AnimatedVisibility(
            visible = !isRecording,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Section Title & Source Type Chip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(DarkSurface.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    val label = when (state.source) {
                        is VideoSource.LiveCameraFront -> "$title (Front Cam)"
                        is VideoSource.LiveCameraRear -> "$title (Rear Cam)"
                        is VideoSource.GalleryVideo -> "$title (Gallery)"
                        else -> title
                    }
                    Text(
                        text = label,
                        color = CyanPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Toolbar Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Switch Camera Lens (if camera source)
                    if (isCameraSource) {
                        IconButton(
                            onClick = onSwitchCameraClick,
                            modifier = Modifier
                                .size(30.dp)
                                .background(DarkSurface.copy(alpha = 0.85f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Switch Camera Lens",
                                tint = TealAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Gallery Select or Replace Video Button
                    IconButton(
                        onClick = onSelectGalleryMediaClick,
                        modifier = Modifier
                            .size(30.dp)
                            .background(DarkSurface.copy(alpha = 0.85f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isGallerySource) Icons.Default.SwapHoriz else Icons.Default.FolderOpen,
                            contentDescription = if (isGallerySource) "Replace Gallery Video" else "Import Gallery Video",
                            tint = CyanPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Remove / Clear Gallery Video Button
                    if (isGallerySource) {
                        IconButton(
                            onClick = onRemoveSourceClick,
                            modifier = Modifier
                                .size(30.dp)
                                .background(DarkSurface.copy(alpha = 0.85f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove Source",
                                tint = Color.Red,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Fit vs Fill Scale Toggle
                    IconButton(
                        onClick = {
                            val nextMode = if (state.contentScaleMode == ContentScaleMode.FILL) ContentScaleMode.FIT else ContentScaleMode.FILL
                            onStateChange(state.copy(contentScaleMode = nextMode))
                        },
                        modifier = Modifier
                            .size(30.dp)
                            .background(DarkSurface.copy(alpha = 0.85f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "Fit/Fill Mode",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Audio Mute Toggle
                    IconButton(
                        onClick = {
                            onStateChange(state.copy(isMuted = !state.isMuted))
                        },
                        modifier = Modifier
                            .size(30.dp)
                            .background(DarkSurface.copy(alpha = 0.85f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (state.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Mute Audio",
                            tint = if (state.isMuted) Color.Red else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Reset Adjustments Button
                    IconButton(
                        onClick = {
                            onStateChange(state.resetAdjustments())
                        },
                        modifier = Modifier
                            .size(30.dp)
                            .background(DarkSurface.copy(alpha = 0.85f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Zoom/Pan",
                            tint = Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
