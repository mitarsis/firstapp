package com.mitrasis.tarot

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.unit.IntSize

/**
 * Готовые иллюстрации вместо нарисованных кодом.
 *
 * В assets/cards лежат 78 контурных изображений, сделанных из сканов
 * Райдера-Уэйта (общественное достояние): тёмные штрихи исходника стали
 * цветными чернилами масти, фон прозрачный. Прозрачность принципиальна —
 * три карты накладываются друг на друга, как в Transparent Tarot, и
 * складываются в общий рисунок.
 */
/**
 * Цвет позиции: по нему видно, где чей слой при наложении.
 * Перевёрнутая карта того же цвета, но притушенная и сдвинутая в фиолетовый.
 */
fun positionTint(index: Int, reversed: Boolean, total: Int = 3): Color {
    // Чистые каналы: слои не перекрывают друг друга, а складываются по цвету.
    // Для двух карт берём красный и синий — они различаются заметнее.
    if (total <= 2) {
        return if (index == 0) Color(0xFFFF3B3B) else Color(0xFF4C7BFF)
    }
    return when (index) {
        0 -> Color(0xFFFF3B3B)
        1 -> Color(0xFF3BFF7A)
        else -> Color(0xFF4C7BFF)
    }
}

object CardImages {

    // Ограниченный кэш: 78 карт по 300×520 в память целиком не нужны.
    private val cache = object : android.util.LruCache<Int, ImageBitmap>(MEMORY_BUDGET_KB) {
        override fun sizeOf(key: Int, value: ImageBitmap): Int =
            value.width * value.height * 4 / 1024
    }

    private const val MEMORY_BUDGET_KB = 12 * 1024

    fun load(context: Context, cardId: Int): ImageBitmap? {
        cache.get(cardId)?.let { return it }
        val name = "cards/%02d.webp".format(cardId)
        return try {
            context.assets.open(name).use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream) ?: return null
                val image = bitmap.asImageBitmap()
                cache.put(cardId, image)
                image
            }
        } catch (t: Throwable) {
            null
        }
    }
}

/**
 * Шершавая гравюрная рамка: несколько неровных процарапанных обводов
 * с пропусками, угловая штриховка и крапины — как у старой печатной доски.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFrame(width: Int, height: Int) {
    val ink = androidx.compose.ui.graphics.Color(0xFFC0A276)
    val w = width.toFloat()
    val h = height.toFloat()

    fun wornSide(
        from: androidx.compose.ui.geometry.Offset,
        to: androidx.compose.ui.geometry.Offset,
        seed: Int,
        alpha: Float,
        strokeWidth: Float
    ) {
        var state = seed
        fun rnd(): Float {
            state = state * 1103515245 + 12345
            return ((state ushr 16) and 0xFF) / 255f
        }
        val steps = 64
        var previous = from
        for (step in 1..steps) {
            val t = step / steps.toFloat()
            val x = from.x + (to.x - from.x) * t + (rnd() - 0.5f) * w * 0.004f
            val y = from.y + (to.y - from.y) * t + (rnd() - 0.5f) * w * 0.004f
            val current = androidx.compose.ui.geometry.Offset(x, y)
            val gap = rnd() < 0.055f
            if (!gap) {
                drawLine(
                    color = ink.copy(alpha = alpha),
                    start = previous,
                    end = current,
                    strokeWidth = strokeWidth
                )
            }
            previous = current
        }
    }

    // три процарапанных обвода
    for (pass in 0..2) {
        val inset = w * (0.018f + pass * 0.007f)
        val alpha = 0.40f - pass * 0.10f
        val stroke = w * (0.0050f - pass * 0.0011f)
        wornSide(androidx.compose.ui.geometry.Offset(inset, inset),
            androidx.compose.ui.geometry.Offset(w - inset, inset), 11 + pass, alpha, stroke)
        wornSide(androidx.compose.ui.geometry.Offset(w - inset, inset),
            androidx.compose.ui.geometry.Offset(w - inset, h - inset), 37 + pass, alpha, stroke)
        wornSide(androidx.compose.ui.geometry.Offset(w - inset, h - inset),
            androidx.compose.ui.geometry.Offset(inset, h - inset), 71 + pass, alpha, stroke)
        wornSide(androidx.compose.ui.geometry.Offset(inset, h - inset),
            androidx.compose.ui.geometry.Offset(inset, inset), 97 + pass, alpha, stroke)
    }

    // угловая штриховка в четыре линии
    val cornerInset = w * 0.030f
    val hatchLength = w * 0.045f
    val hatchGap = w * 0.011f
    val corners = listOf(
        Triple(cornerInset, cornerInset, 1f),
        Triple(w - cornerInset, cornerInset, -1f),
        Triple(w - cornerInset, h - cornerInset, -1f),
        Triple(cornerInset, h - cornerInset, 1f)
    )
    corners.forEach { (cx, cy, direction) ->
        for (line in 0 until 4) {
            val shift = line * hatchGap
            drawLine(
                color = ink.copy(alpha = 0.22f - line * 0.04f),
                start = androidx.compose.ui.geometry.Offset(cx + direction * shift, cy),
                end = androidx.compose.ui.geometry.Offset(
                    cx + direction * (shift + hatchLength), cy + hatchLength
                ),
                strokeWidth = w * 0.0022f
            )
        }
    }

    // крапины по кромке
    var scatter = 1234
    fun noise(): Float {
        scatter = scatter * 1103515245 + 12345
        return ((scatter ushr 16) and 0xFF) / 255f
    }
    repeat(26) {
        val x = noise() * w
        val y = noise() * h
        drawCircle(ink.copy(alpha = 0.06f + noise() * 0.10f), w * 0.0022f,
            androidx.compose.ui.geometry.Offset(x, y))
    }
}

@Composable
fun rememberCardImage(context: Context, cardId: Int): ImageBitmap? =
    remember(cardId) { CardImages.load(context, cardId) }

/**
 * Наложение карт друг на друга: каждая следующая добавляется в режиме Screen,
 * поэтому штрихи не затирают друг друга, а складываются.
 */
/** Три растровые сетки под углами 15°, 45°, 75° — своя на каждый слой. */
private fun buildHalftone(angle: Float, cell: Int = 7, dot: Float = 2.1f): androidx.compose.ui.graphics.Brush {
    val bitmap = androidx.compose.ui.graphics.ImageBitmap(96, 96)
    val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
    val paint = androidx.compose.ui.graphics.Paint().apply {
        color = androidx.compose.ui.graphics.Color.Black
        isAntiAlias = true
    }
    val radians = Math.toRadians(angle.toDouble())
    val cosA = kotlin.math.cos(radians).toFloat()
    val sinA = kotlin.math.sin(radians).toFloat()
    for (gx in -96..192 step cell) {
        for (gy in -96..192 step cell) {
            val x = gx * cosA - gy * sinA
            val y = gx * sinA + gy * cosA
            canvas.drawCircle(androidx.compose.ui.geometry.Offset(x, y), dot, paint)
        }
    }
    val shader = androidx.compose.ui.graphics.ImageShader(
        bitmap,
        androidx.compose.ui.graphics.TileMode.Repeated,
        androidx.compose.ui.graphics.TileMode.Repeated
    )
    return androidx.compose.ui.graphics.ShaderBrush(shader)
}

/**
 * Цвет канала с учётом сдвига: при долгом нажатии каналы перетекают
 * друг в друга, поэтому между двумя цветами интерполируем.
 */
fun channelColor(index: Int, shift: Float, total: Int): Color {
    val palette = if (total <= 2) {
        listOf(Color(0xFFF2A93B), Color(0xFF3FC6DC))
    } else {
        listOf(Color(0xFFF2A93B), Color(0xFF3FC6DC), Color(0xFFA45BE8))
    }
    val size = palette.size
    val position = (((index + shift) % size) + size) % size
    val base = position.toInt()
    val fraction = position - base
    return androidx.compose.ui.graphics.lerp(
        palette[base % size],
        palette[(base + 1) % size],
        fraction
    )
}

@Composable
fun CardStack(
    context: Context,
    cards: List<TarotCard>,
    probabilities: List<Float>,
    reversed: List<Boolean> = emptyList(),
    focusIndex: Int = -1,
    parallax: Pair<Float, Float> = 0f to 0f,
    channelShift: Float = 0f,
    tinted: Boolean = true,
    modifier: Modifier = Modifier
) {
    val images = cards.map { rememberCardImage(context, it.id) }

    val strongest = probabilities.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val alphas = probabilities.mapIndexed { index, probability ->
        if (focusIndex >= 0) {
            if (index == focusIndex) 1f else 0f
        } else {
            val share = (probability / strongest).coerceIn(0f, 1f)
            (0.55f + 0.45f * share).coerceIn(0.5f, 1f)
        }
    }

    // бесконечное плаванье слоёв: не зависит от телефона и не дёргается
    val drift = rememberInfiniteTransition(label = "drift")
    val phase by drift.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing)),
        label = "phase"
    )

    Canvas(modifier = modifier.aspectRatio(0.62f)) {
        val w = size.width
        val h = size.height
        val corner = androidx.compose.ui.geometry.CornerRadius(w * 0.045f, w * 0.045f)

        drawRoundRect(color = Color(0xFF14111C), cornerRadius = corner)
        drawRoundRect(
            brush = Brush.linearGradient(colors = listOf(Color(0xFF1A1622), Color(0xFF0E0B12))),
            cornerRadius = corner
        )

        val clip = androidx.compose.ui.graphics.Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    androidx.compose.ui.geometry.Rect(w * 0.03f, h * 0.03f, w - w * 0.03f, h - h * 0.03f),
                    androidx.compose.ui.geometry.CornerRadius(w * 0.04f, w * 0.04f)
                )
            )
        }

        clipPath(clip) {
            images.forEachIndexed { index, image ->
                if (image == null) return@forEachIndexed
                val layerAlpha = alphas.getOrElse(index) { 0f }
                if (layerAlpha <= 0.01f) return@forEachIndexed

                // все слои одного размера: различаются цветом и прозрачностью,
                // а не масштабом — иначе третья карта выглядит сдвинутой
                val fit = minOf(w / image.width.toFloat(), h / image.height.toFloat())
                val dstW = (image.width * fit).toInt()
                val dstH = (image.height * fit).toInt()

                // Карта всегда занимает рамку целиком: никаких сдвигов.
                // Вместо движения — линза: по изображению бежит волна искажения.
                val weight = 1f - index * 0.20f
                val lensScale = if (cards.size > 1 && focusIndex < 0) 1f else 0.45f
                val dx = (w - dstW) / 2f
                val dy = (h - dstH) / 2f

                // мягкое свечение: тот же слой, чуть крупнее и прозрачнее
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset((dx - dstW * 0.012f).toInt(), (dy - dstH * 0.012f).toInt()),
                    dstSize = IntSize((dstW * 1.024f).toInt(), (dstH * 1.024f).toInt()),
                    alpha = layerAlpha * 0.35f,
                    colorFilter = if (tinted) {
                        ColorFilter.tint(
                            channelColor(index, channelShift, cards.size).copy(alpha = 0.55f),
                            BlendMode.SrcIn
                        )
                    } else {
                        null
                    },
                    blendMode = BlendMode.Screen
                )

                // линия рисуется полосами: волна проходит сквозь карту, а не двигает её
                val strips = 34
                for (strip in 0 until strips) {
                    val top = dy + dstH * strip / strips
                    val bottom = dy + dstH * (strip + 1) / strips
                    val srcTop = image.height * strip / strips
                    val srcBottom = image.height * (strip + 1) / strips
                    val t = strip / (strips - 1f)
                    val wave = sin(phase + t * 6.0f + index * 0.9f) * dstW * 0.018f * weight * lensScale
                    drawImage(
                        image = image,
                        srcOffset = IntOffset(0, srcTop),
                        srcSize = IntSize(image.width, (srcBottom - srcTop).coerceAtLeast(1)),
                        dstOffset = IntOffset((dx + wave).toInt(), top.toInt()),
                        dstSize = IntSize(dstW, (bottom - top).toInt().coerceAtLeast(1)),
                        alpha = layerAlpha,
                        colorFilter = if (tinted) {
                            ColorFilter.tint(
                                channelColor(index, channelShift, cards.size),
                                BlendMode.SrcIn
                            )
                        } else {
                            null
                        },
                        blendMode = BlendMode.Screen
                    )
                }
            }
        }

        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(
                    w * (0.5f + parallax.first * 0.35f),
                    h * (0.5f + parallax.second * 0.35f)
                ),
                radius = w * 0.75f
            ),
            cornerRadius = corner
        )

        drawFrame(w.toInt(), h.toInt())
    }
}
