package com.mitrasis.tarot

/**
 * Сборка трактовки из выбранных меток. Модель не пишет прозу — она отдаёт
 * вероятности, а фразы собираются детерминированно, по шаблонам.
 * Так честнее: видно, откуда взялось каждое слово.
 */
fun interpret(
    question: String,
    spread: Spread,
    drawn: List<Drawn>,
    result: DecisionResult
): List<String> {
    val paragraphs = mutableListOf<String>()

    val head = if (question.isBlank()) {
        "Вопрос не задан — расклад отвечает на то, что и так на уме."
    } else {
        "Вопрос: «$question»."
    }
    paragraphs += head

    val tone = result.answer("tone")?.winner
    val toneText = when (tone?.option) {
        "светлый" -> "Расклад дышит легко: карты не сопротивляются."
        "тревожный" -> "Расклад настороже: слишком много перевёрнутого и острого."
        "переломный" -> "Всё висит на переломе — не конец, но и не продолжение."
        "пустой" -> "Расклад пустоват: вопрос либо не тот, либо рано спрашивать."
        else -> "Тон не читается."
    }
    paragraphs += toneText

    drawn.forEach { d ->
        val answer = result.answer("pos_${d.position}")
        val verdict = answer?.winner?.option ?: "молчит"
        val cardLine = buildString {
            append("«${d.position}» — ${d.card.title} (${d.orientation}). ")
            append(d.activeKeywords().take(2).joinToString(", ").replaceFirstChar { it.uppercase() })
            append(". По схеме модели эта позиция ")
            append(
                when (verdict) {
                    "поддерживает" -> "работает на тебя."
                    "тормозит" -> "тянет назад."
                    "запутывает" -> "мутит воду."
                    "требует действия" -> "не терпит бездействия."
                    else -> "не определилась."
                }
            )
        }
        paragraphs += cardLine
    }

    result.answer("advice")?.let { a ->
        val advice = when (a.winner.option) {
            "ждать" -> "Совет скучный, но верный: не дёргаться. Сейчас любое движение стоит дороже, чем кажется."
            "резать по живому" -> "Совет неприятный: резать. Полумеры уже опробованы и не сработали."
            "договариваться" -> "Совет дипломатический: разговор, а не ультиматум. У тебя есть чем торговаться."
            "менять тактику" -> "Совет технический: менять подход, а не цель. Цель как раз живая."
            else -> "Совет не сформирован."
        }
        paragraphs += advice
    }

    result.answer("outcome")?.let { a ->
        val outcome = when (a.winner.option) {
            "да" -> "Исход скорее да."
            "нет" -> "Исход скорее нет."
            "не сейчас" -> "Исход: не сейчас. Вопрос не закрыт, он отложен."
            "зависит от тебя" -> "Исход зависит от тебя — карты тут честно умыли руки."
            else -> "Исход размыт."
        }
        val confidence = (a.winner.probability * 100).toInt()
        paragraphs += "$outcome Уверенность модели — $confidence%."
    }

    return paragraphs
}
