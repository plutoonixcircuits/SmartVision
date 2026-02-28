package com.smartvision.ml

import androidx.camera.core.ImageProxy
import com.smartvision.navigation.Kalman1D
import org.tensorflow.lite.Interpreter

class DepthProcessor(
    private val inputSize: Int = 256,
    private val outputSize: Int = 256
) {
    private val inputTensor = Array(1) { Array(inputSize) { Array(inputSize) { FloatArray(3) } } }
    private val outputTensor = Array(1) { Array(outputSize) { FloatArray(outputSize) } }
    private val depthKalman = Kalman1D(processNoise = 0.01f, measurementNoise = 0.2f)

    fun estimateDepthMeters(image: ImageProxy, interpreter: Interpreter): Float {
        fillInputTensorFromY(image)
        interpreter.run(inputTensor, outputTensor)

        var sum = 0f
        var count = 0
        for (y in outputSize / 3 until (2 * outputSize / 3)) {
            for (x in outputSize / 3 until (2 * outputSize / 3)) {
                sum += outputTensor[0][y][x]
                count++
            }
        }
        val meanDepthRaw = if (count == 0) 1.5f else (sum / count).coerceAtLeast(0.05f)
        val pseudoMeters = (1.0f / (meanDepthRaw + 0.001f)).coerceIn(0.2f, 4.5f)
        return depthKalman.update(pseudoMeters)
    }

    private fun fillInputTensorFromY(image: ImageProxy) {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val srcWidth = image.width
        val srcHeight = image.height

        for (y in 0 until inputSize) {
            val srcY = y * srcHeight / inputSize
            for (x in 0 until inputSize) {
                val srcX = x * srcWidth / inputSize
                val srcIndex = srcY * rowStride + srcX * pixelStride
                val yValue = (buffer.get(srcIndex).toInt() and 0xFF) / 255f
                inputTensor[0][y][x][0] = yValue
                inputTensor[0][y][x][1] = yValue
                inputTensor[0][y][x][2] = yValue
            }
        }
    }
}
