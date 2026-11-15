package com.mitrasis.tarot

/**
 * Отсев вопросов, на которые карта ответить не может.
 *
 * Важно: это НЕ вывод модели. Замеры показали, что косинус и форма
 * распределения одинаковы у живых вопросов и у метавопросов
 * («Пора ли заводить детей» 0.817 против «Расскажи анекдот» 0.864),
 * поэтому порог по близости ничего не отсекает. Здесь работает честное
 * правило по формулировке — и приложение так и говорит пользователю.
 */
object QuestionGuard {

    private val META_PATTERNS = listOf(
        Regex("^\\s*(что|кто)\\s+так(ое|ой|ая|ие)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bкто\\s+(ты|вы|такой|такая)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bсколько\\s+будет\\b", RegexOption.IGNORE_CASE),
        Regex("^\\s*(расскажи|объясни|переведи|напиши|посчитай)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bкак\\s+(работает|устроен|устроена|устроено)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bчто\\s+(означает|значит)\\s+(слово|термин)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(погода|курс|биткоин|футбол|счёт матча)\\b", RegexOption.IGNORE_CASE)
    )

    /** Доля кириллицы: «dQw4w9WgXcQ» тоже не вопрос про жизнь. */
    fun isNotLifeQuestion(text: String): Boolean {
        val clean = text.trim()
        if (clean.length < 3) return true
        if (META_PATTERNS.any { it.containsMatchIn(clean) }) return true

        val letters = clean.count { it.isLetter() }
        if (letters == 0) return true
        val cyrillic = clean.count { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }
        return cyrillic.toFloat() / letters < 0.5f
    }
}
