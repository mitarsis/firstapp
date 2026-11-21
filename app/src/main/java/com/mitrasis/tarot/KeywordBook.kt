package com.mitrasis.tarot

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Теги карт: светлые и теневые, с эмбеддингами.
 *
 * Формат assets/keywords.bin (KEY4):
 *   magic, rows:i32, cols:i32, cards:i32,
 *   cards × (lightCount:u16, shadowCount:u16),
 *   rows × (flag:u8, len:u16, utf8, cols × f32)
 *
 * Для прямой карты модель ищет ближайший светлый тег, для перевёрнутой —
 * теневой. Одна и та же карта звучит по-разному в зависимости от положения.
 */
class KeywordBook private constructor(
    private val vectors: Array<FloatArray>,
    private val shadowFlags: BooleanArray,
    private val keywords: List<String>,
    private val offsets: IntArray,
    private val countsLight: IntArray,
    private val countsShadow: IntArray,
    private val frequency: IntArray
) {

    fun bestKeyword(cardIndex: Int, questionVector: FloatArray, reversed: Boolean): Pair<String, Float> {
        if (cardIndex !in offsets.indices) return "" to 0f
        val start = offsets[cardIndex]
        val total = countsLight[cardIndex] + countsShadow[cardIndex]
        if (total <= 0) return "" to 0f

        var bestIndex = -1
        var bestScore = -Float.MAX_VALUE
        for (index in start until start + total) {
            if (shadowFlags[index] != reversed) continue
            val vector = vectors[index]
            var dot = 0f
            for (dimension in vector.indices) {
                dot += vector[dimension] * questionVector[dimension]
            }
            // штраф за «общий» тег: «мысли режут своего же» есть у всей масти
            // Мечей, и без штрафа он выигрывает у конкретного значения ранга
            val adjusted = dot - 0.02f * (frequency[index] - 1)
            if (adjusted > bestScore) {
                bestScore = adjusted
                bestIndex = index
            }
        }
        if (bestIndex < 0) return "" to 0f
        return keywords.getOrElse(bestIndex) { "" } to bestScore
    }


    /**
     * Общее ядро расклада: теги, близкие по смыслу сразу ко всем картам.
     *
     * Берём теги всех карт, усредняем их векторы, подмешиваем вопрос
     * и достаём ближайшие теги из всей колоды — по одному на корень слова.
     */
    fun sharedTags(
        cards: List<Pair<Int, Boolean>>,
        questionVector: FloatArray,
        limit: Int = 6
    ): List<String> {
        if (cards.isEmpty() || vectors.isEmpty()) return emptyList()
        val collected = ArrayList<Int>()
        for ((card, reversed) in cards) {
            if (card !in offsets.indices) continue
            val start = offsets[card]
            val total = countsLight[card] + countsShadow[card]
            for (i in start until start + total) {
                if (shadowFlags[i] == reversed) collected += i
            }
        }
        if (collected.isEmpty()) return emptyList()

        val dimension = vectors[0].size
        val centre = FloatArray(dimension)
        for (index in collected) {
            val vector = vectors[index]
            for (d in 0 until dimension) centre[d] += vector[d]
        }
        for (d in 0 until dimension) {
            centre[d] = centre[d] / collected.size * 0.65f + questionVector[d] * 0.35f
        }
        var norm = 0f
        for (d in 0 until dimension) norm += centre[d] * centre[d]
        norm = kotlin.math.sqrt(norm) + 1e-9f
        for (d in 0 until dimension) centre[d] /= norm

        val scored = (vectors.indices).map { index ->
            var dot = 0f
            val vector = vectors[index]
            for (d in 0 until dimension) dot += vector[d] * centre[d]
            Triple(index, dot - 0.02f * (frequency[index] - 1), keywords[index])
        }.sortedByDescending { it.second }

        val out = LinkedHashMap<String, String>()
        for ((index, _, word) in scored) {
            val root = word.lowercase().take(5)
            if (out.containsKey(root)) continue
            out[root] = word
            if (out.size >= limit) break
        }
        return out.values.toList()
    }

    companion object {
        fun fromStream(stream: InputStream): KeywordBook {
            val bytes = stream.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4)
            buffer.get(magic)
            require(String(magic, Charsets.US_ASCII) == "KEY4") { "bad keywords magic" }

            val rows = buffer.int
            val cols = buffer.int
            val cards = buffer.int

            val countsLight = IntArray(cards)
            val countsShadow = IntArray(cards)
            for (card in 0 until cards) {
                countsLight[card] = buffer.short.toInt() and 0xFFFF
                countsShadow[card] = buffer.short.toInt() and 0xFFFF
            }

            val offsets = IntArray(cards)
            val vectors = Array(rows) { FloatArray(cols) }
            val shadowFlags = BooleanArray(rows)
            val keywords = ArrayList<String>(rows)

            var cursor = 0
            for (card in 0 until cards) {
                offsets[card] = cursor
                cursor += countsLight[card] + countsShadow[card]
            }

            for (row in 0 until rows) {
                shadowFlags[row] = buffer.get().toInt() == 1
                val length = buffer.short.toInt() and 0xFFFF
                val raw = ByteArray(length)
                buffer.get(raw)
                keywords.add(String(raw, Charsets.UTF_8))
                for (column in 0 until cols) {
                    vectors[row][column] = buffer.float
                }
            }

            val freqMap = HashMap<String, Int>()
            keywords.forEach { word -> freqMap[word] = (freqMap[word] ?: 0) + 1 }
            val frequency = IntArray(rows) { freqMap[keywords[it]] ?: 1 }

            return KeywordBook(vectors, shadowFlags, keywords, offsets, countsLight, countsShadow, frequency)
        }
    }
}
