package com.gireesh.camviewsplit.ui.components.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.gireesh.camviewsplit.ui.theme.CyanPrimary
import com.gireesh.camviewsplit.ui.theme.RecRed

@Composable
fun AdjustableSplitLayout(
    splitRatio: Float,
    onSplitRatioChange: (Float) -> Unit,
    isRecording: Boolean,
    isFullScreenBottom: Boolean,
    topContent: @Composable () -> Unit,
    bottomContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isFullScreenBottom) {
        Box(modifier = modifier.fillMaxSize()) {
            bottomContent()
        }
    } else {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val totalHeightPx = with(LocalDensity.current) { constraints.maxHeight.toFloat() }

            Column(modifier = Modifier.fillMaxSize()) {
                // TOP SECTION
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(splitRatio.coerceIn(0.20f, 0.80f))
                ) {
                    topContent()
                }

                // DRAGGABLE DIVIDER HANDLE (Tap to Reset to 50:50)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .background(if (isRecording) RecRed.copy(alpha = 0.3f) else CyanPrimary.copy(alpha = 0.2f))
                        .clickable { onSplitRatioChange(0.5f) }
                        .pointerInput(totalHeightPx) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                if (totalHeightPx > 0f) {
                                    val deltaRatio = dragAmount.y / totalHeightPx
                                    val newRatio = (splitRatio + deltaRatio).coerceIn(0.20f, 0.80f)
                                    onSplitRatioChange(newRatio)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    HorizontalDivider(
                        thickness = 3.dp,
                        color = if (isRecording) RecRed else CyanPrimary
                    )
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .fillMaxWidth(0.15f)
                            .background(if (isRecording) RecRed else CyanPrimary, CircleShape)
                    )
                }

                // BOTTOM SECTION
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight((1.0f - splitRatio).coerceIn(0.20f, 0.80f))
                ) {
                    bottomContent()
                }
            }
        }
    }
}
