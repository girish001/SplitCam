package com.gireesh.camviewsplit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gireesh.camviewsplit.model.DualVideoCompositionConfig
import com.gireesh.camviewsplit.ui.theme.CyanPrimary
import com.gireesh.camviewsplit.ui.theme.DarkBorder
import com.gireesh.camviewsplit.ui.theme.DarkSurface
import com.gireesh.camviewsplit.ui.theme.DarkSurfaceElevated
import com.gireesh.camviewsplit.ui.theme.RecRed
import com.gireesh.camviewsplit.ui.theme.TealAccent

@Composable
fun AudioControlPanel(
    config: DualVideoCompositionConfig,
    onConfigChange: (DualVideoCompositionConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val isTopMuted = config.topState.isMuted
    val isBottomMuted = config.bottomState.isMuted
    val isMicOn = config.isMicAudioEnabled
    val isAllMuted = isTopMuted && isBottomMuted && !isMicOn

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Audio Toggle Chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (!isTopMuted) CyanPrimary.copy(alpha = 0.2f) else DarkSurfaceElevated)
                    .border(
                        1.dp,
                        if (!isTopMuted) CyanPrimary else DarkBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable {
                        onConfigChange(
                            config.copy(
                                topState = config.topState.copy(isMuted = !isTopMuted)
                            )
                        )
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (!isTopMuted) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Top Audio Toggle",
                        tint = if (!isTopMuted) CyanPrimary else Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (!isTopMuted) "Top Audio ON" else "Top Audio Muted",
                        color = if (!isTopMuted) CyanPrimary else Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Bottom Audio Toggle Chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (!isBottomMuted) TealAccent.copy(alpha = 0.2f) else DarkSurfaceElevated)
                    .border(
                        1.dp,
                        if (!isBottomMuted) TealAccent else DarkBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable {
                        onConfigChange(
                            config.copy(
                                bottomState = config.bottomState.copy(isMuted = !isBottomMuted)
                            )
                        )
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (!isBottomMuted) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Bottom Audio Toggle",
                        tint = if (!isBottomMuted) TealAccent else Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (!isBottomMuted) "Btm Audio ON" else "Btm Audio Muted",
                        color = if (!isBottomMuted) TealAccent else Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Microphone Audio Chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isMicOn) Color.Green.copy(alpha = 0.15f) else DarkSurfaceElevated)
                    .border(
                        1.dp,
                        if (isMicOn) Color.Green else DarkBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable {
                        onConfigChange(config.copy(isMicAudioEnabled = !isMicOn))
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isMicOn) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = "Mic Toggle",
                        tint = if (isMicOn) Color.Green else Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isMicOn) "Mic ON" else "Mic OFF",
                        color = if (isMicOn) Color.Green else Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Mute All Button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (isAllMuted) RecRed.copy(alpha = 0.25f) else DarkSurfaceElevated)
                .border(
                    1.dp,
                    if (isAllMuted) RecRed else DarkBorder,
                    RoundedCornerShape(14.dp)
                )
                .clickable {
                    val targetMute = !isAllMuted
                    onConfigChange(
                        config.copy(
                            topState = config.topState.copy(isMuted = targetMute),
                            bottomState = config.bottomState.copy(isMuted = targetMute),
                            isMicAudioEnabled = !targetMute
                        )
                    )
                }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isAllMuted) "ALL MUTED" else "Mute All",
                color = if (isAllMuted) RecRed else Color.LightGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
