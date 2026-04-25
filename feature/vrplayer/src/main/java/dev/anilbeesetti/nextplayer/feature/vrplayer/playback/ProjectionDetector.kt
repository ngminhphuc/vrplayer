package dev.anilbeesetti.nextplayer.feature.vrplayer.playback

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File

/**
 * Best-effort projection guessing for a given video source. Two passes:
 *
 * 1. Filename heuristics — `*_360`, `*_180`, `*_eq`, etc. Cheap and good
 *    enough for libraries where users name files explicitly.
 * 2. MP4 spherical metadata via [MediaExtractor]. Looks for the
 *    standardised `sv3d` / `st3d` boxes (`MediaFormat.KEY_SPATIAL_FORMAT`
 *    on API 33+, fall-back to extra `csd-...` / track features on older
 *    devices). Quest is API 32 so we currently rely on heuristics only,
 *    but the structure is here for when Meta enables 33+.
 *
 * Returns [ProjectionMode.OFF] when nothing matches — the caller defaults
 * to the cinema cylinder.
 */
enum class ProjectionMode(val raw: Int) {
    OFF(0),
    EQUIRECT_360(1),
    HEMISPHERE_180(2),
}

object ProjectionDetector {

    fun detect(source: String): ProjectionMode {
        val byName = guessByFilename(source)
        if (byName != ProjectionMode.OFF) return byName
        if (source.startsWith("http", ignoreCase = true)) return ProjectionMode.OFF
        return runCatching { guessByMetadata(source) }.getOrDefault(ProjectionMode.OFF)
    }

    private fun guessByFilename(path: String): ProjectionMode {
        val name = File(path).nameWithoutExtension.lowercase()
        return when {
            // Order matters — 180 must come before 360 because *_180 doesn't
            // imply *_360 but a file named "scene_360_eq" should still match.
            name.contains("_180") || name.endsWith("180") || name.contains("vr180") -> ProjectionMode.HEMISPHERE_180
            name.contains("_360") || name.endsWith("360") || name.contains("equirect") || name.contains("vr360") -> ProjectionMode.EQUIRECT_360
            else -> ProjectionMode.OFF
        }
    }

    private fun guessByMetadata(path: String): ProjectionMode {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(path)
        } catch (e: Exception) {
            extractor.release()
            return ProjectionMode.OFF
        }
        try {
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("video/")) continue
                // KEY_SPATIAL_FORMAT was added in API 33; on older devices we
                // currently can't read sv3d. Skip silently.
                runCatching {
                    val raw = format.getInteger("spatial-format")
                    if (raw == 1) return ProjectionMode.EQUIRECT_360
                }
            }
        } finally {
            extractor.release()
        }
        return ProjectionMode.OFF
    }
}
