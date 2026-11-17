package com.mitrasis.tarot

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Эпитет берётся из тех прилагательных, которые действительно встречаются
 * в тексте этой карты у Уэйта, и выбирается тот, что ближе к вопросу.
 * Формы (род, число) лежат в EpithetsData, порядок совпадает с epithets.bin.
 */
object EpithetBook {

    private var vectors: Array<FloatArray>? = null

    fun load(stream: InputStream) {
        if (vectors != null) return
        val bytes = stream.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4)
        buffer.get(magic)
        require(String(magic, Charsets.US_ASCII) == "EPI1") { "bad epithets magic" }
        val rows = buffer.int
        val cols = buffer.int
        val parsed = Array(rows) { FloatArray(cols) }
        for (row in 0 until rows) {
            for (column in 0 until cols) {
                parsed[row][column] = buffer.float
            }
        }
        vectors = parsed
    }

    /** 0 — мужской, 1 — женский, 2 — средний, 3 — множественное. */
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

    fun pick(questionVector: FloatArray, cardIndex: Int, gender: Int): String {
        val table = vectors ?: return ""
        val candidates = EpithetsData.perCard.getOrNull(cardIndex) ?: return ""
        var bestId = candidates.firstOrNull() ?: return ""
        var bestScore = -Float.MAX_VALUE
        for (id in candidates) {
            if (id !in table.indices) continue
            val vector = table[id]
            var dot = 0f
            for (dimension in vector.indices) {
                dot += vector[dimension] * questionVector[dimension]
            }
            if (dot > bestScore) {
                bestScore = dot
                bestId = id
            }
        }
        val forms = EpithetsData.forms.getOrNull(bestId) ?: return ""
        return forms.getOrElse(gender) { forms.first() }
    }
}
