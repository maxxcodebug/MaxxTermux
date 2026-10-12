package com.maxxcodebug.maxxtermux.terminal

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import com.maxxcodebug.maxxtermux.ssh.SshSession
import kotlin.math.ceil

private const val DEF_FG = 0xFFDDE6EA.toInt()
private const val DEF_BG = 0xFF0F171B.toInt()

private fun brighten(color: Int): Int {
    for (i in 0 until 8) if (TerminalEmulator.PALETTE[i] == color) return TerminalEmulator.PALETTE[i + 8]
    return color
}

@Composable
fun TerminalView(
    session: SshSession,
    fontSp: Float,
    scrollOffset: Int,
    onScrollChange: (Int) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val paint = remember(fontSp, density.density, density.fontScale) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = with(density) { fontSp.sp.toPx() }
        }
    }
    val cellW = paint.measureText("M")
    val fm = paint.fontMetrics
    val cellH = ceil(fm.descent - fm.ascent)
    val baseline = -fm.ascent
    var dragAcc by remember { mutableFloatStateOf(0f) }
    val currentOffset by rememberUpdatedState(scrollOffset)

    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged { sz ->
                val c = (sz.width / cellW).toInt().coerceAtLeast(20)
                val r = (sz.height / cellH).toInt().coerceAtLeast(4)
                session.resize(c, r)
            }
            .pointerInput(cellH) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragAcc += dragAmount
                        val lines = (dragAcc / cellH).toInt()
                        if (lines != 0) {
                            dragAcc -= lines * cellH
                            val max = session.emulator.scrollbackSize()
                            onScrollChange((currentOffset + lines).coerceIn(0, max))
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            },
    ) {
        session.version // redraw whenever new output arrives
        drawRect(Color(DEF_BG))
        val nc = drawContext.canvas.nativeCanvas
        session.emulator.read { e ->
            val total = e.scrollbackSize() + e.rows
            val first = total - e.rows - scrollOffset
            for (vr in 0 until e.rows) {
                val idx = first + vr
                if (idx < 0 || idx >= total) continue
                val row = e.rowAtVirtual(idx)
                val n = minOf(row.ch.size, e.cols)
                val ry = vr * cellH
                var x = 0
                while (x < n) {
                    val fg = row.fg[x]
                    val bg = row.bg[x]
                    val at = row.at[x].toInt()
                    var y = x + 1
                    while (y < n && row.fg[y] == fg && row.bg[y] == bg && row.at[y].toInt() == at) y++
                    var f = if (fg == 0) DEF_FG else fg
                    var b = if (bg == 0) DEF_BG else bg
                    if (at and TerminalEmulator.ATTR_INVERSE != 0) {
                        val t = f
                        f = b
                        b = t
                    }
                    if (at and TerminalEmulator.ATTR_BOLD != 0) f = brighten(f)
                    if (b != DEF_BG) {
                        drawRect(Color(b), Offset(x * cellW, ry), Size((y - x) * cellW, cellH))
                    }
                    var blank = true
                    for (k in x until y) if (row.ch[k] != ' ') { blank = false; break }
                    if (!blank) {
                        paint.color = f
                        paint.isUnderlineText = at and TerminalEmulator.ATTR_UNDERLINE != 0
                        paint.isFakeBoldText = at and TerminalEmulator.ATTR_BOLD != 0
                        nc.drawText(String(row.ch, x, y - x), x * cellW, ry + baseline, paint)
                    }
                    x = y
                }
            }
            if (scrollOffset == 0 && e.cursorVisible) {
                drawRect(
                    Color(0x99A5CFE0),
                    Offset(e.curCol * cellW, e.curRow * cellH),
                    Size(cellW, cellH),
                )
            }
        }
    }
}
