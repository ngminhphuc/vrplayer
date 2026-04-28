package dev.anilbeesetti.nextplayer.feature.vrplayer.picker

import android.content.Context

/**
 * Global (not per-file) subtitle styling preferences. Plain
 * SharedPreferences — no need for encryption since these are
 * cosmetic settings.
 *
 * - Font size: text-line height in sp (24, 32, 40, 48).
 * - Vertical offset: meters added to the quad Y position. Negative
 *   values push the panel further down so it stops overlapping the
 *   cinema screen for large fonts.
 */
class SubtitlePrefsStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun fontSizeSp(): Int = prefs.getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
    fun setFontSizeSp(size: Int) {
        prefs.edit().putInt(KEY_FONT_SIZE, size.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)).apply()
    }

    /** Y offset in meters. 0 → quad sits at default height. */
    fun verticalOffset(): Float = prefs.getFloat(KEY_OFFSET, 0f)
    fun setVerticalOffset(offset: Float) {
        prefs.edit().putFloat(KEY_OFFSET, offset.coerceIn(MIN_OFFSET, MAX_OFFSET)).apply()
    }

    companion object {
        private const val PREF = "vrplayer_subtitle_prefs"
        private const val KEY_FONT_SIZE = "font_size_sp"
        private const val KEY_OFFSET = "vertical_offset_m"
        const val DEFAULT_FONT_SIZE = 32
        const val MIN_FONT_SIZE = 20
        const val MAX_FONT_SIZE = 56
        const val MIN_OFFSET = -0.6f
        const val MAX_OFFSET = 0.4f
    }
}
