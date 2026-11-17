package com.mitrasis.tarot

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Эпитеты: прилагательное выбирается не по хешу, а по близости к вектору
 * вопроса, и согласуется с родом первого значения.
 *
 * Порядок форм совпадает с assets/epithets.bin (16 masculine-основ).
 */
object EpithetBook {

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

    fun pick(questionVector: FloatArray, gender: Int): String {
        val table = vectors ?: return ""
        var bestIndex = 0
        var bestScore = -Float.MAX_VALUE
        for (index in table.indices) {
            val vector = table[index]
            var dot = 0f
            for (dimension in vector.indices) {
                dot += vector[dimension] * questionVector[dimension]
            }
            if (dot > bestScore) {
                bestScore = dot
                bestIndex = index
            }
        }
        val forms = when (gender) {
            1 -> FEM
            2 -> NEUT
            3 -> PLURAL
            else -> MASC
        }
        return forms.getOrElse(bestIndex) { MASC.first() }
    }
}
