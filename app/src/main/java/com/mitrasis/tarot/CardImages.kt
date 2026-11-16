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
 * Обод зеркала: широкий скошенный кант с бликом, тёмная фаска,
 * зубчатая кромка внутри и накладки по углам.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFrame(width: Int, height: Int) {
    val w = width.toFloat()
    val h = height.toFloat()
    val brass = androidx.compose.ui.graphics.Color(0xFFC9A26B)
    val light = androidx.compose.ui.graphics.Color(0xFFEBD8B2)
    val dark = androidx.compose.ui.graphics.Color(0xFF5A4527)

    // скошенный кант: градиент от блика к тени
    val rim = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(light, brass, dark),
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(w, h)
    )
    val rimThickness = w * 0.030f
    drawRoundRect(
        brush = rim,
        topLeft = androidx.compose.ui.geometry.Offset(rimThickness / 2f, rimThickness / 2f),
        size = androidx.compose.ui.geometry.Size(w - rimThickness, h - rimThickness),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.07f, w * 0.07f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = rimThickness)
    )

    // тёмная фаска внутри канта
    val bevelInset = rimThickness * 1.05f
    drawRoundRect(
        color = androidx.compose.ui.graphics.Color(0xCC120E08),
        topLeft = androidx.compose.ui.geometry.Offset(bevelInset, bevelInset),
        size = androidx.compose.ui.geometry.Size(w - bevelInset * 2f, h - bevelInset * 2f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.055f, w * 0.055f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.008f)
    )

    // зубчатая кромка: мелкие дуги по периметру
    val toothRadius = w * 0.011f
    val inner = bevelInset + w * 0.018f
    val stepsX = 11
    val stepsY = 16
    for (i in 1 until stepsX) {
        val x = inner + (w - inner * 2f) * i / stepsX
        drawArc(brass.copy(alpha = 0.45f), 0f, 180f, false,
            androidx.compose.ui.geometry.Offset(x - toothRadius, inner - toothRadius),
            androidx.compose.ui.geometry.Size(toothRadius * 2f, toothRadius * 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.0025f))
        drawArc(brass.copy(alpha = 0.45f), 180f, 180f, false,
            androidx.compose.ui.geometry.Offset(x - toothRadius, h - inner - toothRadius),
            androidx.compose.ui.geometry.Size(toothRadius * 2f, toothRadius * 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.0025f))
    }
    for (i in 1 until stepsY) {
        val y = inner + (h - inner * 2f) * i / stepsY
        drawArc(brass.copy(alpha = 0.45f), 90f, 180f, false,
            androidx.compose.ui.geometry.Offset(inner - toothRadius, y - toothRadius),
            androidx.compose.ui.geometry.Size(toothRadius * 2f, toothRadius * 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.0025f))
        drawArc(brass.copy(alpha = 0.45f), 270f, 180f, false,
            androidx.compose.ui.geometry.Offset(w - inner - toothRadius, y - toothRadius),
            androidx.compose.ui.geometry.Size(toothRadius * 2f, toothRadius * 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.0025f))
    }

    // накладки по углам: розетка из трёх кругов
    val bossRadius = w * 0.030f
    listOf(
        androidx.compose.ui.geometry.Offset(rimThickness, rimThickness),
        androidx.compose.ui.geometry.Offset(w - rimThickness, rimThickness),
        androidx.compose.ui.geometry.Offset(w - rimThickness, h - rimThickness),
        androidx.compose.ui.geometry.Offset(rimThickness, h - rimThickness)
    ).forEach { point ->
        drawCircle(dark.copy(alpha = 0.85f), bossRadius, point)
        drawCircle(brass.copy(alpha = 0.9f), bossRadius, point,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.004f))
        drawCircle(light.copy(alpha = 0.75f), bossRadius * 0.35f, point)
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
                    androidx.compose.ui.graphics.Color(0x880A0710),
                    androidx.compose.ui.graphics.Color(0xE00A0710)
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
