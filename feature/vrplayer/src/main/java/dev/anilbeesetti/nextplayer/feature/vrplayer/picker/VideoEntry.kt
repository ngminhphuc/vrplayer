package dev.anilbeesetti.nextplayer.feature.vrplayer.picker

/** Lightweight row for the world-space picker — no Room, no Hilt. */
data class VideoEntry(
    val id: Long,
    val title: String,
    val path: String,
    val durationMs: Long,
    val sizeBytes: Long,
)
