package com.gireesh.camviewsplit.model

import android.net.Uri

data class LocalVideoItem(
    val uri: Uri,
    val displayName: String,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L
)
