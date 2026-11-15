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
    modifier: Modifier = Modifier,
    badge: String? = null,
    variant: Int = 0
) {
    val palette = paletteFor(card)
    // variant меняется от расклада к раскладу: символ карты тот же, орнамент новый
    val seed = card.titleEn.hashCode() * 31 + variant

    Canvas(modifier = modifier.aspectRatio(0.62f)) {
        val corner = CornerRadius(size.width * 0.08f, size.width * 0.08f)

        val tilt = (((seed % 9) + 9) % 9) / 9f
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(palette.top, palette.bottom),
                start = Offset(size.width * tilt, 0f),
                end = Offset(size.width * (1f - tilt), size.height)
            ),
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

        // лучи за символом
        val rayCount = 7 + ((seed shr 5) and 0x7)
        val rayCenter = Offset(size.width / 2f, size.height * 0.46f)
        repeat(rayCount) { index ->
            val angle = Math.toRadians(360.0 / rayCount * index)
            drawLine(
                color = palette.accent.copy(alpha = 0.09f),
                start = rayCenter,
                end = Offset(
                    rayCenter.x + (size.minDimension * 0.46f * cos(angle)).toFloat(),
                    rayCenter.y + (size.minDimension * 0.46f * sin(angle)).toFloat()
                ),
                strokeWidth = size.minDimension * 0.004f
            )
        }

        // внутренняя рамка и угловые ромбы
        val inset = size.width * 0.055f
        drawRoundRect(
            color = palette.accent.copy(alpha = 0.20f),
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2f, size.height - inset * 2f),
            cornerRadius = CornerRadius(inset * 0.8f, inset * 0.8f),
            style = Stroke(width = size.minDimension * 0.004f)
        )
        val diamondR = size.minDimension * 0.012f
        listOf(
            Offset(inset, inset), Offset(size.width - inset, inset),
            Offset(inset, size.height - inset), Offset(size.width - inset, size.height - inset)
        ).forEach { cornerPoint ->
            val diamond = androidx.compose.ui.graphics.Path().apply {
                moveTo(cornerPoint.x, cornerPoint.y - diamondR)
                lineTo(cornerPoint.x + diamondR, cornerPoint.y)
                lineTo(cornerPoint.x, cornerPoint.y + diamondR)
                lineTo(cornerPoint.x - diamondR, cornerPoint.y)
                close()
            }
            drawPath(diamond, palette.accent.copy(alpha = 0.45f))
        }

        // Узнаваемый символ вместо абстрактного узора.
        // Лёгкий наклон и звёздная пыль вокруг зависят от варианта расклада.
        val glyphCenter = Offset(size.width / 2f, size.height * 0.46f)
        val tiltAngle = (((seed % 13) + 13) % 13 - 6).toFloat()
        rotate(tiltAngle, glyphCenter) {
            drawCardSymbol(card, palette, probability.coerceIn(0.15f, 1f))
        }

        val stars = 4 + ((seed shr 3) and 0x7)
        repeat(stars) { index ->
            val angle = Math.toRadians(((seed shr (index * 2)) % 360).toDouble())
            val radius = size.minDimension * (0.36f + ((seed shr index) % 9) * 0.012f)
            val point = Offset(
                glyphCenter.x + (radius * cos(angle)).toFloat(),
                glyphCenter.y + (radius * sin(angle)).toFloat()
            )
            drawCircle(
                color = palette.accent.copy(alpha = 0.18f + 0.25f * probability.coerceIn(0f, 1f)),
                radius = size.minDimension * 0.007f,
                center = point
            )
        }

        drawPips(card, palette, seed)
        drawLabel(card, palette)
        drawBottomCaption(card, palette)

        badge?.let {
            drawCircle(
                color = palette.bottom.copy(alpha = 0.85f),
                radius = size.minDimension * 0.075f,
                center = Offset(size.width * 0.12f, size.height * 0.055f)
            )
            drawTextOn(
                text = it,
                center = Offset(size.width * 0.12f, size.height * 0.055f),
                color = palette.accent,
                textSize = size.width * 0.075f
            )
        }

        // рамка: чем увереннее ответ, тем чётче
        drawRoundRect(
            color = palette.accent.copy(alpha = 0.35f + 0.45f * probability.coerceIn(0f, 1f)),
            cornerRadius = corner,
            style = Stroke(width = 1.5f + 2f * probability.coerceIn(0f, 1f))
        )
    }
}

/**
 * Сигил Младшего аркана: орнаментальные кольца, масть по кругу (роза),
 * крупный символ масти в центре. Никакой сетки из смазанных точек.
 */
private fun DrawScope.drawPips(card: TarotCard, palette: Palette, seed: Int) {
    if (card.arcana == "Старший аркан") return

    val center = Offset(size.width / 2f, size.height * 0.50f)
    val minDim = size.minDimension
    val accent = palette.accent
    val thin = minDim * 0.008f

    // орнамент
    drawCircle(accent.copy(alpha = 0.12f), minDim * 0.40f, center, style = Stroke(thin))
    drawCircle(accent.copy(alpha = 0.07f), minDim * 0.31f, center, style = Stroke(thin * 0.8f))
    for (index in 0 until 4) {
        val angle = Math.toRadians(90.0 * index + 45.0)
        val from = Offset(
            center.x + (minDim * 0.31f * cos(angle)).toFloat(),
            center.y + (minDim * 0.31f * sin(angle)).toFloat()
        )
        drawCircle(accent.copy(alpha = 0.25f), thin * 1.6f, from)
    }

    val rank = card.title.substringBefore(' ')
    val count = rankCount(rank)

    if (count == null) {
        // двор: крупная литера в кольце, масть — маленькой меткой сверху
        drawCircle(accent.copy(alpha = 0.35f), minDim * 0.17f, center, style = Stroke(thin * 1.8f))
        drawCircle(accent.copy(alpha = 0.16f), minDim * 0.24f, center, style = Stroke(thin))
        drawTextOn(courtLetter(rank), center, accent, minDim * 0.19f)
        drawSuitGlyph(
            card, palette,
            Offset(center.x, center.y - minDim * 0.30f),
            minDim * 0.055f, accent.copy(alpha = 0.75f), thin
        )
        return
    }

    if (count > 1) {
        val radius = minDim * 0.37f
        for (index in 0 until count) {
            val angle = Math.toRadians(360.0 / count * index - 90.0)
            val position = Offset(
                center.x + (radius * cos(angle)).toFloat(),
                center.y + (radius * sin(angle)).toFloat()
            )
            drawSuitShape(card, palette, position, minDim * 0.021f)
        }
    }

    drawSuitGlyph(card, palette, center, minDim * 0.12f, accent.copy(alpha = 0.9f), thin * 1.5f)
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

private fun DrawScope.drawBottomCaption(card: TarotCard, palette: Palette) {
    drawTextOn(
        text = card.title.uppercase(),
        center = Offset(size.width / 2f, size.height * 0.915f),
        color = palette.accent.copy(alpha = 0.6f),
        textSize = size.width * 0.055f
    )
    drawLine(
        color = palette.accent.copy(alpha = 0.18f),
        start = Offset(size.width * 0.24f, size.height * 0.885f),
        end = Offset(size.width * 0.76f, size.height * 0.885f),
        strokeWidth = size.minDimension * 0.004f
    )
}

private fun DrawScope.drawLabel(card: TarotCard, palette: Palette) {
    val top = card.arcana == "Старший аркан"
    val label = if (top) {
        if (card.id == 0) "0" else romanNumeral(card.id)
    } else {
        card.title.substringBefore(' ')
    }
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
    "Паж" -> "П"
    "Рыцарь" -> "Р"
    "Королева" -> "Д"
    "Король" -> "К"
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

/** Крупный символ карты: у Старших — свой глиф, у Младших — масть. */
private fun DrawScope.drawCardSymbol(card: TarotCard, palette: Palette, sharpness: Float) {
    val center = Offset(size.width / 2f, size.height * 0.46f)
    val r = size.minDimension * 0.30f
    val color = palette.accent.copy(alpha = 0.35f + 0.5f * sharpness)
    val thin = size.minDimension * 0.012f

    if (card.arcana != "Старший аркан") return

    when (card.title) {
        "Шут" -> {
            drawCircle(color.copy(alpha = 0.25f), r, center, style = Stroke(thin * 1.5f))
            drawLine(color, Offset(center.x - r * 0.6f, center.y + r * 0.9f),
                Offset(center.x + r * 0.6f, center.y - r * 1.1f), thin * 1.6f)
        }
        "Маг" -> {
            drawLine(color, Offset(center.x, center.y - r), Offset(center.x, center.y + r), thin * 2f)
            drawCircle(color, r * 0.18f, Offset(center.x, center.y - r * 1.15f))
            drawCircle(color, r * 0.5f, Offset(center.x, center.y + r * 0.1f), style = Stroke(thin))
        }
        "Верховная Жрица" -> {
            drawArc(color, 200f, 140f, false,
                Offset(center.x - r, center.y - r), Size(r * 2f, r * 2f), style = Stroke(thin * 2f))
            drawLine(color, Offset(center.x - r * 0.8f, center.y - r), Offset(center.x - r * 0.8f, center.y + r), thin)
            drawLine(color, Offset(center.x + r * 0.8f, center.y - r), Offset(center.x + r * 0.8f, center.y + r), thin)
        }
        "Императрица" -> {
            drawCircle(color, r * 0.75f, center, style = Stroke(thin * 1.6f))
            drawLine(color, Offset(center.x - r * 0.9f, center.y + r * 0.5f), Offset(center.x, center.y - r * 0.9f), thin)
            drawLine(color, Offset(center.x + r * 0.9f, center.y + r * 0.5f), Offset(center.x, center.y - r * 0.9f), thin)
        }
        "Император" -> {
            drawRect(color, Offset(center.x - r * 0.7f, center.y - r * 0.7f), Size(r * 1.4f, r * 1.4f), style = Stroke(thin * 2f))
            drawLine(color, Offset(center.x, center.y - r * 1.1f), Offset(center.x, center.y - r * 0.7f), thin * 2f)
        }
        "Иерофант" -> {
            drawLine(color, Offset(center.x, center.y - r), Offset(center.x, center.y + r), thin * 2f)
            drawLine(color, Offset(center.x - r * 0.6f, center.y - r * 0.35f), Offset(center.x + r * 0.6f, center.y - r * 0.35f), thin * 1.6f)
            drawLine(color, Offset(center.x - r * 0.45f, center.y + r * 0.15f), Offset(center.x + r * 0.45f, center.y + r * 0.15f), thin * 1.6f)
        }
        "Влюблённые" -> {
            drawCircle(color, r * 0.62f, Offset(center.x - r * 0.35f, center.y), style = Stroke(thin * 1.6f))
            drawCircle(color, r * 0.62f, Offset(center.x + r * 0.35f, center.y), style = Stroke(thin * 1.6f))
        }
        "Колесница", "Колесо Фортуны" -> {
            drawCircle(color, r, center, style = Stroke(thin * 1.8f))
            val spokes = if (card.title == "Колесница") 6 else 8
            for (index in 0 until spokes) {
                val angle = Math.toRadians(360.0 / spokes * index)
                drawLine(color, center,
                    Offset(center.x + (r * cos(angle)).toFloat(), center.y + (r * sin(angle)).toFloat()), thin)
            }
        }
        "Сила" -> {
            drawCircle(color, r * 0.9f, center, style = Stroke(thin * 1.8f))
            drawLine(color, Offset(center.x - r * 0.5f, center.y), Offset(center.x + r * 0.5f, center.y), thin * 2f)
            drawLine(color, Offset(center.x, center.y - r * 0.5f), Offset(center.x, center.y + r * 0.5f), thin * 2f)
        }
        "Отшельник" -> {
            drawRect(color, Offset(center.x - r * 0.45f, center.y - r * 0.5f), Size(r * 0.9f, r), style = Stroke(thin * 1.6f))
            drawCircle(color, r * 0.2f, center)
        }
        "Справедливость" -> {
            drawLine(color, Offset(center.x, center.y - r), Offset(center.x, center.y + r * 0.9f), thin * 2f)
            drawLine(color, Offset(center.x - r * 0.9f, center.y - r * 0.4f), Offset(center.x + r * 0.9f, center.y - r * 0.4f), thin * 1.6f)
            drawLine(color, Offset(center.x - r * 0.9f, center.y - r * 0.4f), Offset(center.x - r * 0.75f, center.y + r * 0.15f), thin)
            drawLine(color, Offset(center.x + r * 0.9f, center.y - r * 0.4f), Offset(center.x + r * 0.75f, center.y + r * 0.15f), thin)
        }
        "Повешенный" -> {
            drawLine(color, Offset(center.x, center.y - r), Offset(center.x, center.y - r * 0.2f), thin * 2f)
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x - r * 0.6f, center.y + r * 0.9f)
                lineTo(center.x + r * 0.6f, center.y + r * 0.9f)
                lineTo(center.x, center.y - r * 0.2f)
                close()
            }
            drawPath(path, color, style = Stroke(thin * 1.6f))
        }
        "Смерть" -> {
            drawCircle(color, r * 0.8f, center, style = Stroke(thin * 1.8f))
            drawArc(color, 200f, 140f, false,
                Offset(center.x + r * 0.3f, center.y - r * 1.1f), Size(r * 1.2f, r * 1.2f), style = Stroke(thin * 2f))
        }
        "Умеренность" -> {
            drawLine(color, Offset(center.x - r, center.y - r * 0.5f), Offset(center.x - r * 0.3f, center.y + r * 0.6f), thin * 1.6f)
            drawLine(color, Offset(center.x + r, center.y - r * 0.5f), Offset(center.x + r * 0.3f, center.y + r * 0.6f), thin * 1.6f)
            drawLine(color, Offset(center.x - r * 0.3f, center.y + r * 0.6f), Offset(center.x + r * 0.3f, center.y + r * 0.6f), thin)
        }
        "Дьявол" -> {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x, center.y + r)
                lineTo(center.x - r * 0.9f, center.y - r * 0.6f)
                lineTo(center.x + r * 0.9f, center.y - r * 0.6f)
                close()
            }
            drawPath(path, color, style = Stroke(thin * 1.8f))
            drawCircle(color, r * 0.25f, Offset(center.x, center.y + r * 0.15f))
        }
        "Башня" -> {
            drawRect(color, Offset(center.x - r * 0.5f, center.y - r * 0.4f), Size(r, r * 1.6f), style = Stroke(thin * 1.8f))
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x + r * 0.2f, center.y - r * 1.2f)
                lineTo(center.x - r * 0.2f, center.y - r * 0.4f)
                lineTo(center.x + r * 0.15f, center.y - r * 0.35f)
                lineTo(center.x - r * 0.25f, center.y + r * 0.4f)
            }
            drawPath(path, color, style = Stroke(thin * 1.6f))
        }
        "Звезда" -> {
            val points = 8
            val path = androidx.compose.ui.graphics.Path()
            for (index in 0 until points * 2) {
                val angle = Math.toRadians(180.0 / points * index - 90)
                val radius = if (index % 2 == 0) r else r * 0.45f
                val x = center.x + (radius * cos(angle)).toFloat()
                val y = center.y + (radius * sin(angle)).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, color, style = Stroke(thin * 1.6f))
        }
        "Луна" -> {
            drawArc(color, 90f, 180f, false,
                Offset(center.x - r * 0.5f, center.y - r), Size(r * 1.6f, r * 2f), style = Stroke(thin * 2f))
            drawCircle(color, r * 0.12f, Offset(center.x + r * 0.7f, center.y - r * 0.4f))
            drawCircle(color, r * 0.09f, Offset(center.x + r * 1f, center.y + r * 0.2f))
        }
        "Солнце" -> {
            drawCircle(color, r * 0.55f, center, style = Stroke(thin * 1.8f))
            for (index in 0 until 12) {
                val angle = Math.toRadians(30.0 * index)
                drawLine(color,
                    Offset(center.x + (r * 0.75f * cos(angle)).toFloat(), center.y + (r * 0.75f * sin(angle)).toFloat()),
                    Offset(center.x + (r * 1.05f * cos(angle)).toFloat(), center.y + (r * 1.05f * sin(angle)).toFloat()),
                    thin * 1.6f)
            }
        }
        "Суд" -> {
            drawRect(color, Offset(center.x - r * 0.15f, center.y - r * 0.2f), Size(r * 0.3f, r * 1.1f), style = Stroke(thin * 1.6f))
            drawCircle(color, r * 0.55f, Offset(center.x, center.y - r * 0.6f), style = Stroke(thin * 1.6f))
        }
        "Мир" -> {
            drawOval(color, Offset(center.x - r * 0.7f, center.y - r), Size(r * 1.4f, r * 2f), style = Stroke(thin * 1.8f))
            drawCircle(color, r * 0.2f, center)
        }
        else -> {
            drawCircle(color, r * 0.8f, center, style = Stroke(thin * 1.6f))
        }
    }
}

/** Символ масти: жезл, чаша, клинок, пентакль. */
private fun DrawScope.drawSuitGlyph(
    card: TarotCard,
    palette: Palette,
    center: Offset,
    r: Float,
    color: Color,
    thin: Float
) {
    when {
        card.title.endsWith("Жезлов") -> {
            drawLine(color, Offset(center.x, center.y - r), Offset(center.x, center.y + r), thin * 2.2f)
            drawCircle(color, thin * 2.2f, Offset(center.x, center.y - r))
        }
        card.title.endsWith("Кубков") -> {
            drawArc(color, 0f, 180f, false, Offset(center.x - r * 0.7f, center.y - r * 0.6f),
                Size(r * 1.4f, r * 1.2f), style = Stroke(thin * 1.8f))
            drawLine(color, Offset(center.x, center.y + r * 0.2f), Offset(center.x, center.y + r * 0.9f), thin * 1.8f)
        }
        card.title.endsWith("Мечей") -> {
            drawLine(color, Offset(center.x, center.y - r), Offset(center.x, center.y + r * 0.6f), thin * 1.8f)
            drawLine(color, Offset(center.x - r * 0.5f, center.y + r * 0.3f), Offset(center.x + r * 0.5f, center.y + r * 0.3f), thin * 1.4f)
        }
        else -> {
            drawCircle(color, r * 0.75f, center, style = Stroke(thin * 1.4f))
            val path = androidx.compose.ui.graphics.Path()
            for (index in 0 until 10) {
                val angle = Math.toRadians(36.0 * index - 90)
                val radius = if (index % 2 == 0) r * 0.65f else r * 0.25f
                val x = center.x + (radius * cos(angle)).toFloat()
                val y = center.y + (radius * sin(angle)).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, color, style = Stroke(thin))
        }
    }
}
