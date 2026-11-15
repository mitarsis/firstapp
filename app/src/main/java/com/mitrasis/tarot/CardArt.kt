package com.mitrasis.tarot

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Иллюстрация не из файлов, а из кода.
 *
 * Слои:
 *  1. градиент по масти (Старшие — фиолетово-золотые);
 *  2. ореол: полупрозрачные пятна второй и третьей гипотез, яркость = их вероятность;
 *  3. сигил — детерминированный узор из хеша названия карты, уникален для каждой из 78;
 *  4. пипсы по рангу (или литера двора), римская цифра для Старших;
 *  5. рамка, толщина и яркость которой зависят от уверенности модели.
 */
@Composable
fun CardArt(
    card: TarotCard,
    probability: Float,
    alternatives: List<Pair<TarotCard, Float>>,
    modifier: Modifier = Modifier
) {
    val palette = paletteFor(card)
    val seed = card.titleEn.hashCode()

    Canvas(modifier = modifier.aspectRatio(0.62f)) {
        val corner = CornerRadius(size.width * 0.08f, size.width * 0.08f)

        drawRoundRect(
            brush = Brush.verticalGradient(listOf(palette.top, palette.bottom)),
            cornerRadius = corner
        )

        // ореол гипотез
        alternatives.take(2).forEachIndexed { index, (_, weight) ->
            val alpha = (weight * 2.2f).coerceIn(0.04f, 0.45f)
            val radius = size.minDimension * (0.30f - index * 0.06f)
            val angle = ((seed shr (index * 5)) % 360).toFloat()
            val offset = size.minDimension * (0.13f + index * 0.05f)
            val center = Offset(
                x = size.width / 2f + cos(Math.toRadians(angle.toDouble())).toFloat() * offset,
                y = size.height / 2f + sin(Math.toRadians(angle.toDouble())).toFloat() * offset
            )
            drawCircle(
                color = palette.accent.copy(alpha = alpha),
                radius = radius,
                center = center
            )
        }

        // сигил
        val petals = 3 + abs(seed % 6)
        val rotation = (abs(seed / 7) % 360).toFloat()
        val sharpness = probability.coerceIn(0.15f, 1f)
        val radius = size.minDimension * (0.30f - 0.08f * (1f - sharpness))
        val center = Offset(size.width / 2f, size.height / 2f)

        rotate(rotation, center) {
            repeat(petals) { petal ->
                val angle = 360f / petals * petal
                val tipX = center.x + cos(Math.toRadians(angle.toDouble())).toFloat() * radius
                val tipY = center.y + sin(Math.toRadians(angle.toDouble())).toFloat() * radius
                drawLine(
                    color = palette.accent.copy(alpha = 0.20f + 0.65f * sharpness),
                    start = center,
                    end = Offset(tipX, tipY),
                    strokeWidth = 1.5f + 3f * sharpness
                )
                drawCircle(
                    color = palette.accent.copy(alpha = 0.30f + 0.5f * sharpness),
                    radius = size.minDimension * 0.022f,
                    center = Offset(tipX, tipY)
                )
            }
            drawCircle(
                color = palette.accent.copy(alpha = 0.85f),
                radius = size.minDimension * 0.035f,
                center = center
            )
            drawCircle(
                color = palette.accent.copy(alpha = 0.5f),
                radius = size.minDimension * 0.10f,
                center = center,
                style = Stroke(width = 1.5f)
            )
        }

        drawPips(card, palette, seed)
        drawLabel(card, palette)

        // рамка: чем увереннее ответ, тем чётче
        drawRoundRect(
            color = palette.accent.copy(alpha = 0.35f + 0.45f * probability.coerceIn(0f, 1f)),
            cornerRadius = corner,
            style = Stroke(width = 1.5f + 2f * probability.coerceIn(0f, 1f))
        )
    }
}

/** Раскладка пипсов как на игральных картах: 1-10 по рангу, дворы — литера. */
private fun DrawScope.drawPips(card: TarotCard, palette: Palette, seed: Int) {
    if (card.arcana == "Старший аркан") return

    val rank = card.title.substringBefore(' ')
    val count = rankCount(rank)
    val center = Offset(size.width / 2f, size.height / 2f)
    val step = size.width * 0.17f

    if (count == null) {
        // двор: одна крупная фигура с литерой
        val letter = courtLetter(rank)
        drawCircle(
            color = palette.accent.copy(alpha = 0.16f),
            radius = size.minDimension * 0.26f,
            center = center,
            style = Stroke(width = 2f)
        )
        drawTextOn(letter, center.copy(y = center.y + size.height * 0.04f), palette.accent, size.width * 0.16f)
        return
    }

    val columns = if (count <= 3) count else 3
    val rows = (count + columns - 1) / columns
    var drawn = 0
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            if (drawn >= count) break
            val x = center.x + (column - (columns - 1) / 2f) * step
            val y = center.y + (row - (rows - 1) / 2f) * step * 1.25f
            val jitter = ((seed shr (drawn % 8)) % 9 - 4) * 0.6f
            val position = Offset(x + jitter, y)
            drawSuitShape(card, palette, position, size.width * 0.035f)
            drawn++
        }
    }
}

private fun DrawScope.drawSuitShape(card: TarotCard, palette: Palette, center: Offset, radius: Float) {
    val color = palette.accent.copy(alpha = 0.9f)
    when {
        card.title.endsWith("Жезлов") -> drawLine(
            color = color,
            start = Offset(center.x, center.y - radius * 1.4f),
            end = Offset(center.x, center.y + radius * 1.4f),
            strokeWidth = radius * 0.55f
        )

        card.title.endsWith("Кубков") -> drawCircle(
            color = color,
            radius = radius,
            center = center
        )

        card.title.endsWith("Мечей") -> {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x, center.y - radius * 1.3f)
                lineTo(center.x + radius, center.y + radius)
                lineTo(center.x - radius, center.y + radius)
                close()
            }
            drawPath(path, color)
        }

        else -> drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = Stroke(width = radius * 0.6f)
        )
    }
}

private fun DrawScope.drawLabel(card: TarotCard, palette: Palette) {
    val top = card.arcana == "Старший аркан"
    val label = if (top) romanNumeral(card.id + 1) else card.title.substringBefore(' ')
    drawTextOn(
        text = label,
        center = Offset(size.width / 2f, size.height * 0.09f),
        color = palette.accent.copy(alpha = 0.75f),
        textSize = size.width * 0.07f
    )
}

private fun DrawScope.drawTextOn(text: String, center: Offset, color: Color, textSize: Float) {
    val paint = Paint().apply {
        isAntiAlias = true
        this.color = color.toArgb()
        this.textSize = textSize
        textAlign = Paint.Align.CENTER
    }
    drawContext.canvas.nativeCanvas.drawText(text, center.x, center.y + textSize * 0.35f, paint)
}

private data class Palette(val top: Color, val bottom: Color, val accent: Color)

private fun paletteFor(card: TarotCard): Palette = when {
    card.arcana == "Старший аркан" ->
        Palette(Color(0xFF2E2347), Color(0xFF120D1C), Color(0xFFD9B25F))
    card.title.endsWith("Жезлов") ->
        Palette(Color(0xFF4A2410), Color(0xFF1B0D06), Color(0xFFE4813A))
    card.title.endsWith("Кубков") ->
        Palette(Color(0xFF13293D), Color(0xFF08131C), Color(0xFF4FA3C7))
    card.title.endsWith("Мечей") ->
        Palette(Color(0xFF2A2C38), Color(0xFF12131A), Color(0xFF9AA4C7))
    else -> Palette(Color(0xFF1B3324), Color(0xFF0A1710), Color(0xFF6FBF8B))
}

private fun rankCount(rank: String): Int? = when (rank) {
    "Туз" -> 1
    "Двойка" -> 2
    "Тройка" -> 3
    "Четвёрка" -> 4
    "Пятёрка" -> 5
    "Шестёрка" -> 6
    "Семёрка" -> 7
    "Восьмёрка" -> 8
    "Девятка" -> 9
    "Десятка" -> 10
    else -> null
}

private fun courtLetter(rank: String): String = when (rank) {
    "Паж" -> "P"
    "Рыцарь" -> "N"
    "Королева" -> "Q"
    "Король" -> "K"
    else -> "?"
}

private fun romanNumeral(value: Int): String {
    val table = listOf(
        1000 to "M", 900 to "CM", 500 to "D", 400 to "CD",
        100 to "C", 90 to "XC", 50 to "L", 40 to "XL",
        10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I"
    )
    var remaining = value
    val builder = StringBuilder()
    for ((number, numeral) in table) {
        while (remaining >= number) {
            builder.append(numeral)
            remaining -= number
        }
    }
    return builder.toString()
}
