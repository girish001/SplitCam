package com.gireesh.camviewsplit.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gireesh.camviewsplit.ui.theme.CyanPrimary
import com.gireesh.camviewsplit.ui.theme.DarkBorder
import com.gireesh.camviewsplit.ui.theme.DarkSurface
import com.gireesh.camviewsplit.ui.theme.DarkSurfaceElevated
import com.gireesh.camviewsplit.ui.theme.TealAccent
import com.gireesh.camviewsplit.ui.theme.TextPrimary
import com.gireesh.camviewsplit.ui.theme.TextSecondary
import com.gireesh.camviewsplit.util.ResolutionValidator
import com.gireesh.camviewsplit.util.SocialPlatform
import com.gireesh.camviewsplit.util.SocialShareHelper

data class SocialPreset(
    val label: String,
    val width: Int,
    val height: Int,
    val aspectRatioLabel: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SocialMediaPanel(
    exportedVideoUri: Uri?,
    onExportWithDimensions: (width: Int, height: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var selectedPlatforms by remember { mutableStateOf(setOf(SocialPlatform.INSTAGRAM)) }

    val instagramPresets = listOf(
        SocialPreset("Reels (9:16)", 1080, 1920, "9:16 (Portrait)"),
        SocialPreset("Square (1:1)", 1080, 1080, "1:1 (Square)"),
        SocialPreset("Landscape (16:9)", 1920, 1080, "16:9 (Landscape)")
    )

    val facebookPresets = listOf(
        SocialPreset("Reels (9:16)", 1080, 1920, "9:16 (Portrait)"),
        SocialPreset("Square (1:1)", 1080, 1080, "1:1 (Square)"),
        SocialPreset("Landscape (16:9)", 1920, 1080, "16:9 (Landscape)")
    )

    val youtubePresets = listOf(
        SocialPreset("Shorts (9:16)", 1080, 1920, "9:16 (Portrait)"),
        SocialPreset("Landscape (16:9)", 1920, 1080, "16:9 (Landscape)"),
        SocialPreset("Square (1:1)", 1080, 1080, "1:1 (Square)")
    )

    var activePreset by remember { mutableStateOf(instagramPresets[0]) }
    var isCustomResolution by remember { mutableStateOf(false) }

    var customWidthStr by remember { mutableStateOf("1080") }
    var customHeightStr by remember { mutableStateOf("1920") }

    val rawWidth = if (isCustomResolution) customWidthStr.toIntOrNull() ?: 1080 else activePreset.width
    val rawHeight = if (isCustomResolution) customHeightStr.toIntOrNull() ?: 1920 else activePreset.height

    val validationResult = remember(rawWidth, rawHeight) {
        ResolutionValidator.validateAndNormalize(rawWidth, rawHeight)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Social Media Export & Sharing",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "${validationResult.width}×${validationResult.height} [${validationResult.aspectRatioLabel}]",
                color = if (validationResult.isValid) TealAccent else Color.Red,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Target Platforms Row
        Text(text = "Target Platforms:", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SocialPlatform.entries.forEach { platform ->
                val isSelected = selectedPlatforms.contains(platform)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) CyanPrimary.copy(alpha = 0.2f) else DarkSurfaceElevated)
                        .border(1.dp, if (isSelected) CyanPrimary else DarkBorder, RoundedCornerShape(12.dp))
                        .clickable {
                            selectedPlatforms = if (isSelected) {
                                if (selectedPlatforms.size > 1) selectedPlatforms - platform else selectedPlatforms
                            } else {
                                selectedPlatforms + platform
                            }
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSelected) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = platform.displayName,
                            color = if (isSelected) CyanPrimary else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Resolution Presets Chips
        Text(text = "Resolution Presets:", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))

        val availablePresets = when {
            selectedPlatforms.contains(SocialPlatform.YOUTUBE) -> youtubePresets
            selectedPlatforms.contains(SocialPlatform.FACEBOOK) -> facebookPresets
            else -> instagramPresets
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            availablePresets.forEach { preset ->
                val isSelected = !isCustomResolution && activePreset == preset
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) TealAccent else DarkSurfaceElevated)
                        .border(1.dp, if (isSelected) TealAccent else DarkBorder, RoundedCornerShape(10.dp))
                        .clickable {
                            isCustomResolution = false
                            activePreset = preset
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = preset.label,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }

            // Custom Option Chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isCustomResolution) TealAccent else DarkSurfaceElevated)
                    .border(1.dp, if (isCustomResolution) TealAccent else DarkBorder, RoundedCornerShape(10.dp))
                    .clickable { isCustomResolution = true }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "Custom Resolution",
                    color = if (isCustomResolution) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = if (isCustomResolution) FontWeight.Bold else FontWeight.Medium
                )
            }
        }

        // Custom Resolution Inputs
        AnimatedVisibility(visible = isCustomResolution) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customWidthStr,
                        onValueChange = { customWidthStr = it.filter { c -> c.isDigit() } },
                        label = { Text("Width (px)", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = DarkBorder,
                            focusedLabelColor = CyanPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Text(text = "×", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = customHeightStr,
                        onValueChange = { customHeightStr = it.filter { c -> c.isDigit() } },
                        label = { Text("Height (px)", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = DarkBorder,
                            focusedLabelColor = CyanPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!validationResult.isValid && validationResult.errorMessage != null) {
                    Text(
                        text = validationResult.errorMessage,
                        color = Color.Red,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Export & Platform Share Buttons Row
        Button(
            onClick = {
                if (!validationResult.isValid) {
                    Toast.makeText(context, validationResult.errorMessage ?: "Invalid resolution dimensions", Toast.LENGTH_LONG).show()
                    return@Button
                }
                onExportWithDimensions(validationResult.width, validationResult.height)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanPrimary,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Export ${validationResult.width}×${validationResult.height} (${validationResult.aspectRatioLabel})",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }

        // Sharing Actions for Each Selected Platform after export completes
        if (exportedVideoUri != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Share Exported Video:", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                selectedPlatforms.forEach { platform ->
                    Button(
                        onClick = {
                            SocialShareHelper.shareVideoToPlatform(context, exportedVideoUri, platform)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TealAccent,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Share to ${platform.displayName}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                // Generic Share Sheet Button
                Button(
                    onClick = {
                        SocialShareHelper.shareVideoGeneric(context, exportedVideoUri)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkSurfaceElevated,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "More Options...", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}
