package com.maxxcodebug.maxxtermux.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The four progress styles the user can pick in Settings. */
enum class AxProgressStyle(val label: String) {
    Bar("Straight"),
    WavyBar("Wavy bar"),
    Ring("Circle"),
    WavyRing("Wavy circle");

    val isRing: Boolean get() = this == Ring || this == WavyRing
    val isWavy: Boolean get() = this == WavyBar || this == WavyRing
}

@Composable
fun AxProgress(
    progress: Float,
    style: AxProgressStyle,
    modifier: Modifier = Modifier,
    thickness: Dp = 6.dp,
    color: Color = AxColors.AccentSoft,
    animateWave: Boolean = true,
) {
    val p = progress.coerceIn(0f, 1f)
    val phase = if (animateWave && style.isWavy) {
        rememberInfiniteTransition(label = "ax-wave").animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
            label = "ax-wave-phase",
        ).value
    } else 0f

    val track = color.copy(alpha = 0.28f)

    Canvas(modifier) {
        val stroke = thickness.toPx()
        when (style) {
            AxProgressStyle.Bar -> {
                val y = size.height / 2
                drawLine(track, Offset(stroke / 2, y), Offset(size.width - stroke / 2, y), stroke, StrokeCap.Round)
                if (p > 0f) {
                    val xe = stroke / 2 + (size.width - stroke) * p
                    drawLine(color, Offset(stroke / 2, y), Offset(xe, y), stroke, StrokeCap.Round)
                }
            }

            AxProgressStyle.WavyBar -> {
                val amp = 4.dp.toPx()
                val wavelength = 26.dp.toPx()
                val k = (2 * PI / wavelength).toFloat()
                val x0 = stroke / 2 + amp
                val x1 = size.width - stroke / 2
                val xe = x0 + (x1 - x0) * p
                val midY = size.height / 2
                val gap = 12.dp.toPx()
                if (xe + gap < x1) {
                    drawLine(track, Offset(xe + gap, midY), Offset(x1, midY), stroke, StrokeCap.Round)
                }
                if (p > 0f) {
                    val path = Path()
                    var x = x0
                    path.moveTo(x, midY + amp * sin(k * x + phase))
                    while (x < xe) {
                        x += 2f
                        path.lineTo(x.coerceAtMost(xe), midY + amp * sin(k * x + phase))
                    }
                    drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }

            AxProgressStyle.Ring -> {
                val d = min(size.width, size.height) - stroke
                val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
                drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(stroke))
                if (p > 0f) {
                    drawArc(color, -90f, 360f * p, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }

            AxProgressStyle.WavyRing -> {
                val amp = 3.2.dp.toPx()
                val r = min(size.width, size.height) / 2 - stroke / 2 - amp
                val cx = size.width / 2
                val cy = size.height / 2
                val sweep = 360f * p
                val gap = 12f
                if (sweep + gap < 348f) {
                    val tl = Offset(cx - r, cy - r)
                    drawArc(
                        track, -90f + sweep + gap, 360f - sweep - 2 * gap, false, tl, Size(2 * r, 2 * r),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                if (p > 0f) {
                    val path = Path()
                    var a = 0f
                    var first = true
                    while (a <= sweep + 0.01f) {
                        val theta = a * PI.toFloat() / 180f
                        val rr = r + amp * sin(10f * theta + phase)
                        val ang = (-90f + a) * PI.toFloat() / 180f
                        val x = cx + rr * cos(ang)
                        val y = cy + rr * sin(ang)
                        if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                        a += 2f
                    }
                    drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}
