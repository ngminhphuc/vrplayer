package dev.anilbeesetti.nextplayer.feature.vrplayer.picker

import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans local on-device storage for video files via [MediaStore]. SAF-based
 * folder picking arrives in Stage 1 Sprint 3 alongside the streaming-URL
 * input. For Sprint 2 we just list whatever the user has already given us
 * `READ_MEDIA_VIDEO` permission to see.
 */
object MediaStoreScanner {

    suspend fun scan(context: Context): List<VideoEntry> = withContext(Dispatchers.IO) {
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
        )
        val sort = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        val out = ArrayList<VideoEntry>()
        runCatching {
            context.contentResolver.query(uri, projection, null, null, sort)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val pathCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                while (c.moveToNext()) {
                    out += VideoEntry(
                        id = c.getLong(idCol),
                        title = c.getString(nameCol) ?: "(no name)",
                        path = c.getString(pathCol) ?: "",
                        durationMs = c.getLong(durCol),
                        sizeBytes = c.getLong(sizeCol),
                    )
                }
            }
        }.onFailure { /* permissions denied / scoped storage — empty list */ }
        out
    }
}
