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
fun TagCloud(tags: List<String>, modifier: Modifier = Modifier) {
    var yaw by remember { mutableStateOf(-20f) }
    var pitch by remember { mutableStateOf(10f) }

    val spin by rememberInfiniteTransition(label = "cloud").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin"
    )

    Box(modifier.pointerInput(Unit) {
        detectDragGestures { change, drag ->
            change.consume()
            yaw += drag.x * 0.45f
            pitch = (pitch - drag.y * 0.35f).coerceIn(-70f, 70f)
        }
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

            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(
                    "sans-serif-light",
                    android.graphics.Typeface.NORMAL
                )
            }

            for ((p, tag) in rotated) {
                val depth = ((p.third + 1f) / 2f).coerceIn(0f, 1f)
                val x = cx + p.first * radius
                val y = cy + p.second * radius
                paint.textSize = size.minDimension * (0.055f + 0.075f * depth)
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
