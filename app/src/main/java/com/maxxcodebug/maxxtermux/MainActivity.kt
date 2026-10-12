package com.maxxcodebug.maxxtermux

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.maxxcodebug.maxxtermux.model.SampleData
import com.maxxcodebug.maxxtermux.ui.home.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeScreen(
                hosts = SampleData.hosts,
                build = SampleData.build,
                prefs = SampleData.progressPrefs,
            )
        }
    }
}
