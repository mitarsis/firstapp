package com.mitrasis.tarot

/**
 * Трактовка по шаблонам, но с человеческим голосом: короткие фразы,
 * разные зачины (выбор зависит от пары «вопрос + карта», а не от рандома),
 * никаких «модель набрала 47% среди ближайших».
 */
object Reading {

    fun interpret(question: String, answer: CardAnswer): List<String> {
        val card = answer.card
        val keywords = if (answer.reversed) card.shadow else card.light
        val share = (answer.probability * 100).toInt()
        val seed = (question + card.title).hashCode()
        fun pick(vararg variants: String) = variants[(seed and 0x7fffffff) % variants.size]

        val lines = mutableListOf<String>()

        if (question.isNotBlank()) {
            lines += "Вопрос: «$question»."
        }

        val opening = if (answer.reversed) {
            pick(
                "${card.title} легла перевёрнутой.",
                "Перевёрнутая ${card.title} — та же история, но вывернутая.",
                "${card.title} вверх ногами."
            )
        } else {
            pick(
                "Выпала ${card.title}.",
                "${card.title}. Без переворотов.",
                "Ответ — ${card.title}."
            )
        }
        lines += opening

        lines += keywords.take(2).joinToString(". ") { it.replaceFirstChar { ch -> ch.uppercase() } } + "."

        lines += when {
            answer.probability >= 0.45f -> pick(
                "Карта держится уверенно — $share%.",
                "Тут почти без вариантов: $share%.",
                "Ответ внятный: $share%."
            )
            answer.probability >= 0.22f -> pick(
                "Не так чтобы наверняка — $share%.",
                "Похоже на правду, но с оговоркой: $share%.",
                "Карта впереди, но не одна: $share%."
            )
            else -> pick(
                "Дальше шум: лучшая набрала $share%.",
                "Уверенности почти нет — $share%. Считай намёком.",
                "Модель тут гадает вместе с тобой: $share%."
            )
        }

        if (answer.reversed) {
            lines += "В прямом положении было бы наоборот: " + card.light.take(2).joinToString(", ") + "."
        }

        answer.alternatives.takeIf { it.isNotEmpty() }?.let { alternatives ->
            val names = alternatives.joinToString(" и ") { (other, probability) ->
                "${other.title} (${(probability * 100).toInt()}%)"
            }
            lines += pick(
                "Рядом: $names.",
                "Тоже звучало: $names.",
                "Соседние по смыслу: $names."
            )
        }

        return lines
    }
}
