package com.mitrasis.tarot

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Облако тегов на сфере плюс туманность, которая из них же и вырастает:
 * каждое облако света привязано к положению тега, его глубине и его цвету.
 */
@Composable
fun TagCloud(tags: List<String>, scores: List<Float> = emptyList(), modifier: Modifier = Modifier) {
    var yaw by remember { mutableStateOf(-20f) }
    var pitch by remember { mutableStateOf(10f) }
    var velocity by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val tracker = remember { androidx.compose.ui.input.pointer.util.VelocityTracker() }

    // Инерция считается по кадрам: скорость гасится множителем, пока не упадёт до нуля
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                if (!dragging && velocity != Offset.Zero) {
                    yaw += velocity.x * dt
                    pitch += velocity.y * dt
                    val damp = (1f - 1.15f * dt).coerceIn(0f, 1f)
                    val next = velocity * damp
                    velocity = if (next.getDistance() < 2f) Offset.Zero else next
                }
            }
        }
    }

    val spin by rememberInfiniteTransition(label = "cloud").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(62000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin"
    )

    Box(modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = {
                dragging = true
                velocity = Offset.Zero
                tracker.resetTracking()
            },
            onDrag = { change, drag ->
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                yaw += drag.x * 0.30f
                pitch += drag.y * 0.30f
            },
            onDragEnd = {
                dragging = false
                val raw = tracker.calculateVelocity()
                // ограничиваем рывок, иначе облако улетает в бесконечность
                val cap = 1400f
                velocity = Offset(
                    raw.x.coerceIn(-cap, cap) * 0.30f,
                    raw.y.coerceIn(-cap, cap) * 0.30f
                )
            },
            onDragCancel = {
                dragging = false
                velocity = Offset.Zero
            }
        )
    }) {
        Canvas(Modifier.fillMaxSize()) {
            if (tags.isEmpty()) return@Canvas

            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = minOf(cx, cy) * 0.82f

            val golden = PI * (1 + sqrt(5.0))
            val points = tags.mapIndexed { index, tag ->
                val phi = acos(1 - 2 * (index + 0.5) / tags.size)
                val theta = golden * (index + 0.5)
                Triple(
                    (sin(phi) * cos(theta)).toFloat(),
                    cos(phi).toFloat(),
                    (sin(phi) * sin(theta)).toFloat()
                ) to tag
            }

            val yawRad = Math.toRadians((yaw + spin).toDouble())
            val pitchRad = Math.toRadians(pitch.toDouble())
            val rotated = points.map { (p, tag) ->
                val px = p.first.toDouble()
                val py = p.second.toDouble()
                val pz = p.third.toDouble()
                val x1 = px * cos(yawRad) + pz * sin(yawRad)
                val z1 = -px * sin(yawRad) + pz * cos(yawRad)
                val y2 = py * cos(pitchRad) - z1 * sin(pitchRad)
                val z2 = py * sin(pitchRad) + z1 * cos(pitchRad)
                Triple(x1.toFloat(), y2.toFloat(), z2.toFloat()) to tag
            }.sortedBy { it.first.third }

            val order = tags.withIndex().associate { it.value to it.index }
            val high = scores.maxOrNull() ?: 1f
            val low = scores.minOrNull() ?: 0f
            val span = (high - low).coerceAtLeast(1e-4f)

            // созвездие: тонкие связи между соседями по сфере
            for (a in rotated.indices) {
                for (b in a + 1 until rotated.size) {
                    val pa = rotated[a].first
                    val pb = rotated[b].first
                    val dot = pa.first * pb.first + pa.second * pb.second + pa.third * pb.third
                    if (dot < 0.72f) continue
                    val depthAvg = ((pa.third + pb.third) / 2f + 1f) / 2f
                    drawLine(
                        color = Color(0xFFD9CFC0).copy(alpha = 0.05f + 0.10f * depthAvg),
                        start = Offset(cx + pa.first * radius, cy + pa.second * radius),
                        end = Offset(cx + pb.first * radius, cy + pb.second * radius),
                        strokeWidth = 1f
                    )
                }
            }

            val nebula = listOf(
                Color(0xFF6D3BD8), Color(0xFF1FA3B8), Color(0xFFC8389B),
                Color(0xFFE08A2B), Color(0xFF3F6FD8)
            )
            val breathe = 0.75f + 0.25f * (0.5f + 0.5f * sin(spin * PI.toFloat() / 180f))

            // фон: несколько больших облаков, медленно плывущих вокруг оси
            for (i in 0 until 2) {
                val angle = yawRad * 0.6 + i * 1.27
                val cloudRadius = radius * (0.60f + 0.12f * i)
                val bx = cx + (cos(angle) * radius * 0.30).toFloat()
                val by = cy + (sin(angle * 0.7) * radius * 0.26).toFloat()
                val tint = nebula[i % nebula.size]
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tint.copy(alpha = 0.13f * breathe),
                            tint.copy(alpha = 0.05f * breathe),
                            Color.Transparent
                        ),
                        center = Offset(bx, by),
                        radius = cloudRadius
                    ),
                    radius = cloudRadius,
                    center = Offset(bx, by),
                    blendMode = BlendMode.Screen
                )
            }

            // свет вокруг каждого тега: генерируется самим облаком
            for ((p, tag) in rotated) {
                val depth = ((p.third + 1f) / 2f).coerceIn(0f, 1f)
                val tagRadius = radius * (0.18f + 0.14f * depth)
                val tint = nebula[Math.floorMod(tag.hashCode(), nebula.size)]
                val bx = cx + p.first * radius * 0.85f
                val by = cy + p.second * radius * 0.85f

                // ядро — ярче у значимых тегов
                val weightOf = scores.getOrNull(order[tag] ?: -1)
                val importance = if (weightOf == null) 0.5f else ((weightOf - low) / span).coerceIn(0f, 1f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tint.copy(alpha = 0.07f + 0.12f * depth + 0.08f * importance),
                            Color.Transparent
                        ),
                        center = Offset(bx, by),
                        radius = tagRadius
                    ),
                    radius = tagRadius,
                    center = Offset(bx, by),
                    blendMode = BlendMode.Screen
                )
            }

            fun face(score: Float): android.graphics.Typeface {
                val level = (score - low) / span
                val family = when {
                    level >= 0.66f -> "sans-serif-medium"
                    level >= 0.33f -> "sans-serif"
                    else -> "sans-serif-light"
                }
                return android.graphics.Typeface.create(family, android.graphics.Typeface.NORMAL)
            }

            // пыль: чем быстрее вращение после броска, тем больше искр
            val speed = velocity.getDistance()
            if (speed > 90f) {
                val dirX = velocity.x / speed
                val dirY = velocity.y / speed
                val sparks = (speed / 120f).toInt().coerceIn(1, 8)
                for (spark in 0 until sparks) {
                    val spread = ((spark * 37) % 100) / 100f - 0.5f
                    val sx = cx + dirX * radius * (0.55f + spread * 0.4f)
                    val sy = cy + dirY * radius * (0.55f + spread * 0.4f)
                    drawCircle(
                        color = Color(0xFFF2A93B).copy(alpha = (speed / 1400f).coerceIn(0.05f, 0.35f)),
                        radius = 1.2f + spread * 1.5f,
                        center = Offset(sx, sy)
                    )
                }
            }

            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = android.graphics.Paint.Align.CENTER
                // без этих двух флагов Android округляет кегль и позицию до пикселя,
                // отсюда «ступеньки» при вращении сферы
                isSubpixelText = true
                isLinearText = true
                typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            }

            // Разводка по местам: сначала считаем размеры, потом раздвигаем пересечения
            data class Slot(var x: Float, var y: Float, var size: Float, val w: Float, val h: Float, val tag: String, val depth: Float)
            val placed = ArrayList<Slot>()
            for ((p, tag) in rotated) {
                val depth = ((p.third + 1f) / 2f).coerceIn(0f, 1f)
                var textSize = size.minDimension * (0.055f + 0.075f * depth)
                paint.textSize = textSize
                var slot = Slot(
                    x = cx + p.first * radius,
                    y = cy + p.second * radius,
                    size = textSize,
                    w = paint.measureText(tag),
                    h = textSize * 1.15f,
                    tag = tag,
                    depth = depth
                )
                // пробуем сдвинуть вверх-вниз, при неудаче — уменьшить кегль
                var attempt = 0
                while (attempt < 10 && placed.any { other ->
                        kotlin.math.abs(other.x - slot.x) < (other.w + slot.w) / 2f + 6f &&
                        kotlin.math.abs(other.y - slot.y) < (other.h + slot.h) / 2f + 4f
                    }
                ) {
                    val step = (attempt / 2 + 1) * slot.h * 0.85f
                    slot.y += if (attempt % 2 == 0) -step else step
                    attempt++
                }
                placed += slot
            }

            for (slot in placed) {
                paint.textSize = slot.size
                val depth = slot.depth
                paint.maskFilter = if (depth < 0.55f) {
                    android.graphics.BlurMaskFilter((0.55f - depth) * 4.0f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                } else null
                val index = order[slot.tag] ?: -1
                paint.typeface = if (index in scores.indices) face(scores[index])
                else android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
                val alpha = (70 + 185 * depth).toInt().coerceIn(0, 255)
                paint.color = android.graphics.Color.argb(
                    alpha,
                    (0xED * depth + 0x66 * (1 - depth)).toInt(),
                    (0xE4 * depth + 0x5B * (1 - depth)).toInt(),
                    (0xD2 * depth + 0x56 * (1 - depth)).toInt()
                )
                drawContext.canvas.nativeCanvas.drawText(slot.tag, slot.x, slot.y, paint)
            }
        }
    }
}
