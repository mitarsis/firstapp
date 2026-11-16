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

        // Только значения трёх карт, без служебных слов и пояснений.
        val values = lines
            .map { it.keyword.substringBefore(',').substringBefore(' ').trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (values.isEmpty()) return emptyList()

        val sentence = when (values.size) {
            1 -> values.first()
            2 -> "${values[0]} и ${values[1]}"
            else -> values.dropLast(1).joinToString(", ") + " и " + values.last()
        }
        return listOf(sentence.replaceFirstChar { it.uppercase() } + ".")
    }
}
