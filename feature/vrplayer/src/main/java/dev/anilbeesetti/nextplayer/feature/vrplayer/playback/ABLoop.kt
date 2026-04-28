package dev.anilbeesetti.nextplayer.feature.vrplayer.playback

/**
 * A-B loop state for one playback session. Owners (XrActivity) poll
 * [check] from the per-second resume-writer loop; when current position
 * has crossed [b], they jump back to [a].
 *
 * Stateless w.r.t. ExoPlayer — caller passes positions in and decides
 * whether to issue a `seekTo`. Keeps this class trivial to unit-test.
 */
class ABLoop {
    private var a: Long = -1L
    private var b: Long = -1L

    fun setA(positionMs: Long) {
        a = positionMs
    }

    fun setB(positionMs: Long) {
        b = positionMs
    }

    fun clear() {
        a = -1L
        b = -1L
    }

    val isArmed: Boolean get() = a >= 0 && b > a
    val pointA: Long get() = a
    val pointB: Long get() = b

    /** If armed and `position` >= b, returns the seek target [a]; else null. */
    fun seekTargetIfPastB(position: Long): Long? =
        if (isArmed && position >= b) a else null
}
