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
 * Pauses on un-mount, resumes on re-mount only if we paused it ourselves.
 */
class ProximityAutoPause(
    context: Context,
    private val onUnmounted: () -> Unit,
    private val onRemounted: () -> Unit,
) : SensorEventListener {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private var wasNear = true

    fun start() {
        // Reset baseline mỗi lần re-register: nếu user tháo headset trong
        // lúc app pause, lần resume tiếp theo phải coi như đang ở trạng
        // thái "near" để first reading "far" còn fire onUnmounted.
        wasNear = true
        sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        sm.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val s = event.sensor ?: return
        val near = event.values[0] < (s.maximumRange.takeIf { it > 0f } ?: 5f)
        if (wasNear && !near) {
            onUnmounted()
        } else if (!wasNear && near) {
            onRemounted()
        }
        wasNear = near
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
