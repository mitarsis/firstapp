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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
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
fun TagCloud(
    tags: List<String>,
    scores: List<Float> = emptyList(),
    outer: List<String> = emptyList(),
    tint: Color = Color(0xFFF2A93B),
    outerTint: Color = Color(0xFFF2A93B),
    modifier: Modifier = Modifier
) {
    var yaw by remember { mutableStateOf(-20f) }
    var pitch by remember { mutableStateOf(10f) }
    var velocity by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val tracker = remember { androidx.compose.ui.input.pointer.util.VelocityTracker() }
    var nebulaBmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var nebulaKey by remember { mutableStateOf(Float.NaN) }

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
                    val damp = (1f - 0.85f * dt).coerceIn(0f, 1f)
                    val next = velocity * damp
                    velocity = if (next.getDistance() < 2f) Offset.Zero else next
                }
            }
        }
    }

    val spin by rememberInfiniteTransition(label = "cloud").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(38000, easing = LinearEasing), RepeatMode.Restart),
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
                yaw += drag.x * 0.38f
                pitch += drag.y * 0.38f
            },
            onDragEnd = {
                dragging = false
                val raw = tracker.calculateVelocity()
                // ограничиваем рывок, иначе облако улетает в бесконечность
                val cap = 1800f
                velocity = Offset(
                    raw.x.coerceIn(-cap, cap) * 0.38f,
                    raw.y.coerceIn(-cap, cap) * 0.38f
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
            try {

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

            // цвета тегов повторяют палитру слоёв карты
            val layerColors = listOf(
                Color(0xFFF2A93B), Color(0xFF3FC6DC), Color(0xFFA45BE8)
            )

            val nebula = listOf(
                Color(0xFF6D3BD8), Color(0xFF1FA3B8), Color(0xFFC8389B),
                Color(0xFFE08A2B), Color(0xFF3F6FD8)
            )
            val breathe = 0.75f + 0.25f * (0.5f + 0.5f * sin(spin * PI.toFloat() / 180f))

            // Туманность кешируется в битмап: перерисовываем только при заметном повороте,
            // остальные кадры — просто вывод готовой картинки (это и убирает тормоза).
            val bmpW = size.width.toInt().coerceAtLeast(1)
            val bmpH = size.height.toInt().coerceAtLeast(1)
            val keyNow = yawRad.toFloat() * 57.29578f + pitch * 0.5f
            val cached = nebulaBmp
            val stale = cached == null || cached.width != bmpW || cached.height != bmpH ||
                kotlin.math.abs(keyNow - nebulaKey) > 1.2f
            val target = if (cached != null && cached.width == bmpW && cached.height == bmpH) {
                cached
            } else {
                android.graphics.Bitmap.createBitmap(bmpW, bmpH, android.graphics.Bitmap.Config.ARGB_8888).also {
                    nebulaBmp = it
                }
            }
            if (stale) nebulaKey = keyNow

            if (stale) {
                val bc = android.graphics.Canvas(target)
                bc.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR)
                val blob = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                blob.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)

                fun glow(x: Float, y: Float, r: Float, color: Int, alpha: Float) {
                    blob.shader = android.graphics.RadialGradient(
                        x, y, r,
                        android.graphics.Color.argb(
                            (alpha * 255).toInt().coerceIn(0, 255),
                            android.graphics.Color.red(color),
                            android.graphics.Color.green(color),
                            android.graphics.Color.blue(color)
                        ),
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    bc.drawCircle(x, y, r, blob)
                }

                // фоновые облака
                for (i in 0 until 2) {
                    val angle = yawRad * 0.6 + i * 1.27
                    val cloudRadius = radius * (0.60f + 0.12f * i)
                    val bx = cx + (cos(angle) * radius * 0.30).toFloat()
                    val by = cy + (sin(angle * 0.7) * radius * 0.26).toFloat()
                    glow(bx, by, cloudRadius, nebula[i % nebula.size].toArgb(), 0.13f * breathe)
                }

                // свет вокруг тегов
                for ((p, tag) in rotated) {
                    val depth = ((p.third + 1f) / 2f).coerceIn(0f, 1f)
                    val tagRadius = radius * (0.18f + 0.14f * depth)
                    val tint = nebula[Math.floorMod(tag.hashCode(), nebula.size)]
                    val bx = cx + p.first * radius * 0.85f
                    val by = cy + p.second * radius * 0.85f
                    val weightOf = scores.getOrNull(order[tag] ?: -1)
                    val importance = if (weightOf == null) 0.5f else ((weightOf - low) / span).coerceIn(0f, 1f)
                    glow(bx, by, tagRadius, tint.toArgb(), 0.07f + 0.12f * depth + 0.08f * importance)
                }
            }

            drawImage(
                image = target.asImageBitmap(),
                srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                srcSize = androidx.compose.ui.unit.IntSize(bmpW, bmpH),
                dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                dstSize = androidx.compose.ui.unit.IntSize(bmpW, bmpH)
            )

            fun face(score: Float): android.graphics.Typeface {
                val level = (score - low) / span
                val family = when {
                    level >= 0.66f -> "sans-serif-medium"
                    level >= 0.33f -> "sans-serif"
                    else -> "sans-serif-light"
                }
                return android.graphics.Typeface.create(family, android.graphics.Typeface.NORMAL)
            }

            // Внешнее кольцо: слова Старшего, вращаются медленнее и мельче —
            // видно, что Младший говорит внутри его поля
            if (outer.isNotEmpty()) {
                val outerRadius = radius * 1.0f
                val outerYawRad = Math.toRadians((yaw * 0.55 + spin * 0.7).toDouble())
                val goldenOuter = PI * (1 + sqrt(5.0))
                val outerPoints = outer.mapIndexed { index, word ->
                    val phi = acos(1 - 2 * (index + 0.5) / outer.size)
                    val theta = goldenOuter * (index + 0.5)
                    Triple(
                        (sin(phi) * cos(theta)).toFloat(),
                        cos(phi).toFloat(),
                        (sin(phi) * sin(theta)).toFloat()
                    ) to word
                }
                val outerRotated = outerPoints.map { (p, word) ->
                    val px = p.first.toDouble(); val py = p.second.toDouble(); val pz = p.third.toDouble()
                    val x1 = px * cos(outerYawRad) + pz * sin(outerYawRad)
                    val z1 = -px * sin(outerYawRad) + pz * cos(outerYawRad)
                    val y2 = py * cos(pitchRad) - z1 * sin(pitchRad)
                    val z2 = py * sin(pitchRad) + z1 * cos(pitchRad)
                    Triple(x1.toFloat(), y2.toFloat(), z2.toFloat()) to word
                }.sortedBy { it.first.third }

                // связи внутри внешней сферы
                for (a in outerRotated.indices) {
                    val neighbours = outerRotated.indices
                        .filter { it != a }
                        .sortedByDescending { b ->
                            val pa = outerRotated[a].first
                            val pb = outerRotated[b].first
                            pa.first * pb.first + pa.second * pb.second + pa.third * pb.third
                        }
                        .take(2)
                    for (b in neighbours) {
                        val pa = outerRotated[a].first
                        val pb = outerRotated[b].first
                        val depthAvg = ((pa.third + pb.third) / 2f + 1f) / 2f
                        drawLine(
                            color = Color(0xFFE8E0D2).copy(alpha = 0.12f + 0.26f * depthAvg),
                            start = Offset(cx + pa.first * outerRadius, cy + pa.second * outerRadius),
                            end = Offset(cx + pb.first * outerRadius, cy + pb.second * outerRadius),
                            strokeWidth = 1.1f
                        )
                    }
                }

                val outerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = android.graphics.Paint.Align.CENTER
                    isSubpixelText = true
                    isLinearText = true
                    typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
                }
                for ((p, word) in outerRotated) {
                    val depth = ((p.third + 1f) / 2f).coerceIn(0f, 1f)
                    outerPaint.textSize = size.minDimension * (0.040f + 0.030f * depth)
                    val alpha = (45 + 110 * depth).toInt().coerceIn(0, 255)
                    val argb = outerTint.toArgb()
                    outerPaint.color = android.graphics.Color.argb(
                        (alpha * 0.8f).toInt().coerceIn(0, 255),
                        android.graphics.Color.red(argb),
                        android.graphics.Color.green(argb),
                        android.graphics.Color.blue(argb)
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        word, cx + p.first * outerRadius, cy + p.second * outerRadius, outerPaint
                    )
                }
            }

            // созвездие: каждый тег связан с двумя ближайшими на сфере
            for (a in rotated.indices) {
                val neighbours = rotated.indices
                    .filter { it != a }
                    .sortedByDescending { b ->
                        val pa = rotated[a].first
                        val pb = rotated[b].first
                        pa.first * pb.first + pa.second * pb.second + pa.third * pb.third
                    }
                    .take(2)
                for (b in neighbours) {
                    val pa = rotated[a].first
                    val pb = rotated[b].first
                    val depthAvg = ((pa.third + pb.third) / 2f + 1f) / 2f
                    drawLine(
                        color = Color(0xFFE8E0D2).copy(alpha = 0.12f + 0.26f * depthAvg),
                        start = Offset(cx + pa.first * radius, cy + pa.second * radius),
                        end = Offset(cx + pb.first * radius, cy + pb.second * radius),
                        strokeWidth = 1.2f
                    )
                }
            }

            val blurCache = Array(5) { index ->
                android.graphics.BlurMaskFilter((index + 1).toFloat(), android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = android.graphics.Paint.Align.CENTER
                // без этих двух флагов Android округляет кегль и позицию до пикселя,
                // отсюда «ступеньки» при вращении сферы
                isSubpixelText = true
                isLinearText = true
                typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            }

            // внутренняя сфера сжата: поле Старшего должно быть снаружи
            val innerRadius = if (outer.isEmpty()) radius else radius * 0.60f
            for ((p, tag) in rotated) {
                val depth = ((p.third + 1f) / 2f).coerceIn(0f, 1f)
                val x = cx + p.first * innerRadius
                val y = cy + p.second * innerRadius
                paint.textSize = size.minDimension * (0.055f + 0.075f * depth)
                paint.maskFilter = if (depth < 0.55f) {
                    // берём готовый фильтр из кеша: радиус всегда 1..5, нулевого не бывает
                    val level = (((0.55f - depth) * 6f).toInt()).coerceIn(1, 5)
                    blurCache[level - 1]
                } else null
                val index = order[tag] ?: -1
                paint.typeface = if (index in scores.indices) face(scores[index])
                else android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
                val alpha = (60 + 195 * depth).toInt().coerceIn(0, 255)
                val argb = tint.toArgb()
                paint.color = android.graphics.Color.argb(
                    alpha,
                    android.graphics.Color.red(argb),
                    android.graphics.Color.green(argb),
                    android.graphics.Color.blue(argb)
                )
                drawContext.canvas.nativeCanvas.drawText(tag, x, y, paint)
            }
            } catch (t: Throwable) {
                // рисование не должно ронять ответ
            }
        }
    }
}
