package com.smartvision.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.cos

class IMUManager(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    @Volatile
    private var pitchRad: Float = 0f

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun currentPitchRad(): Float = pitchRad

    fun correctDepth(rawDepth: Float): Float {
        val corrected = rawDepth * cos(pitchRad)
        return corrected.coerceAtLeast(0.05f)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        pitchRad = atan2(-x, kotlin.math.sqrt(y * y + z * z))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
