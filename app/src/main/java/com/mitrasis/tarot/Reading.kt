package com.mitrasis.tarot

/**
 * Текст расклада собирается из того, что дала модель, без готовых фраз:
 * главная карта — ответ, две другие — фон; в конце поэтическая строка,
 * где прилагательное согласовано с родом и числом первого значения.
 */
object Reading {

    private val MASC = listOf(
        "невыносимый", "тихий", "поздний", "слепой", "вечный", "чужой",
        "обратный", "глухой", "светлый", "тёмный", "ломкий", "зрячий",
        "беглый", "зимний", "узкий", "последний"
    )
    private val FEM = listOf(
        "невыносимая", "тихая", "поздняя", "слепая", "вечная", "чужая",
        "обратная", "глухая", "светлая", "тёмная", "ломкая", "зрячая",
        "беглая", "зимняя", "узкая", "последняя"
    )
    private val NEUT = listOf(
        "невыносимое", "тихое", "позднее", "слепое", "вечное", "чужое",
        "обратное", "глухое", "светлое", "тёмное", "ломкое", "зрячее",
        "беглое", "зимнее", "узкое", "последнее"
    )
    private val PLURAL = listOf(
        "невыносимые", "тихие", "поздние", "слепые", "вечные", "чужие",
        "обратные", "глухие", "светлые", "тёмные", "ломкие", "зрячие",
        "беглые", "зимние", "узкие", "последние"
    )

    /** 0 — мужской, 1 — женский, 2 — средний, 3 — множественное. */
    private fun genderOf(noun: String): Int {
        val word = noun.lowercase()
        val last = word.lastOrNull() ?: return 0
        if (last == 'и' || last == 'ы') return 3
        return when (last) {
            'а', 'я', 'ь' -> 1
            'о', 'е' -> 2
            else -> 0
        }
    }

    private fun shortValue(keyword: String): String =
        keyword.substringBefore(',').substringBefore(' ').trim()

    fun compose(answer: CardAnswer): List<String> {
        val lines = answer.lines
        if (lines.isEmpty()) return emptyList()

        fun mark(line: CardLine): String =
            if (line.reversed) "${line.title} (перевёрнута)" else line.title

        val result = mutableListOf<String>()
        val main = lines.first()
        result += "${mark(main)} — ${main.keyword}."

        val background = lines.drop(1).filter { it.keyword.isNotBlank() }
        if (background.isNotEmpty()) {
            val parts = background.joinToString(" и ") { "${mark(it)} — ${it.keyword}" }
            result += "Фон: $parts."
        }

        val values = lines.map { shortValue(it.keyword) }.filter { it.isNotBlank() }.distinct()
        if (values.isNotEmpty()) {
            val pool = when (genderOf(values.first())) {
                1 -> FEM
                2 -> NEUT
                3 -> PLURAL
                else -> MASC
            }
            val seed = (answer.card.title + values.joinToString()).hashCode()
            val adjective = pool[Math.floorMod(seed, pool.size)]
            // Два слова в заголовке — предел живой русской конструкции;
            // три и больше превращаются в перечисление.
            val tail = if (values.size == 1) "" else " и " + values[1]
            result += (adjective + " " + values.first() + tail).replaceFirstChar { it.uppercase() } + "."
        }

        return result
    }
}
