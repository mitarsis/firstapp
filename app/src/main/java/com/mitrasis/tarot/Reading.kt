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

    private val ADJECTIVE_ENDINGS = listOf(
        "ый", "ий", "ой", "ая", "яя", "ое", "ее", "ые", "ие",
        "ого", "ому", "ым", "ых", "ую", "ей"
    )

    /**
     * Короткое значение для строки: существительное, а не прилагательное.
     * Теги вроде «мнимая стабильность» иначе давали «полная мнимая».
     */
    private val STOP_WORDS = setOf(
        "в", "во", "на", "с", "со", "к", "ко", "о", "об", "из", "от", "до",
        "по", "за", "под", "над", "при", "для", "без", "через", "и", "а", "но"
    )

    fun shortValue(keyword: String): String {
        val words = keyword.substringBefore(',').trim().split(" ").filter { it.isNotBlank() }
        if (words.isEmpty()) return ""
        // пропускаем предлоги и короткие служебные слова
        var index = 0
        while (index < words.size - 1 && (words[index].length <= 2 || words[index].lowercase() in STOP_WORDS)) {
            index++
        }
        val candidate = words[index]
        val isAdjective = ADJECTIVE_ENDINGS.any { candidate.endsWith(it, ignoreCase = true) }
        return if (isAdjective && index + 1 < words.size) words[index + 1] else candidate
    }

    fun compose(answer: CardAnswer): List<String> {
        val values = answer.lines
            .map { shortValue(it.keyword) }
            .filter { it.isNotBlank() }
            .distinct()

        if (values.isEmpty()) return emptyList()

        val pool = when (genderOf(values.first())) {
            1 -> FEM
            2 -> NEUT
            3 -> PLURAL
            else -> MASC
        }
        val seed = (answer.card.title + values.joinToString()).hashCode()
        // Эпитет приходит из модели (прилагательные из текста Уэйта);
        // запасной вариант — детерминированный выбор из пула.
        val adjective = answer.epithet.ifBlank { pool[Math.floorMod(seed, pool.size)] }

        val tail = when {
            values.size == 1 -> ""
            values.size == 2 -> " и " + values[1]
            else -> ", " + values[1] + " и " + values[2]
        }

        return listOf((adjective + " " + values.first() + tail).replaceFirstChar { it.uppercase() } + ".")
    }
}
