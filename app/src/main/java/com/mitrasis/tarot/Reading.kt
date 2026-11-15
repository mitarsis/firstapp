package com.mitrasis.tarot

/**
 * Только комбинированное значение расклада: три карты и то, что они говорят
 * вместе. Ни пересказа вопроса, ни рассуждений про проценты.
 */
object Reading {

    fun interpret(question: String, answer: CardAnswer): List<String> {
        val cards = listOf(answer.card) + answer.alternatives.map { it.first }
        if (cards.isEmpty()) return emptyList()

        val meanings = cards.mapIndexed { index, card ->
            val values = if (index == 0 && answer.reversed) card.shadow else card.light
            values.firstOrNull().orEmpty()
        }.filter { it.isNotEmpty() }

        if (meanings.isEmpty()) return emptyList()

        val lead = when (meanings.size) {
            1 -> "Смысл расклада"
            else -> "Вместе карты читаются так"
        }

        val body = if (meanings.size == 1) {
            meanings.first()
        } else {
            meanings.dropLast(1).joinToString(", ") + " и " + meanings.last()
        }

        val reversedNote = if (answer.reversed) {
            " Главная карта перевёрнута: ${answer.card.shadow.firstOrNull().orEmpty()}."
        } else {
            ""
        }

        return listOf("$lead: $body.$reversedNote")
    }
}
