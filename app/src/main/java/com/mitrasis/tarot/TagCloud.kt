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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Облако тегов на сфере: вращается само, тянется пальцем.
 * Дальние слова мельче и бледнее, ближние крупнее — это и даёт глубину.
 */
@Composable
fun TagCloud(tags: List<String>, scores: List<Float> = emptyList(), modifier: Modifier = Modifier) {
    var yaw by remember { mutableStateOf(-20f) }
    var pitch by remember { mutableStateOf(10f) }

    val spin by rememberInfiniteTransition(label = "cloud").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(60000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin"
    )

    Box(modifier.pointerInput(Unit) {
        detectDragGestures { change, drag ->
            change.consume()
            yaw += drag.x * 0.22f
            pitch += drag.y * 0.22f
            pitch %= 360f
        }
    }) {
        Canvas(Modifier.fillMaxSize()) {
            if (tags.isEmpty()) return@Canvas
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = minOf(cx, cy) * 0.82f

            // Туманность: облака света привязаны к тегам — их положение и глубина
            // берутся из той же сферы, поэтому картинка «растёт» из расклада.
            val nebula = listOf(
                Color(0xFF6D3BD8), Color(0xFF1FA3B8), Color(0xFFC8389B),
                Color(0xFFE08A2B), Color(0xFF3F6FD8)
            )
            val breathe = 0.75f + 0.25f * (0.5f + 0.5f * sin(yaw * PI.toFloat() / 180f))

            // общий фон: несколько больших облаков, медленно плывущих вокруг оси
            for (i in 0 until 5) {
                val angle = yawRad * 0.6 + i * 1.27
                val cloudRadius = radius * (0.55f + 0.10f * i)
                val bx = cx + (cos(angle) * radius * 0.30).toFloat()
                val by = cy + (sin(angle * 0.7) * radius * 0.26).toFloat()
                val tint = nebula[i % nebula.size]
                drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            tint.copy(alpha = 0.13f * breathe),
                            tint.copy(alpha = 0.05f * breathe),
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(bx, by),
                        radius = cloudRadius
                    ),
                    radius = cloudRadius,
                    center = androidx.compose.ui.geometry.Offset(bx, by),
                    blendMode = androidx.compose.ui.graphics.BlendMode.Screen
                )
            }
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

            // значимость — не размером (он занят глубиной), а насыщенностью шрифта
            val high = scores.maxOrNull() ?: 1f
            val low = scores.minOrNull() ?: 0f
            val span = (high - low).coerceAtLeast(1e-4f)
            fun face(score: Float): android.graphics.Typeface {
                val level = ((score - low) / span)
                val style = when {
                    level >= 0.66f -> android.graphics.Typeface.NORMAL
                    level >= 0.33f -> android.graphics.Typeface.NORMAL
                    else -> android.graphics.Typeface.NORMAL
                }
                val family = when {
                    level >= 0.66f -> "sans-serif-medium"
                    level >= 0.33f -> "sans-serif"
                    else -> "sans-serif-light"
                }
                return android.graphics.Typeface.create(family, style)
            }
            // свет вокруг каждого тега: чем ближе к зрителю, тем плотнее
            for ((p, tag) in rotated) {
                val depth = ((p.third + 1f) / 2f).coerceIn(0f, 1f)
                val tagRadius = radius * (0.22f + 0.18f * depth)
                val tint = nebula[Math.floorMod(tag.hashCode(), nebula.size)]
                val bx = cx + p.first * radius * 0.85f
                val by = cy + p.second * radius * 0.85f
                drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            tint.copy(alpha = 0.10f + 0.10f * depth),
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(bx, by),
                        radius = tagRadius
                    ),
                    radius = tagRadius,
                    center = androidx.compose.ui.geometry.Offset(bx, by),
                    blendMode = androidx.compose.ui.graphics.BlendMode.Screen
                )
            }

            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            }

            for ((p, tag) in rotated) {
                val depth = ((p.third + 1f) / 2f).coerceIn(0f, 1f)
                val x = cx + p.first * radius
                val y = cy + p.second * radius
                paint.textSize = size.minDimension * (0.055f + 0.075f * depth)
                val index = tagIndex[tag] ?: -1
                paint.typeface = if (index in scores.indices) face(scores[index])
                else android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
                val alpha = (60 + 195 * depth).toInt().coerceIn(0, 255)
                paint.color = android.graphics.Color.argb(
                    alpha,
                    (0xED * depth + 0x60 * (1 - depth)).toInt(),
                    (0xE4 * depth + 0x55 * (1 - depth)).toInt(),
                    (0xD2 * depth + 0x50 * (1 - depth)).toInt()
                )
                drawContext.canvas.nativeCanvas.drawText(tag, x, y, paint)
            }
        }
    }
}
