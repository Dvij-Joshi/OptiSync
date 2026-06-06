package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.data.OptiSyncDatabase
import com.example.data.SettingsRepository
import com.example.ui.CalibrationScreen
import com.example.ui.CameraHandler
import com.example.ui.CameraPermissionDeniedView
import com.example.ui.MainViewModel

/**
 * Dedicated fullscreen activity for the calibration process.
 * Forces the user to complete the flow without accidental navigation.
 */
class CalibrationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = applicationContext
            val database = OptiSyncDatabase.getDatabase(context)
            val repository = SettingsRepository(database.gestureSettingsDao())

            val mainViewModel: MainViewModel = viewModel(
                factory = MainViewModel.Factory(repository, context)
            )

            // Let users escape if lighting/tracking is bad, then recalibrate later.
            BackHandler(enabled = true) {
                finish()
            }

            var hasCameraPermission by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.CAMERA
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                )
            }

            val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { granted ->
                hasCameraPermission = granted
            }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (!hasCameraPermission) {
                    launcher.launch(android.Manifest.permission.CAMERA)
                }
            }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                        if (hasCameraPermission) {
                            CameraHandler(viewModel = mainViewModel)
                            CalibrationScreen(
                                viewModel = mainViewModel,
                                onFinish = { finish() }
                            )
                        } else {
                            CameraPermissionDeniedView {
                                launcher.launch(android.Manifest.permission.CAMERA)
                            }
                        }
                    }
                }
            }
        }
    }
}
