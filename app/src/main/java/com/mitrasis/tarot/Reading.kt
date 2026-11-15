package com.mitrasis.tarot

/**
 * Текст расклада не выбирается из готовых фраз, а собирается заново:
 * у каждой карты берётся свой тег (ближайший к вопросу), учитывается
 * перевёрнутость, а связки подставляются из набора по хешу вопроса.
 * Число возможных текстов — миллионы, а не сто пять.
 */
object Reading {

    private val SECOND_LINE = listOf(
        "Рядом %s: %s.",
        "Вторит ей %s — %s.",
        "Поддерживает %s: %s.",
        "Дальше %s, и это про %s.",
        "Не отстаёт %s: %s.",
        "Второй голос — %s, %s.",
        "Тут же %s: %s."
    )

    private val THIRD_LINE = listOf(
        "Замыкает %s: %s.",
        "И третий голос — %s, %s.",
        "Слабее звучит %s: %s.",
        "На фоне — %s, %s.",
        "Последним идёт %s: %s."
    )

    private val CLOSING = listOf(
        "Вместе это разговор про %s.",
        "Если сложить: %s.",
        "Общий смысл — %s.",
        "Складывается в одно: %s."
    )

    fun compose(answer: CardAnswer): List<String> {
        val lines = answer.lines
        if (lines.isEmpty()) return listOfNotNull(answer.summary.takeIf { it.isNotBlank() })

        val seed = lines.joinToString("|") { it.title + it.keyword }.hashCode()
        fun pick(variants: List<String>, salt: Int): String =
            variants[Math.floorMod(seed + salt, variants.size)]

        val result = mutableListOf<String>()

        val first = lines.first()
        result += if (first.reversed) {
            "${first.title}, перевёрнутая, — про ${first.keyword}."
        } else {
            "${first.title} — про ${first.keyword}."
        }

        lines.getOrNull(1)?.let { line ->
            val template = pick(SECOND_LINE, 1)
            val text = template.replaceFirst("%s", line.title).replaceFirst("%s", line.keyword)
            result += if (line.reversed) "Перевёрнутая. $text" else text
        }

        lines.getOrNull(2)?.let { line ->
            val template = pick(THIRD_LINE, 2)
            val text = template.replaceFirst("%s", line.title).replaceFirst("%s", line.keyword)
            result += if (line.reversed) "Тоже перевёрнутая. $text" else text
        }

        if (answer.summary.isNotBlank()) {
            val template = pick(CLOSING, 3)
            result += template.replaceFirst("%s", answer.summary)
        }

        return result
    }
}
