package com.smartvision.navigation

class Kalman1D(
    private val processNoise: Float,
    private val measurementNoise: Float
) {
    private var estimate = 0f
    private var errorCov = 1f
    private var initialized = false

    fun update(measurement: Float): Float {
        if (!initialized) {
            estimate = measurement
            initialized = true
        }
        errorCov += processNoise
        val gain = errorCov / (errorCov + measurementNoise)
        estimate += gain * (measurement - estimate)
        errorCov *= (1 - gain)
        return estimate
    }
}
