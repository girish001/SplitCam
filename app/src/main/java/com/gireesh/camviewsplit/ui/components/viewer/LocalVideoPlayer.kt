package com.gireesh.camviewsplit.ui.components.viewer

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.gireesh.camviewsplit.model.ContentScaleMode
import com.gireesh.camviewsplit.model.LocalVideoItem
import com.gireesh.camviewsplit.ui.theme.CyanPrimary
import com.gireesh.camviewsplit.ui.theme.DarkBorder
import com.gireesh.camviewsplit.ui.theme.DarkSurface
import com.gireesh.camviewsplit.ui.theme.DarkSurfaceElevated
import com.gireesh.camviewsplit.ui.theme.TealAccent
import com.gireesh.camviewsplit.ui.theme.TextPrimary
import com.gireesh.camviewsplit.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun LocalVideoPlayer(
    videoList: List<LocalVideoItem>,
    currentIndex: Int,
    isFullScreen: Boolean,
    onIndexChanged: (Int) -> Unit,
    onSelectVideosClick: () -> Unit,
    onRemoveVideoClick: (Int) -> Unit,
    onClearSelectionClick: () -> Unit,
    onToggleFullScreenClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val currentVideo = videoList.getOrNull(currentIndex)

    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var scaleMode by remember { mutableStateOf(ContentScaleMode.FIT) }
    var isAutoPlayNext by remember { mutableStateOf(true) }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }

    var playbackErrorMsg by remember { mutableStateOf<String?>(null) }
    var showPlaylistDrawer by remember { mutableStateOf(false) }

    // ExoPlayer Lifecycle and Event Management
    DisposableEffect(currentVideo) {
        if (currentVideo != null) {
            playbackErrorMsg = null
            val player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(currentVideo.uri))
                volume = if (isMuted) 0f else 1f
                prepare()
                playWhenReady = true

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            totalDurationMs = duration.coerceAtLeast(0L)
                        } else if (state == Player.STATE_ENDED) {
                            if (isAutoPlayNext && videoList.isNotEmpty()) {
                                val nextIdx = (currentIndex + 1) % videoList.size
                                onIndexChanged(nextIdx)
                            }
                        }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        playbackErrorMsg = "Unplayable video format: ${error.localizedMessage}"
                    }
                })
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

    // Live position progress ticker
    LaunchedEffect(exoPlayer, isPlaying) {
        while (exoPlayer != null && isPlaying) {
            currentPositionMs = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
            delay(500)
        }
    }

    Column(modifier = modifier.fillMaxSize().background(DarkSurface)) {
        if (videoList.isEmpty() || currentVideo == null) {
            // Empty Playlist State View
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(DarkSurfaceElevated, CircleShape)
                            .border(1.dp, CyanPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(32.dp))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Local Video Storage Viewer", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Browse and play videos saved on your phone storage with vertical swipe navigation.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onSelectVideosClick,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Select Videos from Phone", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // Active Local Video Player Container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentIndex, videoList.size) {
                        var totalDrag = 0f
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                totalDrag += dragAmount
                            },
                            onDragEnd = {
                                if (totalDrag < -100f) {
                                    // Swipe Up -> Next Video
                                    if (videoList.isNotEmpty()) {
                                        val nextIdx = (currentIndex + 1) % videoList.size
                                        onIndexChanged(nextIdx)
                                    }
                                } else if (totalDrag > 100f) {
                                    // Swipe Down -> Previous Video
                                    if (videoList.isNotEmpty()) {
                                        val prevIdx = if (currentIndex - 1 < 0) videoList.size - 1 else currentIndex - 1
                                        onIndexChanged(prevIdx)
                                    }
                                }
                            }
                        )
                    }
            ) {
                if (playbackErrorMsg != null) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = Color.Red, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Playback Error", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(playbackErrorMsg!!, color = TextSecondary, fontSize = 12.sp)
                    }
                } else {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                resizeMode = if (scaleMode == ContentScaleMode.FILL) {
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                } else {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            }
                        },
                        update = { view ->
                            view.player = exoPlayer
                            view.resizeMode = if (scaleMode == ContentScaleMode.FILL) {
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            } else {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Overlay Controls Header & Footer Bar
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header Overlay
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurface.copy(alpha = 0.85f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentVideo.displayName,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "Video ${currentIndex + 1} of ${videoList.size} (Swipe Up/Down to Navigate)",
                                color = TealAccent,
                                fontSize = 10.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onSelectVideosClick, modifier = Modifier.size(28.dp)) {
                                Icon(imageVector = Icons.Default.FolderOpen, contentDescription = "Add Videos", tint = CyanPrimary, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { showPlaylistDrawer = !showPlaylistDrawer }, modifier = Modifier.size(28.dp)) {
                                Icon(imageVector = Icons.Default.PlaylistPlay, contentDescription = "Playlist", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = onToggleFullScreenClick, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Footer Playback Controls
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurface.copy(alpha = 0.85f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        // Seek Progress Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(formatMs(currentPositionMs), color = TextSecondary, fontSize = 10.sp)
                            Slider(
                                value = if (totalDurationMs > 0) currentPositionMs.toFloat() / totalDurationMs.toFloat() else 0f,
                                onValueChange = { frac ->
                                    val targetMs = (frac * totalDurationMs).toLong()
                                    exoPlayer?.seekTo(targetMs)
                                    currentPositionMs = targetMs
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = CyanPrimary,
                                    activeTrackColor = CyanPrimary,
                                    inactiveTrackColor = DarkBorder
                                ),
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            Text(formatMs(totalDurationMs), color = TextSecondary, fontSize = 10.sp)
                        }

                        // Playback Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Previous Video
                                IconButton(
                                    onClick = {
                                        val prevIdx = if (currentIndex - 1 < 0) videoList.size - 1 else currentIndex - 1
                                        onIndexChanged(prevIdx)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(20.dp))
                                }

                                // Play / Pause
                                IconButton(
                                    onClick = {
                                        exoPlayer?.let { p ->
                                            if (p.isPlaying) p.pause() else p.play()
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play Pause",
                                        tint = CyanPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // Next Video
                                IconButton(
                                    onClick = {
                                        val nextIdx = (currentIndex + 1) % videoList.size
                                        onIndexChanged(nextIdx)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Fit / Fill Toggle
                                IconButton(
                                    onClick = {
                                        scaleMode = if (scaleMode == ContentScaleMode.FILL) ContentScaleMode.FIT else ContentScaleMode.FILL
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.AspectRatio, contentDescription = "Fit Fill", tint = Color.White, modifier = Modifier.size(18.dp))
                                }

                                // Mute Toggle
                                IconButton(
                                    onClick = {
                                        isMuted = !isMuted
                                        exoPlayer?.volume = if (isMuted) 0f else 1f
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        contentDescription = "Mute",
                                        tint = if (isMuted) Color.Red else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Autoplay Next Toggle
                                IconButton(
                                    onClick = { isAutoPlayNext = !isAutoPlayNext },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoMode,
                                        contentDescription = "Autoplay Next",
                                        tint = if (isAutoPlayNext) TealAccent else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Playlist Drawer Overlay
                androidx.compose.animation.AnimatedVisibility(visible = showPlaylistDrawer) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkSurface.copy(alpha = 0.95f))
                            .padding(12.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Selected Local Videos Playlist (${videoList.size})", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Row {
                                    IconButton(onClick = onClearSelectionClick, modifier = Modifier.size(28.dp)) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear All", tint = Color.Red, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = { showPlaylistDrawer = false }, modifier = Modifier.size(28.dp)) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close Playlist", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DarkBorder)

                            LazyColumn(modifier = Modifier.weight(1f)) {
                                itemsIndexed(videoList) { idx, item ->
                                    val isCurrent = idx == currentIndex
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isCurrent) CyanPrimary.copy(alpha = 0.2f) else DarkSurfaceElevated)
                                            .border(1.dp, if (isCurrent) CyanPrimary else DarkBorder, RoundedCornerShape(8.dp))
                                            .clickable {
                                                onIndexChanged(idx)
                                                showPlaylistDrawer = false
                                            }
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${idx + 1}. ${item.displayName}",
                                            color = if (isCurrent) CyanPrimary else Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )

                                        IconButton(onClick = { onRemoveVideoClick(idx) }, modifier = Modifier.size(24.dp)) {
                                            Icon(imageVector = Icons.Default.Close, contentDescription = "Remove", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
