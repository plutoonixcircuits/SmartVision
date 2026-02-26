package com.smartvision.ml

import android.content.Context
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ModelManager(private val context: Context) {

    private val gpuDelegate: Delegate?
    val yoloInterpreter: Interpreter
    val hazardInterpreter: Interpreter
    val depthInterpreter: Interpreter

    init {
        val yoloStart = SystemClock.elapsedRealtime()
        gpuDelegate = try {
            GpuDelegate()
        } catch (_: Exception) {
            null
        }

        yoloInterpreter = createInterpreter("yolov8n_float16.tflite", useGpu = true)
        hazardInterpreter = createInterpreter("best_float16.tflite", useGpu = false)
        depthInterpreter = createInterpreter("midas_small.tflite", useGpu = false)

        Log.i(TAG, "Models initialized in ${SystemClock.elapsedRealtime() - yoloStart} ms")
    }

    private fun createInterpreter(assetName: String, useGpu: Boolean): Interpreter {
        val opts = Interpreter.Options().apply {
            setNumThreads(4)
            if (useGpu && gpuDelegate != null) {
                addDelegate(gpuDelegate)
                Log.i(TAG, "GPU delegate enabled for $assetName")
            }
        }
        return Interpreter(loadModel(assetName), opts)
    }

    private fun loadModel(assetName: String): ByteBuffer {
        val bytes = context.assets.open(assetName).use { it.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size).apply {
            order(ByteOrder.nativeOrder())
            put(bytes)
            rewind()
        }
    }

    fun close() {
        yoloInterpreter.close()
        hazardInterpreter.close()
        depthInterpreter.close()
        gpuDelegate?.close()
    }

    companion object {
        private const val TAG = "ModelManager"
    }
}
