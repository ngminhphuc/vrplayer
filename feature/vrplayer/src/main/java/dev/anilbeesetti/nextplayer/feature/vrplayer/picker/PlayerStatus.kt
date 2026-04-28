package dev.anilbeesetti.nextplayer.feature.vrplayer.picker

/**
 * High-level player status surfaced to the picker UI. Mapped from
 * ExoPlayer's `Player.Listener` (state + onPlayerError) by [XrActivity].
 *
 * Intentionally narrower than `Player.State` so the UI doesn't need
 * to special-case STATE_IDLE vs STATE_ENDED, and so it can include
 * an error string for display.
 */
sealed interface PlayerStatus {
    object Idle : PlayerStatus
    object Buffering : PlayerStatus
    object Playing : PlayerStatus
    object Ended : PlayerStatus
    data class Error(val message: String) : PlayerStatus
}
