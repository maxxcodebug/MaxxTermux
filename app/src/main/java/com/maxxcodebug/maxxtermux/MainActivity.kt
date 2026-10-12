package com.maxxcodebug.maxxtermux

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.maxxcodebug.maxxtermux.design.AxColors
import com.maxxcodebug.maxxtermux.design.AxGlassDock
import com.maxxcodebug.maxxtermux.design.AxTab
import com.maxxcodebug.maxxtermux.notify.BuildNotifier
import com.maxxcodebug.maxxtermux.state.AppState
import com.maxxcodebug.maxxtermux.state.Screen
import com.maxxcodebug.maxxtermux.ui.addhost.AddHostScreen
import com.maxxcodebug.maxxtermux.ui.home.HomeScreen
import com.maxxcodebug.maxxtermux.ui.keys.KeysScreen
import com.maxxcodebug.maxxtermux.ui.shell.ShellScreen
import com.maxxcodebug.maxxtermux.ui.snippets.SnippetsScreen

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppState.init(this)
        BuildNotifier.ensureChannels(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val screen = AppState.screen
            val shellOpen = screen == Screen.Shell && AppState.active != null
            val showDock = !shellOpen && screen != Screen.AddHost

            BackHandler(enabled = screen != Screen.Home) { AppState.screen = Screen.Home }

            Box(Modifier.fillMaxSize().background(AxColors.Bg)) {
                when (screen) {
                    Screen.Home -> HomeScreen()
                    Screen.Shell -> ShellScreen()
                    Screen.Keys -> KeysScreen()
                    Screen.Snippets -> SnippetsScreen()
                    Screen.AddHost -> AddHostScreen()
                }
                if (showDock) {
                    AxGlassDock(
                        selected = when (screen) {
                            Screen.Shell -> AxTab.Shell
                            Screen.Keys -> AxTab.Keys
                            Screen.Snippets -> AxTab.Snippets
                            else -> AxTab.Home
                        },
                        onSelect = { tab ->
                            AppState.screen = when (tab) {
                                AxTab.Home -> Screen.Home
                                AxTab.Shell -> Screen.Shell
                                AxTab.Keys -> Screen.Keys
                                AxTab.Snippets -> Screen.Snippets
                            }
                        },
                        onFab = { AppState.screen = Screen.AddHost },
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                    )
                }
            }
        }
    }
}
