package com.mitrasis.tarot

/**
 * Текст расклада.
 *
 * Задача — не перечислить три значения, а собрать из них одну мысль:
 *  1. роли: что действует сейчас, что мешает, куда идёт;
 *  2. напряжение: если среди значений есть противоположности — назвать их;
 *  3. синтез: короткая формула из прилагательного и значений.
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

    /** Пары, которые действительно спорят друг с другом. */
    private val ANTAGONISTS = listOf(
        "свет" to "тьма",
        "порядок" to "хаос",
        "начало" to "конец",
        "любовь" to "разлука",
        "сила" to "слабость",
        "деньги" to "нужда",
        "движение" to "застой",
        "правда" to "обман",
        "надежда" to "отчаяние",
        "рост" to "упадок",
        "близость" to "дистанция",
        "победа" to "поражение",
        "терпение" to "спешка",
        "щедрость" to "скупость",
        "ясность" to "туман",
        "доверие" to "ревность",
        "свобода" to "зависимость",
        "верность" to "измена",
        "покой" to "тревога",
        "прошлое" to "будущее"
    )

    private val ADJECTIVE_ENDINGS = listOf(
        "ый", "ий", "ой", "ая", "яя", "ое", "ее", "ые", "ие",
        "ого", "ому", "ым", "ых", "ую", "ей"
    )

    private val STOP_WORDS = setOf(
        "в", "во", "на", "с", "со", "к", "ко", "о", "об", "из", "от", "до",
        "по", "за", "под", "над", "при", "для", "без", "через", "и", "а", "но"
    )

    fun shortValue(keyword: String): String {
        val words = keyword.substringBefore(',').trim().split(" ").filter { it.isNotBlank() }
        if (words.isEmpty()) return ""
        var index = 0
        while (index < words.size - 1 &&
            (words[index].length <= 2 || words[index].lowercase() in STOP_WORDS)
        ) {
            index++
        }
        val candidate = words[index]
        val isAdjective = ADJECTIVE_ENDINGS.any { candidate.endsWith(it, ignoreCase = true) }
        return if (isAdjective && index + 1 < words.size) words[index + 1] else candidate
    }

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

    fun compose(answer: CardAnswer): List<String> {
        val lines = answer.lines
        if (lines.isEmpty()) return emptyList()

        val values = lines.map { shortValue(it.keyword) }.filter { it.isNotBlank() }
        if (values.isEmpty()) return emptyList()

        val result = mutableListOf<String>()

        // 1. Роли: карты как этапы мысли, а не список
        val first = lines.first()
        result += "${first.title} задаёт тон: ${values.first()}."

        val second = lines.getOrNull(1)
        val third = lines.getOrNull(2)
        if (second != null && second.keyword.isNotBlank()) {
            result += "Дальше ${second.title} — ${shortValue(second.keyword)}."
        }
        if (third != null && third.keyword.isNotBlank()) {
            result += "Замыкает ${third.title}: ${shortValue(third.keyword)}."
        }

        // 2. Напряжение: противоположности внутри расклада
        val joined = lines.joinToString(" ") { it.keyword.lowercase() }
        val clash = ANTAGONISTS.firstOrNull { (a, b) -> joined.contains(a) && joined.contains(b) }
        if (clash != null) {
            result += "Здесь спорят ${clash.first} и ${clash.second} — вот на чём всё держится."
        }

        // 3. Синтез одной формулой
        val distinct = values.distinct()
        val pool = when (genderOf(distinct.first())) {
            1 -> FEM
            2 -> NEUT
            3 -> PLURAL
            else -> MASC
        }
        val seed = (answer.card.title + distinct.joinToString()).hashCode()
        val adjective = answer.epithet.ifBlank { pool[Math.floorMod(seed, pool.size)] }
        val tail = when {
            distinct.size == 1 -> ""
            distinct.size == 2 -> " и " + distinct[1]
            else -> ", " + distinct[1] + " и " + distinct[2]
        }
        result += (adjective + " " + distinct.first() + tail).replaceFirstChar { it.uppercase() } + "."

        return result
    }
}
