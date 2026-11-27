package com.gireesh.camviewsplit.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import com.gireesh.camviewsplit.model.LocalVideoItem
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

    private val localPlaylistFile: File by lazy {
        File(context.filesDir, "local_playlist.json")
    }

    suspend fun loadAllMedia() = withContext(Dispatchers.IO) {
        val captured = loadCapturedFromIndex()
        val imported = loadImportedFiles()
        val mediaStoreItems = queryCamViewMediaStoreItems()

        val map = linkedMapOf<String, MediaItem>()
        captured.forEach { map[it.uri.toString()] = it }
        mediaStoreItems.forEach { map[it.uri.toString()] = it }
        imported.forEach { map[it.uri.toString()] = it }

        val sorted = map.values.sortedByDescending { it.dateAdded }
        _mediaItems.value = sorted
    }

    suspend fun saveLocalPlaylist(playlist: List<LocalVideoItem>, currentIndex: Int) = withContext(Dispatchers.IO) {
        try {
            if (playlist.isEmpty()) {
                clearLocalPlaylist()
                return@withContext
            }
            val json = JSONObject()
            json.put("currentIndex", currentIndex)
            val array = JSONArray()
            playlist.forEach { item ->
                val obj = JSONObject()
                obj.put("uri", item.uri.toString())
                obj.put("displayName", item.displayName)
                obj.put("durationMs", item.durationMs)
                obj.put("sizeBytes", item.sizeBytes)
                array.put(obj)
            }
            json.put("playlist", array)
            localPlaylistFile.writeText(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun loadLocalPlaylist(): Pair<List<LocalVideoItem>, Int> = withContext(Dispatchers.IO) {
        if (!localPlaylistFile.exists()) return@withContext Pair(emptyList(), 0)
        val validItems = mutableListOf<LocalVideoItem>()
        var savedIdx = 0
        try {
            val text = localPlaylistFile.readText()
            val json = JSONObject(text)
            savedIdx = json.optInt("currentIndex", 0)
            val array = json.optJSONArray("playlist") ?: JSONArray()

            val persistedPermissions = context.contentResolver.persistedUriPermissions.map { it.uri.toString() }.toSet()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val uriStr = obj.optString("uri")
                val name = obj.optString("displayName", "Local Video")
                val dur = obj.optLong("durationMs", 0L)
                val size = obj.optLong("sizeBytes", 0L)

                if (uriStr.isNotEmpty()) {
                    val uri = Uri.parse(uriStr)
                    val isAccessible = if (uri.scheme == "file") {
                        File(uri.path ?: "").exists()
                    } else {
                        persistedPermissions.contains(uriStr) || try {
                            context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
                        } catch (_: Exception) { false }
                    }

                    if (isAccessible) {
                        validItems.add(LocalVideoItem(uri, name, dur, size))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Pair(validItems, savedIdx.coerceIn(0, (validItems.size - 1).coerceAtLeast(0)))
    }

    suspend fun clearLocalPlaylist() = withContext(Dispatchers.IO) {
        try {
            if (localPlaylistFile.exists()) localPlaylistFile.delete()
        } catch (_: Exception) {}
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
                val (originalName, detectedIsVideo) = resolveUriDetails(uri)
                val ext = if (detectedIsVideo) {
                    if (originalName.contains(".")) ".${originalName.substringAfterLast('.')}" else ".mp4"
                } else {
                    if (originalName.contains(".")) ".${originalName.substringAfterLast('.')}" else ".jpg"
                }
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
                    if (detectedIsVideo) {
                        duration = retrieveVideoDuration(fileUri)
                    }
                    val item = MediaItem(
                        id = fileUri.toString(),
                        uri = fileUri,
                        isVideo = detectedIsVideo,
                        displayName = originalName.ifEmpty { filename },
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
            _mediaItems.value = current
        }
    }

    suspend fun deleteMediaItem(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        var deleted = false
        val fileProviderAuthority = "${context.packageName}.fileprovider"

        try {
            val uri = item.uri
            when {
                uri.authority == fileProviderAuthority -> {
                    // SplitCam FileProvider URI: Resolve file inside allowed SplitCam Movies directories
                    val moviesDir = File(
                        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES),
                        "SplitCam"
                    )
                    val appMoviesDir = context.getExternalFilesDir("Movies")

                    val candidate1 = File(moviesDir, item.displayName)
                    val candidate2 = if (appMoviesDir != null) File(appMoviesDir, item.displayName) else null

                    val fileToDelete = when {
                        candidate1.exists() && candidate1.canonicalPath.startsWith(moviesDir.canonicalPath) -> candidate1
                        candidate2 != null && candidate2.exists() && candidate2.canonicalPath.startsWith(appMoviesDir!!.canonicalPath) -> candidate2
                        else -> null
                    }

                    if (fileToDelete != null) {
                        deleted = fileToDelete.delete()
                    }
                }
                item.isImported || uri.scheme == "file" -> {
                    val file = File(uri.path ?: "")
                    val allowedImportDir = importedDir.canonicalPath
                    if (file.exists() && file.canonicalPath.startsWith(allowedImportDir)) {
                        deleted = file.delete()
                    }
                }
                uri.scheme == "content" -> {
                    val rows = context.contentResolver.delete(uri, null, null)
                    deleted = rows > 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            deleted = false
        }

        // Always remove from captured index and in-memory list
        val captured = loadCapturedFromIndex().toMutableList()
        captured.removeAll { it.id == item.id }
        saveCapturedToIndex(captured)

        val current = _mediaItems.value.toMutableList()
        current.removeAll { it.id == item.id }
        _mediaItems.value = current

        deleted
    }

    fun parseCapturedMediaItem(obj: JSONObject): MediaItem? {
        return try {
            val uriStr = obj.optString("uri", "")
            if (uriStr.isEmpty()) return null
            val rawId = obj.optString("id", "")
            val id = if (rawId.isNotEmpty()) rawId else uriStr
            MediaItem(
                id = id,
                uri = Uri.parse(uriStr),
                isVideo = obj.optBoolean("isVideo", true),
                displayName = obj.optString("displayName", "SplitCam Video"),
                dateAdded = obj.optLong("dateAdded", System.currentTimeMillis()),
                durationMs = obj.optLong("durationMs", 0L),
                isImported = false
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun loadCapturedFromIndex(): List<MediaItem> {
        if (!capturedIndexFile.exists()) return emptyList()
        val list = mutableListOf<MediaItem>()
        try {
            val jsonText = capturedIndexFile.readText()
            val array = JSONArray(jsonText)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val item = parseCapturedMediaItem(obj)
                if (item != null) {
                    list.add(item)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun saveCapturedToIndex(items: List<MediaItem>) {
        try {
            val array = JSONArray()
            items.filter { !it.isImported }.forEach { item ->
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("uri", item.uri.toString())
                obj.put("isVideo", item.isVideo)
                obj.put("displayName", item.displayName)
                obj.put("dateAdded", item.dateAdded)
                obj.put("durationMs", item.durationMs)
                array.put(obj)
            }
            capturedIndexFile.writeText(array.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadImportedFiles(): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        val files = importedDir.listFiles() ?: return emptyList()
        for (file in files) {
            val isVideo = file.extension.lowercase() in listOf("mp4", "mkv", "webm", "3gp", "avi", "mov")
            val fileUri = Uri.fromFile(file)
            var duration = 0L
            if (isVideo) {
                duration = retrieveVideoDuration(fileUri)
            }
            list.add(
                MediaItem(
                    id = fileUri.toString(),
                    uri = fileUri,
                    isVideo = isVideo,
                    displayName = file.name,
                    dateAdded = file.lastModified(),
                    durationMs = duration,
                    isImported = true
                )
            )
        }
        return list
    }

    private fun queryCamViewMediaStoreItems(): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION
        )
        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%Movies/SplitCam%")

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val dateAdded = cursor.getLong(dateColumn) * 1000
                    val duration = cursor.getLong(durationColumn)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    list.add(
                        MediaItem(
                            id = contentUri.toString(),
                            uri = contentUri,
                            isVideo = true,
                            displayName = name ?: "SplitCam Video",
                            dateAdded = dateAdded,
                            durationMs = duration,
                            isImported = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun resolveUriDetails(uri: Uri): Pair<String, Boolean> {
        var name = "imported_${System.currentTimeMillis()}"
        var isVideo = true
        try {
            val mime = context.contentResolver.getType(uri)
            if (mime != null) {
                isVideo = mime.startsWith("video/")
            }
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (idx != -1) {
                        val displayName = cursor.getString(idx)
                        if (!displayName.isNullOrEmpty()) {
                            name = displayName
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return Pair(name, isVideo)
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
            try { retriever.release() } catch (_: Exception) {}
        }
    }
}
