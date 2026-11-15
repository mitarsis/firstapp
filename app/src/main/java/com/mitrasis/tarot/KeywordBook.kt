package com.mitrasis.tarot

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Теги всех карт с их эмбеддингами.
 *
 * Нужны, чтобы для конкретного вопроса выбрать у карты не «первое значение
 * из списка», а то, которое ближе именно к этому вопросу. Благодаря этому
 * текст расклада не берётся из готового набора фраз, а собирается заново:
 * 78 карт × их теги × ориентация × порядок = миллионы вариантов.
 */
class KeywordBook private constructor(
    private val vectors: Array<FloatArray>,
    private val offsets: IntArray,
    private val counts: IntArray,
    private val keywords: List<String>
) {

    /** Лучший тег карты под данный вектор вопроса. */
    fun bestKeyword(cardIndex: Int, questionVector: FloatArray): String {
        if (cardIndex !in offsets.indices) return ""
        val start = offsets[cardIndex]
        val count = counts[cardIndex]
        if (count <= 0) return ""

        var bestOffset = start
        var bestScore = -Float.MAX_VALUE
        for (index in start until start + count) {
            val vector = vectors[index]
            var dot = 0f
            for (dimension in vector.indices) {
                dot += vector[dimension] * questionVector[dimension]
            }
            if (dot > bestScore) {
                bestScore = dot
                bestOffset = index
            }
        }
        return keywords.getOrElse(bestOffset) { "" }
    }

    companion object {
        fun fromStream(stream: InputStream): KeywordBook {
            val bytes = stream.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4)
            buffer.get(magic)
            require(String(magic, Charsets.US_ASCII) == "KEY1") { "bad keywords magic" }
            val rows = buffer.int
            val cols = buffer.int
            val vectors = Array(rows) { FloatArray(cols) }
            for (row in 0 until rows) {
                for (column in 0 until cols) {
                    vectors[row][column] = buffer.float
                }
            }

            // порядок тот же, что в CardMeanings.all: раскладываем облака на теги
            val offsets = IntArray(CardMeanings.all.size)
            val counts = IntArray(CardMeanings.all.size)
            val keywords = ArrayList<String>(rows)
            CardMeanings.all.forEachIndexed { cardIndex, entry ->
                val parts = entry.full.split(", ").map { it.trim() }.filter { it.isNotEmpty() }
                offsets[cardIndex] = keywords.size
                counts[cardIndex] = parts.size
                keywords.addAll(parts)
            }
            require(keywords.size == rows) {
                "несовпадение тегов: в assets $rows, в коде ${keywords.size}"
            }
            return KeywordBook(vectors, offsets, counts, keywords)
        }
    }
}
