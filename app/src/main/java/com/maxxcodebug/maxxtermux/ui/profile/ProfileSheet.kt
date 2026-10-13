package com.maxxcodebug.maxxtermux.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxxcodebug.maxxtermux.design.AxButton
import com.maxxcodebug.maxxtermux.design.AxButtonKind
import com.maxxcodebug.maxxtermux.design.AxColors
import com.maxxcodebug.maxxtermux.design.AxField
import com.maxxcodebug.maxxtermux.design.AxShapes
import com.maxxcodebug.maxxtermux.design.AxText
import com.maxxcodebug.maxxtermux.state.AppState

/** Asked on first start, and opened again by tapping the avatar on Home. */
@Composable
fun ProfileSheet() {
    var name by remember { mutableStateOf(AppState.userName) }
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val initials = if (parts.isEmpty()) "AX" else parts.take(2).joinToString("") { it.substring(0, 1).uppercase() }
    val first = AppState.userName.isBlank()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xB3000000))
            .pointerInput(Unit) { detectTapGestures { } }
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .clip(AxShapes.Card)
                .background(AxColors.Surface)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(AxColors.Teal),
                contentAlignment = Alignment.Center,
            ) { AxText(initials, size = 26.sp, color = AxColors.OnTeal, weight = FontWeight.Medium) }
            Spacer(Modifier.height(12.dp))
            AxText(
                if (first) "Welcome to MaxxTermux" else "Your profile",
                size = 20.sp,
                weight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            AxText(
                "What should we call you? This stays on your phone. There is no account or sign in.",
                size = 13.sp,
                color = AxColors.OnMuted,
            )
            Spacer(Modifier.height(14.dp))
            AxField("Your name", name, { name = it })
            Spacer(Modifier.height(14.dp))
            AxButton(
                if (first) "Continue" else "Save",
                onClick = { AppState.saveUserName(name) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            AxButton(
                if (first) "Skip" else "Close",
                onClick = { if (first) AppState.skipProfile() else AppState.profileOpen = false },
                modifier = Modifier.fillMaxWidth(),
                kind = AxButtonKind.Secondary,
            )
        }
    }
}
