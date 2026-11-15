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

    /**
     * Веса берутся из вероятностей самих карт: уверенная карта тянет итог
     * на себя, три равные карты дают почти равный вклад. Нижняя граница 0.08,
     * чтобы гипотеза с 2% всё же учитывалась как контекст.
     */
    fun nearestForCards(cards: List<Pair<FloatArray, Float>>, seed: Int): String {
        if (cards.isEmpty()) return ""
        val weights = cards.map { it.second.coerceAtLeast(0.08f) }
        val totalWeight = weights.sum().takeIf { it > 0f } ?: 1f

        fun bestIn(range: IntRange): Int {
            var index = range.first
            var best = -Float.MAX_VALUE
            for (phrase in range) {
                val phraseVector = vectors[phrase]
                var weighted = 0f
                cards.forEachIndexed { position, (cardVector, _) ->
                    var dot = 0f
                    for (dimension in phraseVector.indices) {
                        dot += phraseVector[dimension] * cardVector[dimension]
                    }
                    weighted += dot * weights[position] / totalWeight
                }
                val score = weighted - genericity[phrase]
                if (score > best) {
                    best = score
                    index = phrase
                }
            }
            return index
        }

        val concreteEnd = (SummaryPhrases.CONCRETE_COUNT - 1).coerceAtMost(vectors.lastIndex)
        val concrete = bestIn(0..concreteEnd)
        if (concreteEnd >= vectors.lastIndex) return SummaryPhrases.all.getOrElse(concrete) { "" }

        // Метафоры по близости не выигрывают — они далеки от смыслов карт.
        // Поэтому образ выбирается жребием: примерно в 40% раскладов.
        val metaphor = bestIn((concreteEnd + 1)..vectors.lastIndex)
        val chosen = if (Math.floorMod(seed, 100) < 40) metaphor else concrete
        return SummaryPhrases.all.getOrElse(chosen) { "" }
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
