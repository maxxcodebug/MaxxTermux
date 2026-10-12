package com.maxxcodebug.maxxtermux.ui.keys

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxxcodebug.maxxtermux.design.AxColors
import com.maxxcodebug.maxxtermux.design.AxIconButton
import com.maxxcodebug.maxxtermux.design.AxScreenHeader
import com.maxxcodebug.maxxtermux.design.AxShapes
import com.maxxcodebug.maxxtermux.design.AxText
import com.maxxcodebug.maxxtermux.state.AppState

@Composable
fun KeysScreen() {
    val entries = AppState.known.entries.sortedBy { it.key }
    Column(
        Modifier
            .fillMaxSize()
            .background(AxColors.Bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
    ) {
        AxScreenHeader("Keys", "Trusted hosts")
        AxText(
            "The first time you connect, MaxxTermux saves the server's fingerprint. If it ever changes, the connection is blocked. Remove an entry here if you reinstalled the server.",
            size = 13.sp,
            color = AxColors.OnMuted,
        )
        Spacer(Modifier.height(14.dp))
        if (entries.isEmpty()) {
            AxText("No trusted hosts yet. Connect to a host first.", size = 15.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().clip(AxShapes.Row).background(AxColors.Surface).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        AxText(e.key, size = 15.sp, weight = androidx.compose.ui.text.font.FontWeight.Medium, maxLines = 1)
                        AxText(e.value, size = 11.sp, color = AxColors.OnMuted, mono = true)
                    }
                    Spacer(Modifier.width(8.dp))
                    AxIconButton(Icons.Rounded.Close, { AppState.forgetKnown(e.key) })
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        AxText("SSH key pairs (generate and import) are coming next.", size = 13.sp, color = AxColors.OnMuted)
    }
}
