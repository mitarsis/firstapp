package com.mitrasis.tarot

import android.content.Context

/**
 * История последних раскладов. Хранится строкой в SharedPreferences:
 * без баз данных, без библиотек, переживает перезапуск.
 */
object History {

    data class Entry(
        val question: String,
        val card: String,
        val percent: Int,
        val reversed: Boolean,
        val time: Long
    )

    private const val PREFS = "tarot_history"
    private const val KEY = "entries"
    private const val LIMIT = 30

    fun load(context: Context): List<Entry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("\n").mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 5) return@mapNotNull null
            Entry(
                question = parts[0],
                card = parts[1],
                percent = parts[2].toIntOrNull() ?: 0,
                reversed = parts[3] == "1",
                time = parts[4].toLongOrNull() ?: 0L
            )
        }
    }

    fun add(context: Context, question: String, answer: CardAnswer) {
        if (answer.noAnswer) return
        val entry = Entry(
            question = question.replace("|", " ").replace("\n", " ").take(120),
            card = answer.card.title,
            percent = (answer.probability * 100).toInt(),
            reversed = answer.reversed,
            time = System.currentTimeMillis()
        )
        val current = load(context).toMutableList()
        current.add(0, entry)
        val trimmed = current.take(LIMIT)
        val raw = trimmed.joinToString("\n") {
            "${it.question}|${it.card}|${it.percent}|${if (it.reversed) 1 else 0}|${it.time}"
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, raw)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
