package com.maxxcodebug.maxxtermux

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.maxxcodebug.maxxtermux.model.SampleData
import com.maxxcodebug.maxxtermux.notify.BuildNotifier
import com.maxxcodebug.maxxtermux.service.BuildService
import com.maxxcodebug.maxxtermux.ui.home.HomeScreen

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BuildNotifier.ensureChannels(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            HomeScreen(
                hosts = SampleData.hosts,
                build = SampleData.build,
                prefs = SampleData.progressPrefs,
                // Temporary: starts a 30 second demo build to test the notification.
                onOpenSession = { BuildService.startDemo(this@MainActivity) },
            )
        }
    }
}
