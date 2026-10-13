package com.maxxcodebug.maxxtermux.design

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Phone tilt as an angle. 0 means upright. Used to turn the light on the glass edge. */
@Composable
private fun rememberGravityAngle(): State<Float> {
    val ctx = LocalContext.current
    val angle = remember { mutableFloatStateOf(0f) }
    DisposableEffect(ctx) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val ax = e.values[0]
                val ay = e.values[1]
                if (hypot(ax, ay) < 2f) return
                val target = atan2(ax, ay)
                var d = target - angle.floatValue
                if (d > PI.toFloat()) d -= 2f * PI.toFloat()
                if (d < -PI.toFloat()) d += 2f * PI.toFloat()
                if (abs(d) > 0.004f) angle.floatValue = angle.floatValue + d * 0.12f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sm != null && sensor != null) {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sm?.unregisterListener(listener) }
    }
    return angle
}

/**
 * Glass look: tint, soft sheen, a light spot and a bright rim on the side the light comes from,
 * and a darker edge on the opposite side. [angle] turns the light, [lift] brightens it while touched.
 */
private fun Modifier.liquidSurface(
    shape: Shape,
    tint: Color,
    angle: () -> Float,
    lift: () -> Float,
): Modifier = this.drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val stroke = Stroke(width = 1.2.dp.toPx())
    onDrawBehind {
        val a = angle()
        val l = lift()
        val dir = Offset(sin(a), -cos(a))
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = maxOf(size.width, size.height) / 2f

        drawOutline(outline, color = tint)
        drawOutline(
            outline,
            brush = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.16f + 0.10f * l), Color.White.copy(alpha = 0.03f)),
            ),
        )
        drawOutline(
            outline,
            brush = Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.24f + 0.18f * l), Color.Transparent),
                center = c + dir * (r * 0.55f),
                radius = r * 1.1f,
            ),
        )
        drawOutline(
            outline,
            brush = Brush.linearGradient(
                listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent),
                start = c - dir * r,
                end = c,
            ),
        )
        drawOutline(
            outline,
            brush = Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.95f), Color.Transparent, Color.White.copy(alpha = 0.35f)),
                start = c + dir * r,
                end = c - dir * r,
            ),
            style = stroke,
        )
    }
}

/**
 * Liquid glass dock. The selection is a glass bubble that moves with a springy, wobbly motion,
 * stretches while it travels, grows when pressed, and can be dragged between tabs.
 * Icons under the bubble are magnified like a lens.
 */
@Composable
fun AxLiquidDock(
    selected: AxTab,
    onSelect: (AxTab) -> Unit,
    onFab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = AxTab.entries
    val n = tabs.size
    val angle = rememberGravityAngle()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val currentOnSelect by rememberUpdatedState(onSelect)

    var innerWidthPx by remember { mutableFloatStateOf(0f) }
    val tabWpx = if (innerWidthPx > 0f) innerWidthPx / n else 0f
    val selIndex = tabs.indexOf(selected)
    val indicatorX = remember { Animatable(0f) }
    var initialized by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }

    LaunchedEffect(selIndex, tabWpx, dragging) {
        if (tabWpx <= 0f || dragging) return@LaunchedEffect
        val target = selIndex * tabWpx
        if (!initialized) {
            indicatorX.snapTo(target)
            initialized = true
        } else {
            indicatorX.animateTo(target, spring(dampingRatio = 0.58f, stiffness = 420f))
        }
    }

    val lift by animateFloatAsState(
        targetValue = if (dragging || pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "lift",
    )
    val stretch by animateFloatAsState(
        targetValue = if (indicatorX.isRunning || dragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 260f),
        label = "stretch",
    )

    fun commit() {
        if (tabWpx <= 0f) return
        val idx = (indicatorX.value / tabWpx).roundToInt().coerceIn(0, n - 1)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        currentOnSelect(tabs[idx])
    }

    val pillShape = RoundedCornerShape(36.dp)
    val bubbleShape = RoundedCornerShape(percent = 50)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(68.dp)
                .shadow(14.dp, pillShape, clip = false, ambientColor = Color(0x80000000), spotColor = Color(0x80000000))
                .clip(pillShape)
                .liquidSurface(pillShape, AxColors.Glass, { angle.value }, { lift * 0.4f }),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .onSizeChanged { innerWidthPx = it.width.toFloat() }
                    .pointerInput(tabWpx) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                tryAwaitRelease()
                                pressed = false
                            },
                            onTap = { off ->
                                if (tabWpx > 0f) {
                                    val idx = (off.x / tabWpx).toInt().coerceIn(0, n - 1)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentOnSelect(tabs[idx])
                                }
                            },
                        )
                    }
                    .pointerInput(tabWpx) {
                        detectDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = {
                                dragging = false
                                commit()
                            },
                            onDragCancel = { dragging = false },
                            onDrag = { change, amount ->
                                change.consume()
                                val maxX = tabWpx * (n - 1)
                                scope.launch {
                                    indicatorX.snapTo((indicatorX.value + amount.x).coerceIn(0f, maxX))
                                }
                            },
                        )
                    },
            ) {
                if (tabWpx > 0f) {
                    Box(
                        Modifier
                            .offset { IntOffset(indicatorX.value.roundToInt(), 0) }
                            .width(with(density) { tabWpx.toDp() })
                            .fillMaxHeight()
                            .padding(horizontal = 2.dp)
                            .graphicsLayer {
                                scaleX = 1f + 0.10f * lift + 0.22f * stretch
                                scaleY = 1f + 0.10f * lift - 0.06f * stretch
                            }
                            .liquidSurface(bubbleShape, Color(0x24DCF0FA), { angle.value }, { lift }),
                    )
                }

                Row(Modifier.fillMaxSize()) {
                    tabs.forEachIndexed { i, tab ->
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    val center = (i + 0.5f) * tabWpx
                                    val bubble = indicatorX.value + tabWpx / 2f
                                    val prox = if (tabWpx > 0f) {
                                        (1f - abs(center - bubble) / tabWpx).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                    val sc = 1f + 0.16f * prox
                                    scaleX = sc
                                    scaleY = sc
                                    alpha = 0.62f + 0.38f * prox
                                },
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AxIcon(tab.icon, tint = AxColors.OnTeal, size = 22.dp)
                            AxText(
                                tab.label,
                                size = 10.sp,
                                color = AxColors.OnTeal,
                                weight = FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        Box(
            Modifier
                .size(58.dp)
                .axClickable { onFab() }
                .shadow(14.dp, CircleShape, clip = false, ambientColor = Color(0x80000000), spotColor = Color(0x80000000))
                .clip(CircleShape)
                .liquidSurface(CircleShape, AxColors.GlassAccent, { angle.value }, { 0f }),
            contentAlignment = Alignment.Center,
        ) {
            AxIcon(Icons.Rounded.Add, tint = AxColors.OnTeal, size = 26.dp)
        }
    }
}
