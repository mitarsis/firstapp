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
 * Ажурная рамка в оккультном духе: двойной контур, дуги-завитки по углам,
 * точки по кромке, ромбы и «глаз» по центру каждой стороны.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFrame(width: Int, height: Int) {
    val gold = androidx.compose.ui.graphics.Color(0xFFC0A276)
    val w = width.toFloat()
    val h = height.toFloat()
    val thin = w * 0.0035f

    // внешний и внутренний контуры
    drawRoundRect(
        color = gold.copy(alpha = 0.55f),
        topLeft = androidx.compose.ui.geometry.Offset(w * 0.006f, w * 0.006f),
        size = androidx.compose.ui.geometry.Size(w - w * 0.012f, h - w * 0.012f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f, w * 0.05f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.008f)
    )
    val inset = w * 0.045f
    drawRoundRect(
        color = gold.copy(alpha = 0.30f),
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(w - inset * 2f, h - inset * 2f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.035f, w * 0.035f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = thin)
    )

    // завитки по углам: три дуги, вложенные друг в друга
    val corners = listOf(
        Triple(inset, inset, 0f),
        Triple(w - inset, inset, 90f),
        Triple(w - inset, h - inset, 180f),
        Triple(inset, h - inset, 270f)
    )
    corners.forEachIndexed { index, (cx, cy, startAngle) ->
        val radius = w * 0.075f
        for (ring in 0 until 3) {
            val r = radius * (1f - ring * 0.28f)
            drawArc(
                color = gold.copy(alpha = 0.42f - ring * 0.1f),
                startAngle = startAngle,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r),
                size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = thin)
            )
        }
        // точка в углу
        drawCircle(gold.copy(alpha = 0.55f), w * 0.008f, androidx.compose.ui.geometry.Offset(cx, cy))
        // ромбик внутрь от угла
        val dx = if (index == 0 || index == 3) 1f else -1f
        val dy = if (index == 0 || index == 1) 1f else -1f
        val point = androidx.compose.ui.geometry.Offset(cx + dx * w * 0.10f, cy + dy * w * 0.10f)
        val r2 = w * 0.010f
        val diamond = androidx.compose.ui.graphics.Path().apply {
            moveTo(point.x, point.y - r2)
            lineTo(point.x + r2, point.y)
            lineTo(point.x, point.y + r2)
            lineTo(point.x - r2, point.y)
            close()
        }
        drawPath(diamond, gold.copy(alpha = 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = thin))
    }

    // точки-бусины по кромке
    val steps = 9
    for (index in 1 until steps) {
        val fx = w * index / steps
        val fy = h * index / steps
        val alpha = if (index % 2 == 0) 0.35f else 0.20f
        drawCircle(gold.copy(alpha = alpha), w * 0.0045f, androidx.compose.ui.geometry.Offset(fx, inset))
        drawCircle(gold.copy(alpha = alpha), w * 0.0045f, androidx.compose.ui.geometry.Offset(fx, h - inset))
        drawCircle(gold.copy(alpha = alpha), w * 0.0045f, androidx.compose.ui.geometry.Offset(inset, fy))
        drawCircle(gold.copy(alpha = alpha), w * 0.0045f, androidx.compose.ui.geometry.Offset(w - inset, fy))
    }

    // «глаз» по центру верхней и нижней кромки
    listOf(inset, h - inset).forEach { y ->
        val cx = w / 2f
        drawCircle(gold.copy(alpha = 0.45f), w * 0.014f, androidx.compose.ui.geometry.Offset(cx, y),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = thin))
        drawCircle(gold.copy(alpha = 0.7f), w * 0.004f, androidx.compose.ui.geometry.Offset(cx, y))
        drawLine(gold.copy(alpha = 0.25f),
            androidx.compose.ui.geometry.Offset(cx - w * 0.10f, y),
            androidx.compose.ui.geometry.Offset(cx - w * 0.02f, y), thin)
        drawLine(gold.copy(alpha = 0.25f),
            androidx.compose.ui.geometry.Offset(cx + w * 0.02f, y),
            androidx.compose.ui.geometry.Offset(cx + w * 0.10f, y), thin)
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
