package com.smartvision.camera

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.smartvision.ml.DepthProcessor
import com.smartvision.ml.ModelManager
import com.smartvision.ml.YoloProcessor
import com.smartvision.navigation.Kalman1D
import com.smartvision.navigation.NavigationEngine
import com.smartvision.navigation.SceneMemory
import com.smartvision.navigation.SpatialMapper
import com.smartvision.sensors.IMUManager
import com.smartvision.speech.TTSManager
import com.smartvision.tracking.ObjectTracker
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

class CameraAnalyzer(
    private val modelManager: ModelManager,
    private val objectTracker: ObjectTracker,
    private val spatialMapper: SpatialMapper,
    private val navigationEngine: NavigationEngine,
    private val sceneMemory: SceneMemory,
    private val imuManager: IMUManager,
    private val ttsManager: TTSManager,
    private val onNavigationUpdate: (command: String, fps: Float) -> Unit,
    private val analyzerExecutor: Executor
) : ImageAnalysis.Analyzer {

    private val yolo = YoloProcessor()
    private val depthProcessor = DepthProcessor()
    private val frameCounter = AtomicInteger(0)
    private var lastFpsTimestamp = SystemClock.elapsedRealtime()
    private var fps = 0f
    private var depthInterval = 3
    private var lastDepth = 2.0f

    private val xKalman = Kalman1D(0.02f, 0.1f)
    private val yKalman = Kalman1D(0.02f, 0.1f)

    fun buildImageAnalysis(): ImageAnalysis {
        return ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(analyzerExecutor, this) }
    }

    override fun analyze(image: ImageProxy) {
        try {
            val count = frameCounter.incrementAndGet()
            computeFpsAndDepthInterval(count)

            val yoloObjects = yolo.run(image, modelManager.yoloInterpreter, listOf("person", "chair", "wall"))
            val hazardObjects = yolo.run(image, modelManager.hazardInterpreter, listOf("pothole", "staircase"))

            if (count % depthInterval == 0) {
                lastDepth = depthProcessor.estimateDepthMeters(image, modelManager.depthInterpreter)
            }
            val correctedDepth = imuManager.correctDepth(lastDepth)

            val merged = (yoloObjects + hazardObjects).map { det ->
                val smoothX = xKalman.update(det.centerX)
                val smoothY = yKalman.update(det.centerY)
                det.copy(
                    centerX = smoothX,
                    centerY = smoothY,
                    depth = correctedDepth,
                    zone = spatialMapper.mapZone(smoothX, image.width)
                )
            }

            val tracked = objectTracker.update(merged)
            val command = navigationEngine.buildCommand(tracked)
            val top = tracked.minByOrNull { it.depth }

            if (top != null && sceneMemory.shouldSpeak(command, top.id, top.depth)) {
                ttsManager.speak(command)
            }

            onNavigationUpdate(command, fps)
        } finally {
            image.close()
        }
    }

    private fun computeFpsAndDepthInterval(frameCount: Int) {
        val now = SystemClock.elapsedRealtime()
        val delta = now - lastFpsTimestamp
        if (delta >= 1000) {
            fps = frameCount * 1000f / (now - (now - delta))
            frameCounter.set(0)
            lastFpsTimestamp = now
            depthInterval = when {
                fps < 14f -> 5
                fps > 20f -> 2
                else -> 3
            }
        }
    }
}

