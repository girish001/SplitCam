package com.gireesh.camviewsplit.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.gireesh.camviewsplit.model.ContentScaleMode
import com.gireesh.camviewsplit.model.DualVideoCompositionConfig
import com.gireesh.camviewsplit.ui.theme.CyanPrimary
import com.gireesh.camviewsplit.ui.theme.DarkBackground
import com.gireesh.camviewsplit.ui.theme.DarkBorder
import com.gireesh.camviewsplit.ui.theme.DarkSurface
import com.gireesh.camviewsplit.ui.theme.DarkSurfaceElevated
import com.gireesh.camviewsplit.ui.theme.TealAccent
import com.gireesh.camviewsplit.ui.theme.TextPrimary
import com.gireesh.camviewsplit.ui.theme.TextSecondary
import com.gireesh.camviewsplit.util.CompositionTransformEngine
import com.gireesh.camviewsplit.util.ResolutionValidator
import com.gireesh.camviewsplit.util.SocialPlatform
import com.gireesh.camviewsplit.util.SourceVideoDimensions

data class ExportPreset(
    val label: String,
    val width: Int,
    val height: Int,
    val aspectRatioLabel: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportSettingsBottomSheet(
    topMediaUri: Uri?,
    bottomMediaUri: Uri?,
    config: DualVideoCompositionConfig,
    onConfigChange: (DualVideoCompositionConfig) -> Unit,
    onExportWithDimensions: (width: Int, height: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
    }

    var selectedPlatform by remember { mutableStateOf(SocialPlatform.INSTAGRAM) }

    val instagramPresets = listOf(
        ExportPreset("Reels (9:16)", 1080, 1920, "9:16 (Portrait)"),
        ExportPreset("Square (1:1)", 1080, 1080, "1:1 (Square)"),
        ExportPreset("Landscape (16:9)", 1920, 1080, "16:9 (Landscape)")
    )

    val facebookPresets = listOf(
        ExportPreset("Reels (9:16)", 1080, 1920, "9:16 (Portrait)"),
        ExportPreset("Square (1:1)", 1080, 1080, "1:1 (Square)"),
        ExportPreset("Landscape (16:9)", 1920, 1080, "16:9 (Landscape)")
    )

    val youtubePresets = listOf(
        ExportPreset("Shorts (9:16)", 1080, 1920, "9:16 (Portrait)"),
        ExportPreset("Landscape (16:9)", 1920, 1080, "16:9 (Landscape)"),
        ExportPreset("Square (1:1)", 1080, 1080, "1:1 (Square)")
    )

    var activePreset by remember { mutableStateOf(instagramPresets[0]) }
    var isCustomResolution by remember { mutableStateOf(false) }

    var customWidthStr by remember { mutableStateOf("1080") }
    var customHeightStr by remember { mutableStateOf("1920") }

    val rawWidth = if (isCustomResolution) customWidthStr.toIntOrNull() ?: 0 else activePreset.width
    val rawHeight = if (isCustomResolution) customHeightStr.toIntOrNull() ?: 0 else activePreset.height

    val validationResult = remember(rawWidth, rawHeight) {
        ResolutionValidator.validateAndNormalize(rawWidth, rawHeight)
    }

    var topDims by remember { mutableStateOf(SourceVideoDimensions()) }
    var bottomDims by remember { mutableStateOf(SourceVideoDimensions()) }

    LaunchedEffect(topMediaUri) {
        topDims = CompositionTransformEngine.retrieveMediaDimensionsAsync(context, topMediaUri)
    }

    LaunchedEffect(bottomMediaUri) {
        bottomDims = CompositionTransformEngine.retrieveMediaDimensionsAsync(context, bottomMediaUri)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkBackground,
        scrimColor = Color.Black.copy(alpha = 0.7f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            // Header Title & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Video Size & Export Settings",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Settings",
                        tint = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Platform Filter Selection
            Text(text = "Target Platform Presets:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SocialPlatform.entries.forEach { platform ->
                    val isSelected = selectedPlatform == platform
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) CyanPrimary.copy(alpha = 0.2f) else DarkSurfaceElevated)
                            .border(1.dp, if (isSelected) CyanPrimary else DarkBorder, RoundedCornerShape(12.dp))
                            .clickable { selectedPlatform = platform }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = platform.displayName,
                            color = if (isSelected) CyanPrimary else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Preset Resolution Chips
            val availablePresets = when (selectedPlatform) {
                SocialPlatform.YOUTUBE -> youtubePresets
                SocialPlatform.FACEBOOK -> facebookPresets
                else -> instagramPresets
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = preset.label,
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                // Custom Resolution Chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isCustomResolution) TealAccent else DarkSurfaceElevated)
                        .border(1.dp, if (isCustomResolution) TealAccent else DarkBorder, RoundedCornerShape(10.dp))
                        .clickable { isCustomResolution = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Custom Dimensions",
                        color = if (isCustomResolution) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = if (isCustomResolution) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }

            // Custom Resolution Input Fields
            AnimatedVisibility(visible = isCustomResolution) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customWidthStr,
                            onValueChange = { customWidthStr = it.filter { c -> c.isDigit() } },
                            label = { Text("Width (px)", fontSize = 11.sp) },
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
                            label = { Text("Height (px)", fontSize = 11.sp) },
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

            Spacer(modifier = Modifier.height(14.dp))

            // Split Ratio Control Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Layout Split Ratio:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = "${(config.splitRatio * 100).toInt()} / ${(100 - config.splitRatio * 100).toInt()}",
                    color = CyanPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Slider(
                value = config.splitRatio,
                onValueChange = { onConfigChange(config.copy(splitRatio = it)) },
                valueRange = 0.2f..0.8f,
                colors = SliderDefaults.colors(
                    thumbColor = CyanPrimary,
                    activeTrackColor = CyanPrimary,
                    inactiveTrackColor = DarkBorder
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Real Output Framing Preview
            Text(text = "Export Framing Preview:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(Color.Black, RoundedCornerShape(12.dp))
                    .border(1.dp, TealAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (validationResult.isValid) {
                    val rawAspect = validationResult.width.toFloat() / validationResult.height.coerceAtLeast(1).toFloat()
                    val safeAspect = if (rawAspect.isFinite() && rawAspect > 0f) rawAspect.coerceIn(0.2f, 5.0f) else 0.5625f

                    BoxWithConstraints(
                        modifier = Modifier
                            .aspectRatio(safeAspect, matchHeightConstraintsFirst = true)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceElevated)
                            .border(1.dp, CyanPrimary, RoundedCornerShape(8.dp))
                    ) {
                        val previewWidthPx = with(LocalDensity.current) { constraints.maxWidth.toFloat() }
                        val previewHeightPx = with(LocalDensity.current) { constraints.maxHeight.toFloat() }

                        val topTransform = CompositionTransformEngine.calculateTransform(
                            sectionState = config.topState,
                            sourceDimensions = topDims,
                            viewportWidthPx = previewWidthPx,
                            viewportHeightPx = previewHeightPx * config.splitRatio,
                            interactiveViewportWidthPx = 1080f,
                            interactiveViewportHeightPx = 1920f * config.splitRatio
                        )

                        val bottomTransform = CompositionTransformEngine.calculateTransform(
                            sectionState = config.bottomState,
                            sourceDimensions = bottomDims,
                            viewportWidthPx = previewWidthPx,
                            viewportHeightPx = previewHeightPx * (1.0f - config.splitRatio),
                            interactiveViewportWidthPx = 1080f,
                            interactiveViewportHeightPx = 1920f * (1.0f - config.splitRatio)
                        )

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top Preview Section
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(config.splitRatio.coerceIn(0.15f, 0.85f))
                                    .background(DarkSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                if (topMediaUri != null && topDims.isValid) {
                                    val topRequest = remember(topMediaUri) {
                                        ImageRequest.Builder(context)
                                            .data(topMediaUri)
                                            .videoFrameMillis(1000)
                                            .crossfade(true)
                                            .build()
                                    }
                                    SubcomposeAsyncImage(
                                        model = topRequest,
                                        imageLoader = imageLoader,
                                        contentDescription = "Top Preview",
                                        contentScale = ContentScale.FillBounds,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer(
                                                scaleX = topTransform.scaleX,
                                                scaleY = topTransform.scaleY,
                                                translationX = topTransform.composePanX,
                                                translationY = topTransform.composePanY
                                            )
                                    )
                                } else {
                                    Text(
                                        text = "TOP [${if (config.topState.contentScaleMode == ContentScaleMode.FILL) "FILL" else "FIT"}]",
                                        color = CyanPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (config.showDivider) {
                                HorizontalDivider(thickness = 2.dp, color = CyanPrimary)
                            }

                            // Bottom Preview Section
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight((1.0f - config.splitRatio).coerceIn(0.15f, 0.85f))
                                    .background(DarkSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                if (bottomMediaUri != null && bottomDims.isValid) {
                                    val bottomRequest = remember(bottomMediaUri) {
                                        ImageRequest.Builder(context)
                                            .data(bottomMediaUri)
                                            .videoFrameMillis(1000)
                                            .crossfade(true)
                                            .build()
                                    }
                                    SubcomposeAsyncImage(
                                        model = bottomRequest,
                                        imageLoader = imageLoader,
                                        contentDescription = "Bottom Preview",
                                        contentScale = ContentScale.FillBounds,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer(
                                                scaleX = bottomTransform.scaleX,
                                                scaleY = bottomTransform.scaleY,
                                                translationX = bottomTransform.composePanX,
                                                translationY = bottomTransform.composePanY
                                            )
                                    )
                                } else {
                                    Text(
                                        text = "BOTTOM [${if (config.bottomState.contentScaleMode == ContentScaleMode.FILL) "FILL" else "FIT"}]",
                                        color = TealAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Enter valid dimensions above to preview export",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Export Button
            Button(
                onClick = {
                    if (!validationResult.isValid) {
                        Toast.makeText(context, validationResult.errorMessage ?: "Invalid resolution dimensions", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    onExportWithDimensions(validationResult.width, validationResult.height)
                    onDismiss()
                },
                enabled = validationResult.isValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(imageVector = Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Export Composition (${validationResult.width}×${validationResult.height})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
