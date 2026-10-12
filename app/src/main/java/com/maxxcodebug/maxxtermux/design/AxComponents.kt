package com.maxxcodebug.maxxtermux.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AxText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 15.sp,
    color: Color = AxColors.OnSurface,
    weight: FontWeight = FontWeight.Normal,
    mono: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontWeight = weight,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        ),
    )
}

@Composable
fun AxIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = AxColors.OnSurface,
    size: Dp = 22.dp,
) {
    Image(
        imageVector = icon,
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

enum class AxTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Shell("Shell", Icons.Rounded.Terminal),
    Keys("Keys", Icons.Rounded.Key),
    Snippets("Snippets", Icons.Rounded.Code),
}

/**
 * Floating glass dock: nav pill plus a detached round action button.
 * Glass is a translucent fill with a light edge for now. Real backdrop blur
 * (RenderEffect, Android 12+) is a later step.
 */
@Composable
fun AxGlassDock(
    selected: AxTab,
    onSelect: (AxTab) -> Unit,
    onFab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(AxShapes.Pill)
                .background(AxColors.Glass)
                .border(0.5.dp, AxColors.GlassEdge, AxShapes.Pill)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AxTab.entries.forEach { tab ->
                val isSelected = tab == selected
                Row(
                    modifier = Modifier
                        .clip(AxShapes.Pill)
                        .then(if (isSelected) Modifier.background(AxColors.GlassSelected) else Modifier)
                        .clickable { onSelect(tab) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AxIcon(tab.icon, tint = AxColors.OnTeal, size = 22.dp)
                    if (isSelected) {
                        Spacer(Modifier.width(6.dp))
                        AxText(tab.label, size = 14.sp, color = AxColors.OnTeal, weight = FontWeight.Medium)
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(AxColors.GlassAccent)
                .border(0.5.dp, AxColors.GlassEdge, CircleShape)
                .clickable { onFab() },
            contentAlignment = Alignment.Center,
        ) {
            AxIcon(Icons.Rounded.Add, tint = AxColors.OnTeal, size = 26.dp)
        }
    }
}
