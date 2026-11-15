package com.mitrasis.tarot

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Итоговый смысл расклада считается той же моделью, что и карты:
 * берём средний вектор трёх выпавших карт и ищем ближайшую фразу из
 * assets/summaries.bin (они посчитаны тем же энкодером e5).
 * Никаких ручных шаблонов — геометрия смыслов.
 */
class SummaryBook private constructor(
    private val vectors: Array<FloatArray>
) {

    fun nearest(query: FloatArray): String {
        var bestIndex = 0
        var bestScore = -1f
        for (index in vectors.indices) {
            val candidate = vectors[index]
            var dot = 0f
            for (dimension in candidate.indices) {
                dot += candidate[dimension] * query[dimension]
            }
            if (dot > bestScore) {
                bestScore = dot
                bestIndex = index
            }
        }
        return SummaryPhrases.all.getOrElse(bestIndex) { "" }
    }

    companion object {
        fun fromStream(stream: InputStream): SummaryBook {
            val bytes = stream.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(5)
            buffer.get(magic)
            require(String(magic, Charsets.US_ASCII) == "SUMV1") { "bad summaries magic" }
            val rows = buffer.int
            val cols = buffer.int
            val vectors = Array(rows) { FloatArray(cols) }
            for (row in 0 until rows) {
                for (column in 0 until cols) {
                    vectors[row][column] = buffer.float
                }
            }
            return SummaryBook(vectors)
        }
    }
}
