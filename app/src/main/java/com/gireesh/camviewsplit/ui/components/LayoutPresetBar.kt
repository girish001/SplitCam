package com.gireesh.camviewsplit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.gireesh.camviewsplit.model.SplitLayoutPreset
import com.gireesh.camviewsplit.ui.theme.CyanPrimary
import com.gireesh.camviewsplit.ui.theme.DarkBorder
import com.gireesh.camviewsplit.ui.theme.DarkSurface
import com.gireesh.camviewsplit.ui.theme.DarkSurfaceElevated
import com.gireesh.camviewsplit.ui.theme.TealAccent

@Composable
fun LayoutPresetBar(
    config: DualVideoCompositionConfig,
    onConfigChange: (DualVideoCompositionConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Preset Chips (50/50, 40/60, 60/40, Custom)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SplitLayoutPreset.entries.forEach { preset ->
                    val isSelected = config.activePreset == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) CyanPrimary else DarkSurfaceElevated)
                            .border(
                                1.dp,
                                if (isSelected) CyanPrimary else DarkBorder,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                val newRatio = if (preset == SplitLayoutPreset.CUSTOM) config.splitRatio else preset.ratio
                                onConfigChange(
                                    config.copy(
                                        activePreset = preset,
                                        splitRatio = newRatio
                                    )
                                )
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = preset.label,
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Quick Actions: Swap Top/Bottom & Reset Adjustments
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onConfigChange(config.swapTopAndBottom()) },
                    modifier = Modifier
                        .size(32.dp)
                        .background(DarkSurfaceElevated, CircleShape)
                        .border(1.dp, DarkBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Swap Top and Bottom Videos",
                        tint = TealAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = { onConfigChange(config.resetAllAdjustments()) },
                    modifier = Modifier
                        .size(32.dp)
                        .background(DarkSurfaceElevated, CircleShape)
                        .border(1.dp, DarkBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset All Adjustments",
                        tint = Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Custom Split Ratio Slider when Custom preset is selected
        AnimatedVisibility(
            visible = config.activePreset == SplitLayoutPreset.CUSTOM,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Top ${ (config.splitRatio * 100).toInt() }%",
                    color = CyanPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(54.dp)
                )

                Slider(
                    value = config.splitRatio,
                    onValueChange = { newRatio ->
                        onConfigChange(
                            config.copy(
                                splitRatio = newRatio,
                                activePreset = SplitLayoutPreset.CUSTOM
                            )
                        )
                    },
                    valueRange = 0.15f..0.85f,
                    colors = SliderDefaults.colors(
                        thumbColor = CyanPrimary,
                        activeTrackColor = CyanPrimary,
                        inactiveTrackColor = DarkBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                )

                Text(
                    text = "Btm ${ ((1f - config.splitRatio) * 100).toInt() }%",
                    color = TealAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(54.dp)
                )
            }
        }
    }
}
