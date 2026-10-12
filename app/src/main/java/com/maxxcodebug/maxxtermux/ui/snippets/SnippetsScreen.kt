package com.maxxcodebug.maxxtermux.ui.snippets

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxxcodebug.maxxtermux.design.AxButton
import com.maxxcodebug.maxxtermux.design.AxChoiceChip
import com.maxxcodebug.maxxtermux.design.AxColors
import com.maxxcodebug.maxxtermux.design.AxField
import com.maxxcodebug.maxxtermux.design.AxIconButton
import com.maxxcodebug.maxxtermux.design.AxScreenHeader
import com.maxxcodebug.maxxtermux.design.AxShapes
import com.maxxcodebug.maxxtermux.design.AxText
import com.maxxcodebug.maxxtermux.state.AppState

private val GROUPS = listOf("AOSP build", "Git", "GitHub CLI", "Navigate", "Custom")

@Composable
fun SnippetsScreen() {
    var group by remember { mutableStateOf(GROUPS[0]) }
    var text by remember { mutableStateOf("") }
    val grouped = AppState.snippets.groupBy { it.group }

    Column(
        Modifier
            .fillMaxSize()
            .background(AxColors.Bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
    ) {
        AxScreenHeader("Snippets", "Commands for the Shell")

        AxField("New command", text, { text = it }, mono = true)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GROUPS.forEach { g -> AxChoiceChip(g, g == group) { group = g } }
        }
        Spacer(Modifier.height(10.dp))
        AxButton(
            "Add snippet",
            onClick = {
                val t = text.trim()
                if (t.isNotEmpty()) {
                    AppState.addSnippet(group, t)
                    text = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        for ((g, items) in grouped) {
            Spacer(Modifier.height(18.dp))
            AxText(g, size = 14.sp, color = androidx.compose.ui.graphics.Color(0xFF8FC3D6))
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().clip(AxShapes.Row).background(AxColors.Surface).padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AxText(s.text, size = 14.sp, mono = true, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        AxIconButton(Icons.Rounded.Close, { AppState.deleteSnippet(s) })
                    }
                }
            }
        }
    }
}
