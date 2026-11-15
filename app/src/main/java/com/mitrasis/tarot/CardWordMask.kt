package com.mitrasis.tarot

/**
 * GLiNER — это матчер: если слово из вопроса буквально совпадает с названием
 * метки, оно и выигрывает. «Есть ли жизнь на Солнце?» → карта Солнце, и это
 * не ответ, а подсказка.
 *
 * Поэтому слова, похожие на названия карт, заменяются многоточием. Остаётся
 * смысл вопроса без подсказки.
 */
object CardWordMask {

    private val TOKEN = Regex("[\\p{L}\\p{N}_]+|[^\\p{L}\\p{N}_]+")
    private const val VOWELS = "аеёиоуыэюяйь"

    /** Грубая основа слова: срезаем окончание, ё приводим к е. */
    private fun stem(word: String): String {
        var value = word.lowercase().replace('ё', 'е')
        while (value.length > 3 && value.last() in VOWELS) {
            value = value.dropLast(1)
        }
        return value
    }

    private val STEMS: Set<String> by lazy {
        val words = mutableSetOf<String>()
        Deck.cards.forEach { card ->
            card.title.split(' ').forEach { words.add(it) }
        }
        words.add("Колесо")
        words.add("Фортуны")
        words.add("Жрица")
        words.filter { it.length > 2 }.map { stem(it) }.toSet()
    }

    fun mask(text: String): String = TOKEN.replace(text) { match ->
        val token = match.value
        if (token.isNotEmpty() && token[0].isLetter()) {
            val low = token.lowercase().replace('ё', 'е')
            if (STEMS.any { low.startsWith(it) }) "…" else token
        } else {
            token
        }
    }
}
