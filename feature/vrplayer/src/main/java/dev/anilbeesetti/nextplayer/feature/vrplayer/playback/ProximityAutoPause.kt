package dev.anilbeesetti.nextplayer.feature.vrplayer.playback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Auto-pause ExoPlayer when the Quest is taken off the user's head. Quest
 * exposes the proximity sensor as `TYPE_PROXIMITY`; readings below the
 * sensor's max range mean "headset mounted on face".
 *
 * Tracks whether *we* paused playback (vs. e.g. sleep timer / manual) so
 * we only auto-resume on remount when our own pause caused it. The
 * "we paused it" flag is reset on every [start] so it cannot leak across
 * activity lifecycles.
 */
class ProximityAutoPause(
    context: Context,
    private val isPlaying: () -> Boolean,
    private val pausePlayer: () -> Unit,
    private val resumePlayer: () -> Unit,
) : SensorEventListener {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private var wasNear = true
    private var pausedByUs = false

    fun start() {
        // Reset baseline mỗi lần re-register: nếu user tháo headset trong
        // lúc app pause, lần resume tiếp theo phải coi như đang ở trạng
        // thái "near" để first reading "far" còn fire onUnmounted.
        wasNear = true
        // Important: clear the "we paused it" flag too. Without this,
        // a sequence like (off-head → onPause → onResume → user pauses
        // manually → off-head → on-head) would resume playback because
        // the flag stayed true across the activity lifecycle.
        pausedByUs = false
        sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        sm.unregisterListener(this)
    }

    /**
     * Clear the "we paused playback" flag. Other components that pause the
     * player (e.g. SleepTimer, manual togglePlayPause) must call this so we
     * don't auto-resume on remount and undo their pause.
     */
    fun clearPausedFlag() {
        pausedByUs = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val s = event.sensor ?: return
        val near = event.values[0] < (s.maximumRange.takeIf { it > 0f } ?: 5f)
        if (wasNear && !near) {
            // Headset just removed.
            if (isPlaying()) {
                pausedByUs = true
                pausePlayer()
            }
        } else if (!wasNear && near) {
            // Headset put back on.
            if (pausedByUs) {
                pausedByUs = false
                resumePlayer()
            }
        }
        wasNear = near
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
