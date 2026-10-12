package com.maxxcodebug.maxxtermux.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AxField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    singleLine: Boolean = true,
    minHeight: Dp = 0.dp,
    mono: Boolean = false,
) {
    Column(modifier) {
        AxText(
            label,
            size = 12.sp,
            color = AxColors.OnMuted,
            modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .clip(AxShapes.Button)
                .background(AxColors.SurfaceHigh)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = TextStyle(
                    color = AxColors.OnSurface,
                    fontSize = 16.sp,
                    fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                ),
                cursorBrush = SolidColor(AxColors.Accent),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun AxChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(AxShapes.Chip)
            .background(if (selected) AxColors.Accent else AxColors.SurfaceHigh)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        AxText(
            text,
            size = 14.sp,
            color = if (selected) AxColors.OnAccent else Color(0xFFB7C9D1),
            weight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

enum class AxButtonKind { Primary, Secondary, Danger }

@Composable
fun AxButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: AxButtonKind = AxButtonKind.Primary,
) {
    val bg = when (kind) {
        AxButtonKind.Primary -> AxColors.Accent
        AxButtonKind.Secondary -> AxColors.Container
        AxButtonKind.Danger -> Color(0xFF7A3A40)
    }
    val fg = when (kind) {
        AxButtonKind.Primary -> AxColors.OnAccent
        AxButtonKind.Secondary -> AxColors.OnSurface
        AxButtonKind.Danger -> Color(0xFFFFEEEF)
    }
    Box(
        modifier
            .clip(AxShapes.Button)
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        AxText(text, size = 15.sp, color = fg, weight = FontWeight.Medium)
    }
}

@Composable
fun AxIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFBFD8E2),
) {
    Box(
        modifier
            .size(40.dp)
            .clip(AxShapes.Chip)
            .background(AxColors.Container)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        AxIcon(icon, tint = tint, size = 20.dp)
    }
}

@Composable
fun AxScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (onBack != null) {
            AxIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack)
            Spacer(Modifier.width(12.dp))
        }
        Column {
            if (subtitle != null) AxText(subtitle, size = 12.sp, color = AxColors.OnMuted)
            AxText(title, size = 24.sp, weight = FontWeight.Medium)
        }
    }
}
