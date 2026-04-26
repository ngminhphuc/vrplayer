package dev.anilbeesetti.nextplayer.feature.vrplayer.picker

import android.content.Context

/**
 * Per-file resume position. Backed by [android.content.SharedPreferences]
 * for now; will move to proto DataStore alongside the broader app preferences
 * migration in Stage 1 Sprint 3.
 */
class ResumeStore(context: Context) {

    private val prefs = context.getSharedPreferences("vrplayer_resume", Context.MODE_PRIVATE)

    fun load(path: String): Long = prefs.getLong(path, 0L)

    fun save(path: String, positionMs: Long) {
        prefs.edit().putLong(path, positionMs.coerceAtLeast(0L)).apply()
    }

    fun lastPlayedPath(): String? = prefs.getString(KEY_LAST, null)

    fun setLastPlayed(path: String) {
        prefs.edit().putString(KEY_LAST, path).apply()
    }

    companion object {
        private const val KEY_LAST = "_last_played"
    }
}
