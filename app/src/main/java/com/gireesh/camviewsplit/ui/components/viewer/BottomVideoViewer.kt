package com.gireesh.camviewsplit.ui.components.viewer

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gireesh.camviewsplit.model.BottomViewerSource
import com.gireesh.camviewsplit.model.LocalVideoItem
import com.gireesh.camviewsplit.ui.theme.DarkBorder
import com.gireesh.camviewsplit.ui.theme.DarkSurface

@Composable
fun BottomVideoViewer(
    activeSource: BottomViewerSource,
    onSourceChange: (BottomViewerSource) -> Unit,
    localVideoList: List<LocalVideoItem>,
    currentLocalIndex: Int,
    isFullScreen: Boolean,
    onLocalVideoListChange: (List<LocalVideoItem>) -> Unit,
    onLocalIndexChange: (Int) -> Unit,
    onToggleFullScreenClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Local Video Multi-Picker Launcher using OpenMultipleDocuments for persistent URI access
    val localVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                val newItems = uris.mapNotNull { uri ->
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {}

                    val fileName = resolveFileName(context, uri)
                    LocalVideoItem(uri = uri, displayName = fileName)
                }

                // Filter out duplicates already in playlist
                val uniqueItems = newItems.filter { newItem -> localVideoList.none { existing -> existing.uri == newItem.uri } }

                if (uniqueItems.isNotEmpty()) {
                    val updatedList = localVideoList + uniqueItems
                    onLocalVideoListChange(updatedList)
                    if (localVideoList.isEmpty()) {
                        onLocalIndexChange(0)
                    }
                    Toast.makeText(context, "Added ${uniqueItems.size} video(s) to playlist", Toast.LENGTH_SHORT).show()
                } else if (newItems.isNotEmpty()) {
                    Toast.makeText(context, "Selected video(s) already in playlist", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) {
        // Source Platform Selector Tabs
        SocialPlatformSelector(
            selectedSource = activeSource,
            onSourceSelected = onSourceChange,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Main Viewer Display (Social WebView vs Local Video Player)
        when (activeSource) {
            BottomViewerSource.INSTAGRAM,
            BottomViewerSource.FACEBOOK,
            BottomViewerSource.YOUTUBE -> {
                SocialWebView(
                    source = activeSource,
                    isFullScreen = isFullScreen,
                    onToggleFullScreenClick = onToggleFullScreenClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
            BottomViewerSource.LOCAL_STORAGE -> {
                LocalVideoPlayer(
                    videoList = localVideoList,
                    currentIndex = currentLocalIndex,
                    isFullScreen = isFullScreen,
                    onIndexChanged = onLocalIndexChange,
                    onSelectVideosClick = {
                        localVideoPickerLauncher.launch(arrayOf("video/*"))
                    },
                    onRemoveVideoClick = { removeIdx ->
                        val updated = localVideoList.toMutableList().apply { removeAt(removeIdx) }
                        onLocalVideoListChange(updated)
                        if (currentLocalIndex >= updated.size) {
                            onLocalIndexChange((updated.size - 1).coerceAtLeast(0))
                        }
                    },
                    onClearSelectionClick = {
                        onLocalVideoListChange(emptyList())
                        onLocalIndexChange(0)
                        Toast.makeText(context, "Cleared local video playlist", Toast.LENGTH_SHORT).show()
                    },
                    onToggleFullScreenClick = onToggleFullScreenClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun resolveFileName(context: android.content.Context, uri: Uri): String {
    var name = "Local_Video_${System.currentTimeMillis()}.mp4"
    try {
        val cursor = context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                if (idx != -1) {
                    val displayName = it.getString(idx)
                    if (!displayName.isNullOrBlank()) name = displayName
                }
            }
        }
    } catch (_: Exception) {}
    return name
}
