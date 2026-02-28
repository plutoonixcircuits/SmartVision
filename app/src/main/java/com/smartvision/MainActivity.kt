package com.smartvision

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.smartvision.camera.CameraAnalyzer
import com.smartvision.databinding.ActivityMainBinding
import com.smartvision.ml.ModelManager
import com.smartvision.navigation.NavigationEngine
import com.smartvision.navigation.SceneMemory
import com.smartvision.navigation.SpatialMapper
import com.smartvision.sensors.IMUManager
import com.smartvision.speech.TTSManager
import com.smartvision.tracking.ObjectTracker
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewFinder: PreviewView
        get() = binding.viewFinder
    private val analyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val inferenceExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var modelManager: ModelManager
    private lateinit var ttsManager: TTSManager
    private lateinit var imuManager: IMUManager

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else updateNavigationText("Camera permission denied.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ttsManager = TTSManager(this)
        imuManager = IMUManager(this).also { it.start() }
        modelManager = ModelManager(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val cameraProvider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            val analyzer = CameraAnalyzer(
                modelManager = modelManager,
                objectTracker = ObjectTracker(),
                spatialMapper = SpatialMapper(),
                navigationEngine = NavigationEngine(),
                sceneMemory = SceneMemory(),
                imuManager = imuManager,
                ttsManager = ttsManager,
                onNavigationUpdate = { command, fps, cpuMode ->
                    runOnUiThread {
                        val mode = if (cpuMode) "CPU" else "GPU"
                        binding.fpsText.text = "FPS: %.1f | %s".format(fps, mode)
                        updateNavigationText(command)
                    }
                },
                analyzerExecutor = analyzerExecutor,
                inferenceExecutor = inferenceExecutor
            )

            val analysisUseCase = analyzer.buildImageAnalysis()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysisUseCase
                )
            } catch (_: Exception) {
                updateNavigationText("Unable to start camera.")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun updateNavigationText(text: String) {
        binding.navigationText.text = text
        binding.navigationText.contentDescription = text
        binding.navigationText.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
    }

    override fun onDestroy() {
        super.onDestroy()
        analyzerExecutor.shutdownNow()
        inferenceExecutor.shutdownNow()
        imuManager.stop()
        modelManager.close()
        ttsManager.shutdown()
    }
}
