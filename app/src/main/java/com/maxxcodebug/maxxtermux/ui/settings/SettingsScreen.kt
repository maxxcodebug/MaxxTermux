package com.maxxcodebug.maxxtermux.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxxcodebug.maxxtermux.design.AxButton
import com.maxxcodebug.maxxtermux.design.AxButtonKind
import com.maxxcodebug.maxxtermux.design.AxChoiceChip
import com.maxxcodebug.maxxtermux.design.AxColors
import com.maxxcodebug.maxxtermux.design.AxField
import com.maxxcodebug.maxxtermux.design.AxProgress
import com.maxxcodebug.maxxtermux.design.AxProgressStyle
import com.maxxcodebug.maxxtermux.design.AxScreenHeader
import com.maxxcodebug.maxxtermux.design.AxShapes
import com.maxxcodebug.maxxtermux.design.AxText
import com.maxxcodebug.maxxtermux.design.axAppear
import com.maxxcodebug.maxxtermux.design.axClickable
import com.maxxcodebug.maxxtermux.state.AppState
import com.maxxcodebug.maxxtermux.state.Screen

@Composable
fun SettingsScreen() {
    val prefs = AppState.progressPrefs
    var name by remember { mutableStateOf(AppState.userName) }

    Column(
        Modifier
            .fillMaxSize()
            .background(AxColors.Bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
    ) {
        AxScreenHeader("Settings", "Make it yours")

        Column(
            Modifier.fillMaxWidth().axAppear(0).clip(AxShapes.Card).background(AxColors.Surface).padding(18.dp),
        ) {
            AxText("Profile", size = 16.sp, weight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            AxField("Your name", name, { name = it })
            Spacer(Modifier.height(10.dp))
            AxButton("Save name", onClick = { AppState.setUserName(name) }, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().axAppear(1).clip(AxShapes.Card).background(AxColors.Surface).padding(18.dp),
        ) {
            AxText("Build progress style", size = 16.sp, weight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))

            Column(
                Modifier.fillMaxWidth().clip(AxShapes.Row).background(AxColors.Teal).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (prefs.style.isRing) {
                    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                        AxProgress(
                            0.82f, prefs.style, Modifier.fillMaxSize(),
                            prefs.thicknessDp.dp, prefs.color, prefs.animateWave,
                        )
                        AxText("82%", size = 18.sp, color = AxColors.OnTeal, weight = FontWeight.Medium)
                    }
                } else {
                    AxProgress(
                        0.82f, prefs.style, Modifier.fillMaxWidth().height(28.dp),
                        prefs.thicknessDp.dp, prefs.color, prefs.animateWave,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AxProgressStyle.entries.forEach { st ->
                    AxChoiceChip(st.label, prefs.style == st) { AppState.updatePrefs(prefs.copy(style = st)) }
                }
            }

            Spacer(Modifier.height(14.dp))
            AxText("Thickness", size = 12.sp, color = AxColors.OnMuted)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(4, 6, 8, 10).forEach { t ->
                    AxChoiceChip("$t dp", prefs.thicknessDp == t) { AppState.updatePrefs(prefs.copy(thicknessDp = t)) }
                }
            }

            Spacer(Modifier.height(14.dp))
            AxText("Moving wave", size = 12.sp, color = AxColors.OnMuted)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AxChoiceChip("On", prefs.animateWave) { AppState.updatePrefs(prefs.copy(animateWave = true)) }
                AxChoiceChip("Off", !prefs.animateWave) { AppState.updatePrefs(prefs.copy(animateWave = false)) }
            }

            Spacer(Modifier.height(14.dp))
            AxText("Accent color", size = 12.sp, color = AxColors.OnMuted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppState.accentChoices.forEach { c ->
                    val sel = c == prefs.color
                    Box(
                        Modifier
                            .size(38.dp)
                            .axClickable { AppState.updatePrefs(prefs.copy(color = c)) }
                            .clip(CircleShape)
                            .background(c)
                            .border(3.dp, if (sel) AxColors.OnSurface else Color.Transparent, CircleShape),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().axAppear(2).clip(AxShapes.Card).background(AxColors.Surface).padding(18.dp),
        ) {
            AxText("Terminal", size = 16.sp, weight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            AxText(
                "Phone storage: open the Terminal and run  setup-storage . Your commands live in the Snippets panel of the Terminal.",
                size = 13.sp,
                color = AxColors.OnMuted,
            )
            Spacer(Modifier.height(12.dp))
            AxButton(
                "Manage snippets",
                onClick = { AppState.screen = Screen.Snippets },
                modifier = Modifier.fillMaxWidth(),
                kind = AxButtonKind.Secondary,
            )
        }
    }
}
