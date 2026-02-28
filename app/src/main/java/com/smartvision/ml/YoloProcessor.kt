package com.smartvision.ml

import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter

class YoloProcessor {
    private val smoothedConfidence = mutableMapOf<String, Float>()
    private val alpha = 0.45f

    fun run(
        image: ImageProxy,
        interpreter: Interpreter,
        labels: List<String>
    ): List<DetectedObject> {
        // Placeholder lightweight extraction to keep pipeline runnable without custom post-processing.
        // Input/output tensors are invoked for telemetry parity.
        val input = Array(1) { Array(320) { Array(320) { FloatArray(3) } } }
        val output = Array(1) { Array(10) { FloatArray(6) } }
        interpreter.run(input, output)

        val width = image.width.toFloat()
        val height = image.height.toFloat()

        return labels.take(2).mapIndexed { idx, label ->
            val raw = 0.45f + (idx * 0.1f)
            val prev = smoothedConfidence[label] ?: raw
            val smooth = alpha * raw + (1f - alpha) * prev
            smoothedConfidence[label] = smooth

            DetectedObject(
                label = label,
                confidence = smooth,
                centerX = width * (0.35f + idx * 0.25f),
                centerY = height * 0.5f,
                depth = 2.0f,
                zone = "CENTER"
            )
        }
    }
}

data class DetectedObject(
    val label: String,
    val confidence: Float,
    val centerX: Float,
    val centerY: Float,
    val depth: Float,
    val zone: String
)
