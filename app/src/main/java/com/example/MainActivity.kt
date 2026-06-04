package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.OptiSyncDatabase
import com.example.data.SettingsRepository
import com.example.ui.MainLayout
import com.example.ui.MainViewModel

class MainActivity : ComponentActivity() {
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

            Surface(modifier = Modifier.fillMaxSize()) {
                MainLayout(viewModel = mainViewModel)
            }
        }
    }
}
