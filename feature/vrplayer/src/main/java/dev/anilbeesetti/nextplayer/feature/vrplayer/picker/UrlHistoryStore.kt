package dev.anilbeesetti.nextplayer.feature.vrplayer.picker

import android.content.Context

/**
 * Stores user-typed streaming URLs (HTTP/HTTPS, HLS, DASH, RTSP) so the
 * Network tab of the picker can offer one-tap re-play. Bounded to
 * [MAX_ENTRIES] most-recent entries — beyond that we evict from the tail.
 */
class UrlHistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("vrplayer_urls", Context.MODE_PRIVATE)

    fun list(): List<String> {
        val joined = prefs.getString(KEY, "") ?: ""
        return if (joined.isEmpty()) emptyList() else joined.split(SEP)
    }

    fun push(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        val current = list().toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        while (current.size > MAX_ENTRIES) current.removeAt(current.size - 1)
        prefs.edit().putString(KEY, current.joinToString(SEP)).apply()
    }

    fun remove(url: String) {
        val current = list().toMutableList()
        if (current.remove(url)) {
            prefs.edit().putString(KEY, current.joinToString(SEP)).apply()
        }
    }

    companion object {
        private const val KEY = "history"
        private const val SEP = "\u0001"
        private const val MAX_ENTRIES = 32
    }
}
