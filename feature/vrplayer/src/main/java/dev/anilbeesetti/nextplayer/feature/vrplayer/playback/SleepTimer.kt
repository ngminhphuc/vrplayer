package dev.anilbeesetti.nextplayer.feature.vrplayer.playback

import android.os.Handler
import android.os.Looper

/**
 * Posts a delayed action that pauses ExoPlayer after [minutes]. Single-shot;
 * cancellable. Lives on the main looper so it never races with the player
 * which is also accessed on the main thread.
 */
class SleepTimer(private val onFire: () -> Unit) {

    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    var armedMinutes: Int = 0
        private set

    fun arm(minutes: Int) {
        cancel()
        if (minutes <= 0) return
        armedMinutes = minutes
        pending = Runnable {
            onFire()
            armedMinutes = 0
            pending = null
        }.also { handler.postDelayed(it, minutes * 60_000L) }
    }

    fun cancel() {
        pending?.let(handler::removeCallbacks)
        pending = null
        armedMinutes = 0
    }
}
