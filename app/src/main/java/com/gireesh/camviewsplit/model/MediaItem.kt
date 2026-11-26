package com.gireesh.camviewsplit.model

import android.net.Uri

data class MediaItem(
    val id: String,
    val uri: Uri,
    val isVideo: Boolean,
    val displayName: String,
    val dateAdded: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val isImported: Boolean = false
)
