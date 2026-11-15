package com.mitrasis.tarot

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Итоговый смысл расклада считается той же моделью, что и карты.
 *
 * Как именно: у каждой фразы-смысла берётся близость к КАЖДОЙ из трёх карт,
 * и выигрывает та фраза, у которой минимальная близость максимальна — то есть
 * подходит всем трём, а не одной. Сверху вычитается «универсальность» фразы:
 * средняя близость к 78 картам вообще. Без этого штрафа побеждали общие
 * фразы вроде «новое начинание» (было 18 из 47 фраз и 35% на одну,
 * стало 47 из 47 и 5%).
 */
class SummaryBook private constructor(
    private val vectors: Array<FloatArray>,
    private val genericity: FloatArray
) {

    fun nearestForCards(cardVectors: List<FloatArray>): String {
        if (cardVectors.isEmpty()) return ""
        var bestIndex = 0
        var bestScore = -Float.MAX_VALUE

        for (phrase in vectors.indices) {
            var minSimilarity = Float.MAX_VALUE
            for (card in cardVectors) {
                var dot = 0f
                val phraseVector = vectors[phrase]
                for (dimension in phraseVector.indices) {
                    dot += phraseVector[dimension] * card[dimension]
                }
                if (dot < minSimilarity) minSimilarity = dot
            }
            val score = minSimilarity - genericity[phrase]
            if (score > bestScore) {
                bestScore = score
                bestIndex = phrase
            }
        }
        return SummaryPhrases.all.getOrElse(bestIndex) { "" }
    }

    companion object {
        fun fromStream(stream: InputStream, cardVectors: Array<FloatArray>): SummaryBook {
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

            val genericity = FloatArray(rows) { row ->
                var total = 0f
                for (card in cardVectors) {
                    var dot = 0f
                    for (dimension in 0 until cols) {
                        dot += vectors[row][dimension] * card[dimension]
                    }
                    total += dot
                }
                total / cardVectors.size.coerceAtLeast(1)
            }

            return SummaryBook(vectors, genericity)
        }
    }
}
