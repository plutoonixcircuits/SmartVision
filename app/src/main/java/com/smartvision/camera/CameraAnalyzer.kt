package com.smartvision.camera

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.smartvision.ml.DetectedObject
import com.smartvision.ml.DepthProcessor
import com.smartvision.ml.ModelManager
import com.smartvision.ml.YoloProcessor
import com.smartvision.navigation.NavigationEngine
import com.smartvision.navigation.SceneMemory
import com.smartvision.navigation.SpatialMapper
import com.smartvision.sensors.IMUManager
import com.smartvision.speech.TTSManager
import com.smartvision.tracking.ObjectTracker
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class CameraAnalyzer(
    private val modelManager: ModelManager,
    private val objectTracker: ObjectTracker,
    private val spatialMapper: SpatialMapper,
    private val navigationEngine: NavigationEngine,
    private val sceneMemory: SceneMemory,
    private val imuManager: IMUManager,
    private val ttsManager: TTSManager,
    private val onNavigationUpdate: (command: String, fps: Float, cpuMode: Boolean) -> Unit,
    private val analyzerExecutor: Executor,
    private val inferenceExecutor: ExecutorService
) : ImageAnalysis.Analyzer {

    private val yoloProcessor = YoloProcessor()
    private val hazardProcessor = YoloProcessor()
    private val depthProcessor = DepthProcessor()

    private val frameCounter = AtomicInteger(0)
    private val busy = AtomicBoolean(false)
    private var fpsWindowStart = SystemClock.elapsedRealtime()
    private var fps = 0f

    private var latestYolo = emptyList<DetectedObject>()
    private var latestHazards = emptyList<DetectedObject>()
    private var latestDepthMeters = 2.5f

    private var depthInterval = modelManager.runtimeConfig.depthEveryNFrames

    fun buildImageAnalysis(): ImageAnalysis {
        return ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(analyzerExecutor, this) }
    }

    override fun analyze(image: ImageProxy) {
        if (!busy.compareAndSet(false, true)) {
            image.close()
            return
        }

        inferenceExecutor.execute {
            try {
                processFrame(image)
            } catch (_: Throwable) {
                // Keep analyzer alive under all failures.
            } finally {
                image.close()
                busy.set(false)
            }
        }
    }

    private fun processFrame(image: ImageProxy) {
        val frameNo = frameCounter.incrementAndGet()
        updateFpsAndIntervals(frameNo)

        val config = modelManager.runtimeConfig
        val fallbackDepth = latestDepthMeters

        if (frameNo % config.yoloEveryNFrames == 0) {
            latestYolo = yoloProcessor.run(
                image = image,
                interpreter = modelManager.yoloInterpreter,
                labels = listOf("person", "chair", "wall"),
                fallbackDepth = fallbackDepth
            )
        }

        if (frameNo % config.hazardEveryNFrames == 0) {
            latestHazards = hazardProcessor.run(
                image = image,
                interpreter = modelManager.hazardInterpreter,
                labels = listOf("pole", "pothole", "staircase", "ramp", "wall"),
                fallbackDepth = fallbackDepth
            )
        }

        if (frameNo % depthInterval == 0) {
            latestDepthMeters = depthProcessor.estimateDepthMeters(image, modelManager.depthInterpreter)
        }
        val correctedDepth = imuManager.correctDepth(latestDepthMeters)


        val homography = computeGroundHomography(imuManager.currentPitchRad())

        val merged = (latestYolo + latestHazards).map { detection ->
            val projected = applyHomography(detection.centerX, detection.centerY, homography)
            val spatialCell = spatialMapper.mapTo3x3(
                centerX = projected.first,
                centerY = projected.second,
                frameWidth = image.width,
                frameHeight = image.height
            )
            detection.copy(
                centerX = projected.first,
                centerY = projected.second,
                depth = correctedDepth,
                zone = spatialCell.zone,
                row = spatialCell.row,
                col = spatialCell.col
            )
        }

        val tracked = objectTracker.update(merged)
        val command = navigationEngine.buildCommand(tracked)
        val nearest = tracked.minByOrNull { it.depth }

        nearest?.let { top ->
            val now = System.currentTimeMillis()
            if (sceneMemory.shouldSpeak(command, top.id, top.depth, now) && ttsManager.isOperational()) {
                ttsManager.speakAsync(command)
            }
        }

        onNavigationUpdate(command, fps, config.cpuOnlyMode)
    }

    private fun computeGroundHomography(pitchRad: Float): FloatArray {
        val pitchScale = (1f + kotlin.math.abs(pitchRad) * 0.4f).coerceIn(0.8f, 1.3f)
        return floatArrayOf(
            1f, 0f, 0f,
            0f, pitchScale, 0f,
            0f, 0f, 1f
        )
    }

    private fun applyHomography(x: Float, y: Float, h: FloatArray): Pair<Float, Float> {
        val w = (h[6] * x + h[7] * y + h[8]).coerceAtLeast(0.0001f)
        val nx = (h[0] * x + h[1] * y + h[2]) / w
        val ny = (h[3] * x + h[4] * y + h[5]) / w
        return nx to ny
    }

    private fun updateFpsAndIntervals(frameNo: Int) {
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - fpsWindowStart
        if (elapsed < 1000L) return

        fps = frameNo * 1000f / elapsed
        frameCounter.set(0)
        fpsWindowStart = now

        depthInterval = when {
            fps < 14f -> 5
            fps > 20f -> 2
            else -> modelManager.runtimeConfig.depthEveryNFrames
        }
    }
}
