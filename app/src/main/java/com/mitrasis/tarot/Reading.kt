package com.mitrasis.tarot

/**
 * Трактовка по шаблонам. Модель даёт карту и долю, а связный текст собирается
 * из её же данных — без генерации, значит без вранья и без задержек.
 */
object Reading {

    fun interpret(question: String, answer: CardAnswer): List<String> {
        val paragraphs = mutableListOf<String>()
        val card = answer.card
        val keywords = if (answer.reversed) card.shadow else card.light
        val share = (answer.probability * 100).toInt()

        if (question.isNotBlank()) {
            paragraphs += "Вопрос: «$question»."
        }

        val orientation = if (answer.reversed) ", но перевёрнута" else ""
        paragraphs += "Выпала ${card.title}$orientation. Это про ${keywords.take(2).joinToString(" и ")}."

        paragraphs += when {
            answer.probability >= 0.45f ->
                "Смысл вопроса читается ясно: карта набрала $share% среди ближайших."
            answer.probability >= 0.22f ->
                "Ответ читается, но не однозначно — $share% у этой карты."
            else ->
                "Модель колеблется: даже лучшая карта набрала всего $share%. Считай это намёком, а не выводом."
        }

        if (answer.reversed) {
            paragraphs += "Перевёрнутое положение переворачивает и смысл: то же самое, но " +
                card.shadow.take(2).joinToString(", ") + "."
        }

        val alternatives = answer.alternatives
        if (alternatives.isNotEmpty()) {
            val names = alternatives.joinToString(" и ") { (other, probability) ->
                "${other.title} (${(probability * 100).toInt()}%)"
            }
            paragraphs += "Рядом по смыслу: $names. Это то, что тоже звучит в вопросе, но слабее."
        }

        return paragraphs
    }
}
