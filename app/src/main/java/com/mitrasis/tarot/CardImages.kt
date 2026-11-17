package com.mitrasis.tarot

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
fun positionTint(index: Int, reversed: Boolean): Color {
    val base = when (index) {
        0 -> Color(0xFFFFD79A)   // янтарь
        1 -> Color(0xFF8FE6F0)   // бирюза
        else -> Color(0xFFD2AAF5) // пурпур
    }
    return if (reversed) {
        Color(
            red = base.red * 0.55f,
            green = base.green * 0.5f,
            blue = (base.blue * 0.85f).coerceAtLeast(0.5f)
        )
    } else {
        base
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
@Composable
fun CardStack(
    context: Context,
    cards: List<TarotCard>,
    probabilities: List<Float>,
    reversed: List<Boolean> = emptyList(),
    modifier: Modifier = Modifier
) {
    val images = cards.map { rememberCardImage(context, it.id) }
    val flipped = images.mapIndexed { index, _ -> reversed.getOrElse(index) { false } }

    // Акцент на карте пропорционален её вероятности: главная всегда плотная,
    // остальные тем прозрачнее, чем слабее их шанс.
    val strongest = probabilities.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val alphas = probabilities.map { probability ->
        val share = (probability / strongest).coerceIn(0f, 1f)
        (0.55f + 0.45f * share).coerceIn(0.5f, 1f)
    }

    Canvas(modifier = modifier.aspectRatio(0.62f)) {
        val width = size.width.toInt()
        val height = size.height.toInt()

        // Тёмная подложка: штрихи карт светлые, иначе в светлой теме их не видно
        drawRoundRect(
            color = androidx.compose.ui.graphics.Color(0xFF14111C),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                size.width * 0.045f, size.width * 0.045f
            )
        )
        images.forEachIndexed { index, image ->
            if (image == null) return@forEachIndexed
            val isReversed = flipped.getOrElse(index) { false }
            // глубина: чем дальше слой, тем он мельче — эффект вложенности
            val depthScale = when (index) {
                0 -> 1f
                1 -> 0.93f
                else -> 0.86f
            }
            val layerWidth = (width * depthScale).toInt()
            val layerHeight = (height * depthScale).toInt()
            val dx = (width - layerWidth) / 2
            val dy = (height - layerHeight) / 2

            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset(dx, dy),
                dstSize = IntSize(layerWidth, layerHeight),
                alpha = alphas.getOrElse(index) { 1f },
                colorFilter = ColorFilter.tint(positionTint(index, isReversed), BlendMode.SrcIn),
                blendMode = BlendMode.Screen
            )
        }

        // глубина: тёмный омут по краям, светлее к центру
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(
                    androidx.compose.ui.graphics.Color.Transparent,
                    androidx.compose.ui.graphics.Color(0x550A0710),
                    androidx.compose.ui.graphics.Color(0xA00A0710)
                ),
                center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.45f),
                radius = size.height * 0.62f
            )
        )

        // круги на воде — намёк на воронку
        repeat(3) { ring ->
            val radius = size.width * (0.30f + ring * 0.11f)
            drawCircle(
                color = androidx.compose.ui.graphics.Color(0xFFBFA37A).copy(alpha = 0.05f),
                radius = radius,
                center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.45f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.width * 0.002f)
            )
        }

        drawFrame(width, height)
    }
}
