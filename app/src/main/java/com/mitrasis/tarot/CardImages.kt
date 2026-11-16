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
        0 -> Color(0xFFE7B15C)   // янтарь
        1 -> Color(0xFF5EC9D6)   // бирюза
        else -> Color(0xFFB07BE3) // пурпур
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

    private val cache = HashMap<Int, ImageBitmap>()

    fun load(context: Context, cardId: Int): ImageBitmap? {
        cache[cardId]?.let { return it }
        val name = "cards/%02d.webp".format(cardId)
        return try {
            context.assets.open(name).use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream) ?: return null
                val image = bitmap.asImageBitmap()
                cache[cardId] = image
                image
            }
        } catch (t: Throwable) {
            null
        }
    }
}

/**
 * Гравюрная рамка: линия «от руки» с дрожью, а по кромке — алхимические
 * глифы (солнце, луна, стихии, пентаграмма). Ничего веб-дизайнерского.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFrame(width: Int, height: Int) {
    val ink = androidx.compose.ui.graphics.Color(0xFFBFA37A)
    val w = width.toFloat()
    val h = height.toFloat()

    fun wobblyPath(inset: Float, amplitude: Float, seed: Int): androidx.compose.ui.graphics.Path {
        val path = androidx.compose.ui.graphics.Path()
        val steps = 48
        var random = seed
        fun jitter(): Float {
            random = random * 1103515245 + 12345
            return ((random ushr 16 and 0xFF) / 255f - 0.5f) * 2f * amplitude
        }
        // верх
        for (i in 0..steps) {
            val x = inset + (w - inset * 2f) * i / steps
            val y = inset + jitter()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        // право
        for (i in 0..steps) {
            val y = inset + (h - inset * 2f) * i / steps
            path.lineTo(w - inset + jitter(), y)
        }
        // низ
        for (i in steps downTo 0) {
            val x = inset + (w - inset * 2f) * i / steps
            path.lineTo(x, h - inset + jitter())
        }
        // лево
        for (i in steps downTo 0) {
            val y = inset + (h - inset * 2f) * i / steps
            path.lineTo(inset + jitter(), y)
        }
        path.close()
        return path
    }

    drawPath(wobblyPath(w * 0.02f, w * 0.0035f, 17), ink.copy(alpha = 0.55f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.006f))
    val innerInset = w * 0.055f
    drawPath(wobblyPath(innerInset, w * 0.0025f, 91), ink.copy(alpha = 0.28f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.003f))

    // глифы по кромке: солнце, луна, треугольник, квадрат, пентаграмма
    val glyphRadius = w * 0.013f
    fun glyph(index: Int, center: androidx.compose.ui.geometry.Offset) {
        val color = ink.copy(alpha = 0.55f)
        val thin = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.0028f)
        when (index % 5) {
            0 -> { // солнце
                drawCircle(color, glyphRadius, center, style = thin)
                drawCircle(color, glyphRadius * 0.22f, center)
            }
            1 -> { // луна
                drawArc(color, 60f, 240f, false,
                    androidx.compose.ui.geometry.Offset(center.x - glyphRadius, center.y - glyphRadius),
                    androidx.compose.ui.geometry.Size(glyphRadius * 2f, glyphRadius * 2f), style = thin)
            }
            2 -> { // треугольник
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(center.x, center.y - glyphRadius)
                    lineTo(center.x + glyphRadius, center.y + glyphRadius * 0.8f)
                    lineTo(center.x - glyphRadius, center.y + glyphRadius * 0.8f)
                    close()
                }
                drawPath(path, color, style = thin)
            }
            3 -> { // квадрат
                drawRect(color,
                    androidx.compose.ui.geometry.Offset(center.x - glyphRadius * 0.75f, center.y - glyphRadius * 0.75f),
                    androidx.compose.ui.geometry.Size(glyphRadius * 1.5f, glyphRadius * 1.5f), style = thin)
            }
            else -> { // пентаграмма
                val path = androidx.compose.ui.graphics.Path()
                for (point in 0 until 5) {
                    val angle = Math.toRadians(144.0 * point - 90.0)
                    val x = center.x + (glyphRadius * cos(angle)).toFloat()
                    val y = center.y + (glyphRadius * sin(angle)).toFloat()
                    if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color, style = thin)
            }
        }
    }

    val margin = w * 0.037f
    val positions = listOf(
        androidx.compose.ui.geometry.Offset(w * 0.30f, margin),
        androidx.compose.ui.geometry.Offset(w * 0.70f, margin),
        androidx.compose.ui.geometry.Offset(w - margin, h * 0.30f),
        androidx.compose.ui.geometry.Offset(w - margin, h * 0.70f),
        androidx.compose.ui.geometry.Offset(w * 0.70f, h - margin),
        androidx.compose.ui.geometry.Offset(w * 0.30f, h - margin),
        androidx.compose.ui.geometry.Offset(margin, h * 0.70f),
        androidx.compose.ui.geometry.Offset(margin, h * 0.30f)
    )
    positions.forEachIndexed { index, point -> glyph(index, point) }

    // центральный медальон сверху и снизу
    listOf(margin, h - margin).forEach { y ->
        val cx = w / 2f
        drawCircle(ink.copy(alpha = 0.5f), w * 0.019f,
            androidx.compose.ui.geometry.Offset(cx, y),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.003f))
        drawCircle(ink.copy(alpha = 0.75f), w * 0.005f, androidx.compose.ui.geometry.Offset(cx, y))
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
        (0.30f + 0.70f * share).coerceIn(0.25f, 1f)
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
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(width, height),
                alpha = alphas.getOrElse(index) { 1f },
                colorFilter = ColorFilter.tint(positionTint(index, isReversed), BlendMode.SrcIn),
                blendMode = BlendMode.Screen
            )
        }

        drawFrame(width, height)
    }
}
