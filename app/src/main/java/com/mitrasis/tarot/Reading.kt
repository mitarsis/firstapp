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

    private val MASC = listOf(
        "невыносимый", "тихий", "поздний", "слепой", "вечный",
        "чужой", "обратный", "глухой", "светлый", "тёмный"
    )
    private val FEM = listOf(
        "невыносимая", "тихая", "поздняя", "слепая", "вечная",
        "чужая", "обратная", "глухая", "светлая", "тёмная"
    )
    private val PLURAL = listOf(
        "невыносимые", "тихие", "поздние", "слепые", "вечные",
        "чужие", "обратные", "глухие", "светлые", "тёмные"
    )
    private val NEUT = listOf(
        "невыносимое", "тихое", "позднее", "слепое", "вечное",
        "чужое", "обратное", "глухое", "светлое", "тёмное"
    )

    /** 0 — мужской, 1 — женский, 2 — средний, 3 — множественное. */
    private fun genderOf(noun: String): Int {
        val word = noun.lowercase()
        val last = word.lastOrNull() ?: return 0
        // «новости», «перемены», «деньги» — множественное
        if (last == 'и' || last == 'ы') return 3
        return when (last) {
            'а', 'я', 'ь' -> 1
            'о', 'е' -> 2
            else -> 0
        }
    }

    fun compose(answer: CardAnswer): List<String> {
        val lines = answer.lines
        if (lines.isEmpty()) return listOfNotNull(answer.summary.takeIf { it.isNotBlank() })

        val values = lines
            .map { it.keyword.substringBefore(',').substringBefore(' ').trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (values.isEmpty()) return emptyList()

        val seed = (answer.card.title + values.joinToString()).hashCode()
        val main = values.first()
        val pool = when (genderOf(main)) {
            1 -> FEM
            2 -> NEUT
            3 -> PLURAL
            else -> MASC
        }
        val adjective = pool[Math.floorMod(seed, pool.size)]

        val tail = when (values.size) {
            1 -> ""
            2 -> " и " + values[1]
            else -> ", " + values.drop(1).dropLast(1).joinToString(", ") + " и " + values.last()
        }

        return listOf((adjective + " " + main + tail).replaceFirstChar { it.uppercase() } + ".")
    }
}
