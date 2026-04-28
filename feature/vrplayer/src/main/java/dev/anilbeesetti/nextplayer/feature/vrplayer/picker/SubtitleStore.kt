package dev.anilbeesetti.nextplayer.feature.vrplayer.picker

import android.content.Context

/**
 * Per-file external subtitle association. Maps the video path/URI to a
 * sidecar subtitle URI (`.srt`/`.vtt`/`.ssa`/`.ass`/`.ttml`) the user
 * picked. Stored in plain SharedPreferences — paths are not secret and
 * we want them to survive reinstall via Quest's auto-backup.
 */
class SubtitleStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun get(videoKey: String): String? = prefs.getString(videoKey, null)?.takeIf { it.isNotBlank() }

    fun set(videoKey: String, subtitleUri: String?) {
        prefs.edit().apply {
            if (subtitleUri.isNullOrBlank()) remove(videoKey) else putString(videoKey, subtitleUri)
        }.apply()
    }

    fun clear(videoKey: String) {
        prefs.edit().remove(videoKey).apply()
    }

    companion object {
        private const val PREF = "vrplayer_subtitles"
    }
}
