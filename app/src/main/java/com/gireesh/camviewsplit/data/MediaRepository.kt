package com.gireesh.camviewsplit.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import com.gireesh.camviewsplit.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class MediaRepository(private val context: Context) {

    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems.asStateFlow()

    private val importedDir: File by lazy {
        File(context.filesDir, "imported_media").apply { if (!exists()) mkdirs() }
    }

    private val capturedIndexFile: File by lazy {
        File(context.filesDir, "captured_index.json")
    }

    suspend fun loadAllMedia() = withContext(Dispatchers.IO) {
        val captured = loadCapturedFromIndex()
        val imported = loadImportedFiles()
        val mediaStoreItems = queryCamViewMediaStoreItems()

        // Combine unique items by URI
        val map = linkedMapOf<String, MediaItem>()
        // Prioritize captured index
        captured.forEach { map[it.uri.toString()] = it }
        // Add MediaStore items
        mediaStoreItems.forEach { map[it.uri.toString()] = it }
        // Add imported items
        imported.forEach { map[it.uri.toString()] = it }

        val sorted = map.values.sortedByDescending { it.dateAdded }
        _mediaItems.value = sorted
    }

    suspend fun addCapturedMedia(uri: Uri, isVideo: Boolean, displayName: String): MediaItem =
        withContext(Dispatchers.IO) {
            var durationMs = 0L
            if (isVideo) {
                durationMs = retrieveVideoDuration(uri)
            }
            val item = MediaItem(
                id = uri.toString(),
                uri = uri,
                isVideo = isVideo,
                displayName = displayName,
                dateAdded = System.currentTimeMillis(),
                durationMs = durationMs,
                isImported = false
            )

            // Save to captured index
            val existing = loadCapturedFromIndex().toMutableList()
            existing.removeAll { it.uri.toString() == uri.toString() }
            existing.add(0, item)
            saveCapturedToIndex(existing)

            val current = _mediaItems.value.toMutableList()
            current.removeAll { it.uri.toString() == uri.toString() }
            current.add(0, item)
            _mediaItems.value = current
            item
        }

    suspend fun importMediaUris(uris: List<Uri>) = withContext(Dispatchers.IO) {
        val newItems = mutableListOf<MediaItem>()
        for (uri in uris) {
            try {
                val mimeType = context.contentResolver.getType(uri) ?: ""
                val isVideo = mimeType.startsWith("video") || uri.toString().contains("video", ignoreCase = true)
                val ext = if (isVideo) ".mp4" else ".jpg"
                val filename = "import_${System.currentTimeMillis()}_${(1000..9999).random()}$ext"
                val targetFile = File(importedDir, filename)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                if (targetFile.exists() && targetFile.length() > 0) {
                    val fileUri = Uri.fromFile(targetFile)
                    var duration = 0L
                    if (isVideo) {
                        duration = retrieveVideoDuration(fileUri)
                    }
                    val item = MediaItem(
                        id = fileUri.toString(),
                        uri = fileUri,
                        isVideo = isVideo,
                        displayName = filename,
                        dateAdded = System.currentTimeMillis(),
                        durationMs = duration,
                        isImported = true
                    )
                    newItems.add(item)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (newItems.isNotEmpty()) {
            val current = _mediaItems.value.toMutableList()
            current.addAll(0, newItems)
            _mediaItems.value = current.sortedByDescending { it.dateAdded }
        }
    }

    suspend fun deleteMediaItem(item: MediaItem) = withContext(Dispatchers.IO) {
        try {
            if (item.isImported) {
                val file = File(item.uri.path ?: "")
                if (file.exists()) {
                    file.delete()
                }
            } else {
                // Remove from captured index
                val captured = loadCapturedFromIndex().toMutableList()
                captured.removeAll { it.uri.toString() == item.uri.toString() }
                saveCapturedToIndex(captured)

                // Try deleting from MediaStore if applicable
                try {
                    context.contentResolver.delete(item.uri, null, null)
                } catch (_: Exception) {
                    // Ignored if not permitted by scoped storage
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val current = _mediaItems.value.toMutableList()
        current.removeAll { it.id == item.id || it.uri.toString() == item.uri.toString() }
        _mediaItems.value = current
    }

    private fun loadImportedFiles(): List<MediaItem> {
        val files = importedDir.listFiles() ?: return emptyList()
        val list = mutableListOf<MediaItem>()
        for (file in files) {
            if (file.isFile && file.length() > 0) {
                val name = file.name
                val isVideo = name.endsWith(".mp4", ignoreCase = true) ||
                        name.endsWith(".mkv", ignoreCase = true) ||
                        name.endsWith(".webm", ignoreCase = true)
                val uri = Uri.fromFile(file)
                val duration = if (isVideo) retrieveVideoDuration(uri) else 0L
                list.add(
                    MediaItem(
                        id = uri.toString(),
                        uri = uri,
                        isVideo = isVideo,
                        displayName = name,
                        dateAdded = file.lastModified(),
                        durationMs = duration,
                        isImported = true
                    )
                )
            }
        }
        return list
    }

    private fun loadCapturedFromIndex(): List<MediaItem> {
        if (!capturedIndexFile.exists()) return emptyList()
        return try {
            val content = capturedIndexFile.readText()
            val jsonArray = JSONArray(content)
            val list = mutableListOf<MediaItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val uriStr = obj.getString("uri")
                list.add(
                    MediaItem(
                        id = uriStr,
                        uri = Uri.parse(uriStr),
                        isVideo = obj.getBoolean("isVideo"),
                        displayName = obj.getString("displayName"),
                        dateAdded = obj.getLong("dateAdded"),
                        durationMs = obj.optLong("durationMs", 0L),
                        isImported = false
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveCapturedToIndex(items: List<MediaItem>) {
        try {
            val jsonArray = JSONArray()
            for (item in items) {
                val obj = JSONObject().apply {
                    put("uri", item.uri.toString())
                    put("isVideo", item.isVideo)
                    put("displayName", item.displayName)
                    put("dateAdded", item.dateAdded)
                    put("durationMs", item.durationMs)
                }
                jsonArray.put(obj)
            }
            capturedIndexFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun queryCamViewMediaStoreItems(): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        try {
            // Videos
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DURATION
            )
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("SplitCam_%", "CamViewSplit_%")

            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                selection,
                selectionArgs,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol)
                    val dateAdded = cursor.getLong(dateCol) * 1000
                    val duration = cursor.getLong(durCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    list.add(
                        MediaItem(
                            id = uri.toString(),
                            uri = uri,
                            isVideo = true,
                            displayName = name,
                            dateAdded = dateAdded,
                            durationMs = duration,
                            isImported = false
                        )
                    )
                }
            }

            // Photos
            val imageProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
            )
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol)
                    val dateAdded = cursor.getLong(dateCol) * 1000
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    list.add(
                        MediaItem(
                            id = uri.toString(),
                            uri = uri,
                            isVideo = false,
                            displayName = name,
                            dateAdded = dateAdded,
                            durationMs = 0L,
                            isImported = false
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Permission or MediaStore query error fallback
        }
        return list
    }

    private fun retrieveVideoDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            time?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }
}
