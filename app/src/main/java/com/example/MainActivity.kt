package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.repository.CampusGuardRepository
import com.example.ui.CampusGuardApp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var repository: CampusGuardRepository

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && repository.preferences.isMonitoringEnabled && repository.preferences.isRegistered) {
            repository.startMonitoring()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = CampusGuardRepository.getInstance(this)

        setContent {
            MyApplicationTheme {
                CampusGuardApp(repository = repository)
            }
        }

        checkNotificationPermission()

        if (repository.preferences.isMonitoringEnabled && repository.preferences.isRegistered) {
            repository.startMonitoring()
        }
    }

    override fun onResume() {
        super.onResume()
        if (repository.preferences.isRegistered) {
            lifecycleScope.launch(Dispatchers.IO) {
                repository.syncNow()
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                try {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } catch (ignored: Exception) {
                }
            }
        }
    }
}
