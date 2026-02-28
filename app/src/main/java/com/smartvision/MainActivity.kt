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
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var modelManager: ModelManager
    private lateinit var ttsManager: TTSManager
    private lateinit var imuManager: IMUManager

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else updateNavigationText("Camera permission denied")
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
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }

            val analyzer = CameraAnalyzer(
                modelManager = modelManager,
                objectTracker = ObjectTracker(),
                spatialMapper = SpatialMapper(),
                navigationEngine = NavigationEngine(),
                sceneMemory = SceneMemory(),
                imuManager = imuManager,
                ttsManager = ttsManager,
                onNavigationUpdate = { command, fps ->
                    // Telemetry callback keeps UI and accessibility output in sync.
                    runOnUiThread {
                        updateNavigationText(command)
                        binding.fpsText.text = "FPS: %.1f".format(fps)
                    }
                },
                analyzerExecutor = cameraExecutor
            )

            val analysis = analyzer.buildImageAnalysis()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (_: Exception) {
                updateNavigationText("Unable to start camera")
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
        cameraExecutor.shutdown()
        imuManager.stop()
        modelManager.close()
        ttsManager.shutdown()
    }
}
