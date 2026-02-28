package com.smartvision.ml

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate

class ModelManager(private val context: Context) : Closeable {

    data class RuntimeConfig(
        val usingGpuForYolo: Boolean,
        val cpuOnlyMode: Boolean,
        val yoloEveryNFrames: Int,
        val hazardEveryNFrames: Int,
        val depthEveryNFrames: Int,
        val threads: Int
    )

    private var gpuDelegate: GpuDelegate? = null
    val yoloInterpreter: Interpreter
    val hazardInterpreter: Interpreter
    val depthInterpreter: Interpreter
    val runtimeConfig: RuntimeConfig

    init {
        val started = SystemClock.elapsedRealtime()
        gpuDelegate = try {
            GpuDelegate()
        } catch (e: Exception) {
            Log.w(TAG, "GPU delegate unavailable, switching to CPU mode", e)
            null
        }

        val usingGpu = gpuDelegate != null
        runtimeConfig = if (usingGpu) {
            RuntimeConfig(
                usingGpuForYolo = true,
                cpuOnlyMode = false,
                yoloEveryNFrames = 1,
                hazardEveryNFrames = 1,
                depthEveryNFrames = 3,
                threads = 4
            )
        } else {
            RuntimeConfig(
                usingGpuForYolo = false,
                cpuOnlyMode = true,
                yoloEveryNFrames = 3,
                hazardEveryNFrames = 5,
                depthEveryNFrames = 5,
                threads = 4
            )
        }

        yoloInterpreter = createInterpreter(
            assetName = "yolov8n_float16.tflite",
            useGpu = runtimeConfig.usingGpuForYolo,
            numThreads = runtimeConfig.threads
        )
        hazardInterpreter = createInterpreter(
            assetName = "best_float16.tflite",
            useGpu = false,
            numThreads = runtimeConfig.threads
        )
        depthInterpreter = createInterpreter(
            assetName = "midas_small.tflite",
            useGpu = false,
            numThreads = runtimeConfig.threads
        )

        Log.i(
            TAG,
            "Model stack ready in ${SystemClock.elapsedRealtime() - started}ms, config=$runtimeConfig"
        )
    }

    private fun createInterpreter(assetName: String, useGpu: Boolean, numThreads: Int): Interpreter {
        val options = Interpreter.Options().apply {
            setNumThreads(numThreads)
            setUseXNNPACK(true)
            if (useGpu) {
                gpuDelegate?.let { addDelegate(it) }
            }
        }
        return Interpreter(loadModel(assetName), options)
    }

    private fun loadModel(assetName: String): ByteBuffer {
        val bytes = context.assets.open(assetName).use { it.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size).apply {
            order(ByteOrder.nativeOrder())
            put(bytes)
            rewind()
        }
    }

    override fun close() {
        yoloInterpreter.close()
        hazardInterpreter.close()
        depthInterpreter.close()
        gpuDelegate?.close()
        gpuDelegate = null
    }

    companion object {
        private const val TAG = "ModelManager"
    }
}
