package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.ui.screens.MusicMainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MusicViewModel
import com.example.ui.viewmodel.MusicViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels {
        MusicViewModelFactory(applicationContext)
    }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordLog = permissions.entries.joinToString { "${it.key} = ${it.value}" }
        Log.d("MainActivity", "Permissions response state: $recordLog")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        requestNecessaryPermissions()

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MusicMainScreen(viewModel = viewModel)
                }
            }
        }
    }

    private fun requestNecessaryPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        // Add Post Notifications (API 33+) logic for music service foreground indicators
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionsLauncher.launch(missing.toTypedArray())
        }
    }
}
