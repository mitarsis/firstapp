package com.mitrasis.tarot

/**
 * Текст расклада.
 *
 * Форма зависит от жанра вопроса (GenreGate): прогноз, диагноз, совет, срок.
 * Внутри — роли карт, противопоставления, ось разбора и один вывод.
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
        "свет" to "тьма", "порядок" to "хаос", "начало" to "конец",
        "любовь" to "разлука", "сила" to "слабость", "деньги" to "нужда",
        "движение" to "застой", "правда" to "обман", "надежда" to "отчаяние",
        "рост" to "упадок", "близость" to "дистанция", "победа" to "поражение",
        "терпение" to "спешка", "щедрость" to "скупость", "ясность" to "туман",
        "доверие" to "ревность", "свобода" to "зависимость", "верность" to "измена",
        "покой" to "тревога", "прошлое" to "будущее", "воля" to "принуждение",
        "открытость" to "закрытость", "тепло" to "холод", "дар" to "долг",
        "вера" to "сомнение", "целое" to "раскол", "слово" to "молчание",
        "путь" to "стоянка", "огонь" to "пепел", "взлёт" to "падение",
        "своё" to "чужое", "простота" to "хитрость", "игра" to "расчёт",
        "смелость" to "страх", "радость" to "горе", "встреча" to "разрыв",
        "мера" to "излишество", "тишина" to "шум", "память" to "забвение",
        "зрелость" to "детство"
    )

    /** Абстракция — конкретика: сцена вместо термина. */
    private val SCENES = mapOf(
        "воля" to "решение, принятое в одиночку",
        "судьба" to "то, что уже решено за тебя",
        "гармония" to "ровный разговор без напряга",
        "потеря" to "пустая полка после уборки",
        "граница" to "закрытая дверь",
        "границы" to "закрытые двери",
        "обмен" to "разговор, где каждый что-то отдаёт",
        "ожидание" to "долгий звонок без ответа",
        "тревога" to "мысли в четыре утра",
        "движение" to "дорога, на которую уже вышел",
        "застой" to "комната без окон",
        "рост" to "работа, которую видно только через полгода",
        "любовь" to "кто-то ждёт тебя дома",
        "разлука" to "пустая сторона кровати",
        "деньги" to "счёт, который надо закрыть",
        "выбор" to "две двери и один ключ",
        "молчание" to "разговор, который не начали",
        "прошлое" to "чемодан, который не распаковали",
        "маска" to "лицо для чужих",
        "контроль" to "руки, сжатые в кулак"
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

    fun genderOf(noun: String): Int {
        val word = noun.lowercase()
        val last = word.lastOrNull() ?: return 0
        if (last == 'и' || last == 'ы') return 3
        return when (last) {
            'а', 'я', 'ь' -> 1
            'о', 'е' -> 2
            else -> 0
        }
    }

    /** Склонение в родительный падеж: «без границы», «без денег». */
    fun genitive(noun: String): String {
        val word = noun.lowercase()
        return when {
            word.endsWith("ы") || word.endsWith("и") -> word
            word.endsWith("а") -> word.dropLast(1) + "ы"
            word.endsWith("я") -> word.dropLast(1) + "и"
            word.endsWith("ь") -> word.dropLast(1) + "и"
            word.endsWith("о") || word.endsWith("е") -> word.dropLast(1) + "а"
            word.endsWith("й") -> word.dropLast(1) + "я"
            else -> word + "а"
        }
    }

    private fun scene(value: String): String = SCENES[value.lowercase()] ?: value

    private fun pick(seed: Int, vararg options: String): String =
        options[Math.floorMod(seed, options.size)]

    /** Форма текста зависит от жанра вопроса. */
    fun compose(answer: CardAnswer): List<String> {
        val lines = answer.lines
        if (lines.isEmpty()) return emptyList()
        val values = lines.map { shortValue(it.keyword) }.filter { it.isNotBlank() }
        if (values.isEmpty()) return emptyList()

        var best: List<String> = emptyList()
        for (attempt in 0 until 3) {
            best = build(answer, lines, values, attempt)
            if (looksWhole(best)) return best
        }
        return best
    }

    /** Самокритика: короткий или зацикленный текст пересобираем. */
    private fun looksWhole(text: List<String>): Boolean {
        val words = text.joinToString(" ").split(" ").filter { it.isNotBlank() }
        if (words.size < 40) return false
        val repeats = words
            .map { it.lowercase().trim('.', ',') }
            .filter { it.length > 3 && it !in STOP_WORDS }
            .groupingBy { it }.eachCount().values.maxOrNull() ?: 0
        return repeats <= 4
    }

    /** Дательный падеж: «к испытанию», «к чести». */
    fun dative(noun: String): String {
        val word = noun.lowercase()
        return when {
            word.endsWith("а") -> word.dropLast(1) + "е"
            word.endsWith("я") -> word.dropLast(1) + "е"
            word.endsWith("о") -> word.dropLast(1) + "у"
            word.endsWith("е") -> word.dropLast(1) + "ю"
            word.endsWith("ь") -> word.dropLast(1) + "и"
            word.endsWith("и") || word.endsWith("ы") -> word
            else -> word + "у"
        }
    }

    /** Вывод по оси: несколько формулировок, чтобы не приедалась одна. */
    private fun axisVerdicts(axis: Axis): List<String> = when (axis) {
        Axis.BOUNDARIES -> listOf(
            "всё решает, где провести черту",
            "чужое от своего отделяет один разговор",
            "пока границы не названы, их продавливают"
        )
        Axis.MOVEMENT -> listOf(
            "дорога важнее точки, в которой стоишь",
            "стоять на месте дороже, чем идти",
            "шаг уже сделан, осталось его признать"
        )
        Axis.LOSS -> listOf(
            "цена уплачена, и назад её не вернуть",
            "что-то ушло, и это не восстановить",
            "держать прошлое дороже, чем отпустить"
        )
        Axis.EXCHANGE -> listOf(
            "в одиночку это не решается",
            "второй стороны не обойти",
            "всё держится на чужом согласии"
        )
        Axis.WAITING -> listOf(
            "срок важнее выбора",
            "время здесь работает на тебя",
            "поспешишь — потеряешь больше, чем ждёшь"
        )
    }

    private fun capital(text: String): String =
        text.replaceFirstChar { it.uppercase() }

    private fun build(
        answer: CardAnswer,
        lines: List<CardLine>,
        values: List<String>,
        attempt: Int
    ): List<String> {
        val seed = (answer.card.title + values.joinToString() + answer.variant).hashCode() + attempt * 7919
        val out = mutableListOf<String>()
        val v1 = values.first()
        val v2 = values.getOrNull(1) ?: v1
        val v3 = values.getOrNull(2) ?: v2

        val filler = mapOf(
            "v1" to v1, "v2" to v2, "v3" to v3,
            "d2" to dative(v2), "d3" to dative(v3), "g1" to genitive(v1)
        )
        fun fromSlot(slot: String, extra: Map<String, String> = emptyMap()): String {
            val bank = Phrases.of(slot)
            if (bank.isEmpty()) return ""
            val template = bank[Math.floorMod(seed + slot.hashCode(), bank.size)]
            return Phrases.fill(template, filler + extra)
        }

        // 1. Цепочка: как одно переходит в другое
        out += fromSlot("chain").ifBlank {
            "${capital(v1)} ведёт к ${dative(v2)}, а $v2 — к ${dative(v3)}."
        }

        // 2. Один конкретный образ вместо пояснений
        out += fromSlot("scene", mapOf("scene" to scene(v3))).ifBlank {
            "${capital(scene(v1))} — вот с чего это начинается."
        }
        if (out.last().length < 40) {
            out[out.size - 1] = "${capital(scene(v3))} — вот чем это кончится."
        }

        // напряжение: если внутри расклада есть противоположности
        val joinedKeywords = lines.joinToString(" ") { it.keyword.lowercase() }
        ANTAGONISTS.firstOrNull { (a, b) -> joinedKeywords.contains(a) && joinedKeywords.contains(b) }
            ?.let { (a, b) ->
                val line = fromSlot("tension", mapOf("a" to a, "b" to b))
                if (line.isNotBlank()) out += line
            }

        // 3. Формула с выводом по оси
        val distinct = values.distinct()
        val pool = when (genderOf(distinct.first())) {
            1 -> FEM
            2 -> NEUT
            3 -> PLURAL
            else -> MASC
        }
        val adjective = answer.epithet.ifBlank { pool[Math.floorMod(seed, pool.size)] }
        val tail = when {
            distinct.size == 1 -> ""
            distinct.size == 2 -> " и " + distinct[1]
            else -> ", " + distinct[1] + " и " + distinct[2]
        }
        val axisKey = when (answer.axis) {
            Axis.BOUNDARIES -> "verdict:boundaries"
            Axis.MOVEMENT -> "verdict:movement"
            Axis.LOSS -> "verdict:loss"
            Axis.EXCHANGE -> "verdict:exchange"
            Axis.WAITING -> "verdict:waiting"
        }
        val verdictText = fromSlot(axisKey, mapOf(
            "adj" to capital(adjective),
            "v1" to distinct.first(),
            "v2" to distinct.getOrElse(1) { distinct.first() },
            "v3" to distinct.getOrElse(2) { distinct.first() }
        ))
        out += verdictText.ifBlank {
            val verdicts = axisVerdicts(answer.axis)
            "${capital(adjective)} ${distinct.first()}$tail — ${verdicts[Math.floorMod(seed, verdicts.size)]}."
        }
        if (distinct.size > 1) {
            out += fromSlot("close")
        }
        return out
    }
}
