package dev.anilbeesetti.nextplayer.feature.vrplayer.picker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Multi-bookmark per file. Stored as a JSON map keyed by file path /
 * URI string. Each bookmark has a position (ms) and an optional label.
 * Plain SharedPreferences — bookmarks are not sensitive.
 */
class BookmarkStore(context: Context) {

    data class Bookmark(val positionMs: Long, val label: String)

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun list(key: String): List<Bookmark> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Bookmark(o.getLong("p"), o.optString("l", ""))
        }.sortedBy { it.positionMs }
    }

    fun add(key: String, positionMs: Long, label: String = "") {
        val current = list(key).toMutableList()
        current.add(Bookmark(positionMs, label))
        write(key, current)
    }

    fun remove(key: String, positionMs: Long) {
        val current = list(key).filter { it.positionMs != positionMs }
        write(key, current)
    }

    fun clear(key: String) {
        prefs.edit().remove(key).apply()
    }

    private fun write(key: String, list: List<Bookmark>) {
        val arr = JSONArray()
        list.sortedBy { it.positionMs }.forEach { b ->
            arr.put(JSONObject(mapOf("p" to b.positionMs, "l" to b.label)))
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    companion object {
        private const val FILE = "vrplayer_bookmarks"
    }
}
