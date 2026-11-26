package com.gireesh.camviewsplit.ui.components

import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gireesh.camviewsplit.camera.CameraCaptureManager
import com.gireesh.camviewsplit.ui.theme.CyanPrimary
import com.gireesh.camviewsplit.ui.theme.DarkBackground
import com.gireesh.camviewsplit.ui.theme.DarkSurface
import com.gireesh.camviewsplit.ui.theme.RecRed
import com.gireesh.camviewsplit.ui.theme.TealAccent
import java.util.Locale

@Composable
fun CameraSection(
    cameraCaptureManager: CameraCaptureManager,
    isRecording: Boolean,
    recordingDurationSeconds: Long,
    lensFacing: Int,
    isFlashOn: Boolean,
    onPhotoCaptured: () -> Unit,
    onVideoRecorded: (android.net.Uri, String) -> Unit,
    onRecordingError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(lifecycleOwner) {
        cameraCaptureManager.startCamera(lifecycleOwner, previewView)
    }

    DisposableEffect(Unit) {
        onDispose {
            // Keep provider unbound when view is completely disposed
        }
    }

    // Blinking effect for the red recording dot
    val infiniteTransition = rememberInfiniteTransition(label = "rec_blink")
    val recAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        // Camera live preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Subtle gradient overlay for top controls
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.75f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Logo / Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            DarkSurface.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(CyanPrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CamView Split",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // REC indicator when active
                AnimatedVisibility(
                    visible = isRecording,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val minutes = recordingDurationSeconds / 60
                    val seconds = recordingDurationSeconds % 60
                    val timeFormatted = String.format(Locale.US, "%02d:%02d", minutes, seconds)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                RecRed.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .border(1.dp, RecRed, shape = RoundedCornerShape(16.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .alpha(recAlpha)
                                .clip(CircleShape)
                                .background(RecRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "REC $timeFormatted",
                            color = RecRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Top Actions: Flash & Switch camera
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        IconButton(
                            onClick = { cameraCaptureManager.toggleFlash() },
                            modifier = Modifier
                                .size(36.dp)
                                .background(DarkSurface.copy(alpha = 0.8f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = if (isFlashOn) "Turn Flash Off" else "Turn Flash On",
                                tint = if (isFlashOn) CyanPrimary else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    IconButton(
                        onClick = { cameraCaptureManager.toggleCameraFacing(lifecycleOwner, previewView) },
                        enabled = !isRecording,
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (!isRecording) DarkSurface.copy(alpha = 0.8f) else DarkSurface.copy(alpha = 0.4f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Switch Camera Front or Rear",
                            tint = if (!isRecording) CyanPrimary else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Bottom gradient & controls over camera preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Photo capture button (hidden or disabled while recording video)
                if (!isRecording) {
                    IconButton(
                        onClick = onPhotoCaptured,
                        modifier = Modifier
                            .size(56.dp)
                            .background(DarkSurface.copy(alpha = 0.85f), CircleShape)
                            .border(2.dp, CyanPrimary, CircleShape)
                            .semantics { contentDescription = "Capture Photo" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Capture Photo",
                            tint = CyanPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(56.dp))
                }

                // Video Record / Stop button
                if (!isRecording) {
                    // Record button
                    Surface(
                        onClick = {
                            cameraCaptureManager.startVideoRecording(
                                onSuccess = onVideoRecorded,
                                onError = onRecordingError
                            )
                        },
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier
                            .size(68.dp)
                            .border(3.dp, RecRed, CircleShape)
                            .padding(4.dp)
                            .semantics { contentDescription = "Start Video Recording" }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(RecRed, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Record Video",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                } else {
                    // Stop button
                    Surface(
                        onClick = {
                            if (recordingDurationSeconds >= 1) {
                                cameraCaptureManager.stopVideoRecording()
                            } else {
                                onRecordingError("Please record for at least 2 seconds before stopping.")
                            }
                        },
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier
                            .size(68.dp)
                            .border(3.dp, RecRed, CircleShape)
                            .padding(4.dp)
                            .semantics { contentDescription = "Stop Video Recording" }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(RecRed, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Recording",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                // Lens flip quick button (kept on the right side for ergonomic thumb access)
                IconButton(
                    onClick = { cameraCaptureManager.toggleCameraFacing(lifecycleOwner, previewView) },
                    enabled = !isRecording,
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (!isRecording) DarkSurface.copy(alpha = 0.85f) else DarkSurface.copy(alpha = 0.4f),
                            CircleShape
                        )
                        .border(
                            1.dp,
                            if (!isRecording) TealAccent.copy(alpha = 0.6f) else Color.Transparent,
                            CircleShape
                        )
                        .semantics { contentDescription = "Flip Camera" }
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera Front or Rear",
                        tint = if (!isRecording) TealAccent else Color.Gray,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}
