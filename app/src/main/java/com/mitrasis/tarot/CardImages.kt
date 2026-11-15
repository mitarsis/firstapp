package com.mitrasis.tarot

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
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
/** Инверсия RGB: перевёрнутая карта выглядит вывернутой и по цвету. */
private val inverted: ColorFilter = run {
    val matrix = ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    ColorFilter.colorMatrix(matrix)
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

/** Двойная золотая рамка с уголками и ромбами — поверх рисунка. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFrame(width: Int, height: Int) {
    val gold = androidx.compose.ui.graphics.Color(0xFFD9B25F)
    val w = width.toFloat()
    val h = height.toFloat()
    val outer = androidx.compose.ui.geometry.CornerRadius(w * 0.045f, w * 0.045f)

    drawRoundRect(
        color = gold.copy(alpha = 0.75f),
        topLeft = androidx.compose.ui.geometry.Offset(1f, 1f),
        size = androidx.compose.ui.geometry.Size(w - 2f, h - 2f),
        cornerRadius = outer,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.017f)
    )
    val inset = w * 0.045f
    drawRoundRect(
        color = gold.copy(alpha = 0.35f),
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(w - inset * 2f, h - inset * 2f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.03f, w * 0.03f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.006f)
    )

    // уголки-ромбы
    val r = w * 0.024f
    listOf(
        androidx.compose.ui.geometry.Offset(inset, inset),
        androidx.compose.ui.geometry.Offset(w - inset, inset),
        androidx.compose.ui.geometry.Offset(inset, h - inset),
        androidx.compose.ui.geometry.Offset(w - inset, h - inset)
    ).forEach { point ->
        val diamond = androidx.compose.ui.graphics.Path().apply {
            moveTo(point.x, point.y - r)
            lineTo(point.x + r, point.y)
            lineTo(point.x, point.y + r)
            lineTo(point.x - r, point.y)
            close()
        }
        drawPath(diamond, gold.copy(alpha = 0.85f))
    }

    // верхний орнамент: три штриха
    val cx = w / 2f
    drawLine(gold.copy(alpha = 0.5f),
        androidx.compose.ui.geometry.Offset(cx - w * 0.09f, h * 0.028f),
        androidx.compose.ui.geometry.Offset(cx + w * 0.09f, h * 0.028f),
        strokeWidth = w * 0.004f)
    drawCircle(gold.copy(alpha = 0.8f), w * 0.008f,
        androidx.compose.ui.geometry.Offset(cx, h * 0.028f))
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
        images.forEachIndexed { index, image ->
            if (image == null) return@forEachIndexed
            val isReversed = flipped.getOrElse(index) { false }
            if (isReversed) {
                // перевёрнутая карта: без поворота, только вывернутые цвета
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(width, height),
                    alpha = alphas.getOrElse(index) { 1f },
                    colorFilter = inverted,
                    blendMode = BlendMode.Screen
                )
            } else {
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(width, height),
                    alpha = alphas.getOrElse(index) { 1f },
                    blendMode = BlendMode.Screen
                )
            }
        }

        drawFrame(width, height)
    }
}
