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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.maxxcodebug.maxxtermux.design.AxColors
import com.maxxcodebug.maxxtermux.design.AxLiquidDock
import com.maxxcodebug.maxxtermux.design.AxTab
import com.maxxcodebug.maxxtermux.notify.BuildNotifier
import com.maxxcodebug.maxxtermux.state.AppState
import com.maxxcodebug.maxxtermux.state.Screen
import com.maxxcodebug.maxxtermux.ui.addhost.AddHostScreen
import com.maxxcodebug.maxxtermux.ui.home.HomeScreen
import com.maxxcodebug.maxxtermux.ui.keys.KeysScreen
import com.maxxcodebug.maxxtermux.ui.profile.ProfileSheet
import com.maxxcodebug.maxxtermux.ui.settings.SettingsScreen
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
            val showDock = screen != Screen.Shell && screen != Screen.AddHost

            BackHandler(enabled = screen != Screen.Home) { AppState.screen = Screen.Home }
            BackHandler(enabled = AppState.profileOpen) { AppState.profileOpen = false }

            Box(Modifier.fillMaxSize().background(AxColors.Bg)) {
                AnimatedContent(
                    targetState = screen,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        (fadeIn(tween(240)) + slideInVertically(tween(280)) { it / 24 }) togetherWith
                            fadeOut(tween(140))
                    },
                    label = "screens",
                ) { s ->
                    when (s) {
                        Screen.Home -> HomeScreen()
                        Screen.Shell -> ShellScreen()
                        Screen.Keys -> KeysScreen()
                        Screen.Snippets -> SnippetsScreen()
                        Screen.AddHost -> AddHostScreen()
                        Screen.Settings -> SettingsScreen()
                    }
                }

                AnimatedVisibility(
                    visible = showDock,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(tween(280)) { it } + fadeIn(),
                    exit = slideOutVertically(tween(200)) { it } + fadeOut(),
                ) {
                    AxLiquidDock(
                        selected = when (screen) {
                            Screen.Shell -> AxTab.Shell
                            Screen.Keys -> AxTab.Keys
                            Screen.Settings, Screen.Snippets -> AxTab.Settings
                            else -> AxTab.Home
                        },
                        onSelect = { tab ->
                            when (tab) {
                                AxTab.Home -> AppState.screen = Screen.Home
                                AxTab.Shell -> AppState.openTerminal()
                                AxTab.Keys -> AppState.screen = Screen.Keys
                                AxTab.Settings -> AppState.screen = Screen.Settings
                            }
                        },
                        onFab = { AppState.newLocal() },
                        modifier = Modifier.navigationBarsPadding(),
                    )
                }

                AnimatedVisibility(
                    visible = AppState.profileOpen,
                    enter = fadeIn() + scaleIn(initialScale = 0.92f),
                    exit = fadeOut(),
                ) {
                    ProfileSheet()
                }
            }
        }
    }
}
