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
    private val frequency: IntArray,
    private val mean: FloatArray?,
    private val centredVectors: Array<FloatArray>
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
    /**
     * Триангуляция смыслов: три карты задают три направления, а теги —
     * точка, максимально близкая ко всем сразу (максимум минимума сходства).
     * Такой тег не может относиться только к одной карте: он обязан
     * подходить каждой, иначе минимум падает.
     */
    data class SharedCore(
        val tags: List<String>,
        val scores: List<Float>,
        val agreement: Float,
        val outer: List<String> = emptyList()
    )

    private val MAJOR_CARDS = 22

    fun sharedTags(
        cards: List<Pair<Int, Boolean>>,
        questionVector: FloatArray,
        limit: Int = 6
    ): SharedCore {
        if (cards.isEmpty() || vectors.isEmpty()) return SharedCore(emptyList(), emptyList(), 0f)
        if (cards.size == 2) {
            val major = cards.firstOrNull { it.first < MAJOR_CARDS }
            val minor = cards.firstOrNull { it.first >= MAJOR_CARDS }
            if (major != null && minor != null) {
                return domainCore(major, minor, questionVector, limit)
            }
        }
        val dimension = vectors[0].size

        val centres = ArrayList<FloatArray>()
        for ((card, reversed) in cards) {
            if (card !in offsets.indices) continue
            val start = offsets[card]
            val light = countsLight[card]
            val shadow = countsShadow[card]
            val range = if (reversed) (start + light) until (start + light + shadow)
            else start until (start + light)
            if (range.isEmpty()) continue
            val centre = FloatArray(dimension)
            for (index in range) {
                val vector = centredAt(index)
                for (d in 0 until dimension) centre[d] += vector[d]
            }
            val norm = kotlin.math.sqrt(centre.sumOf { (it * it).toDouble() }).toFloat() + 1e-9f
            for (d in 0 until dimension) centre[d] /= norm
            centres += centre
        }
        if (centres.isEmpty()) return SharedCore(emptyList(), emptyList(), 0f)

        // 1. триангуляция: худшее совпадение с картами
        val worst = FloatArray(vectors.size)
        for (index in vectors.indices) {
            val vector = centredAt(index)
            var low = Float.MAX_VALUE
            for (centre in centres) {
                val value = dot(vector, centre)
                if (value < low) low = value
            }
            worst[index] = low - 0.02f * (frequency[index] - 1)
        }

        val question = centred(questionVector)

        // тема вопроса: сам вопрос проецируем в пространство тегов,
        // усредняем ближайшие восемь — это и есть тема, а не формулировка
        val topic = FloatArray(dimension)
        vectors.indices
            .map { it to dot(centredAt(it), question) }
            .sortedByDescending { it.second }
            .take(8)
            .forEach { (index, _) ->
                val vector = centredAt(index)
                for (d in 0 until dimension) topic[d] += vector[d]
            }
        val topicNorm = kotlin.math.sqrt(topic.sumOf { (it * it).toDouble() }).toFloat() + 1e-9f
        for (d in 0 until dimension) topic[d] /= topicNorm

        // 2. «плоская вершина»: все теги, почти равные лучшему
        fun choose(slack: Float): List<Pair<String, Float>> {
            val top = worst.max()
            val candidates = vectors.indices.filter { worst[it] >= top - slack }
            if (candidates.isEmpty()) return emptyList()
            // 3. вопрос решает, какие из равных взять
            val ordered = candidates.sortedByDescending { dot(centredAt(it), topic) }
            val chosen = ArrayList<Int>()
            val out = LinkedHashMap<String, Pair<String, Float>>()
            for (index in ordered) {
                if (out.size >= limit) break
                val vector = centredAt(index)
                // 4. разнообразие: близнецы по смыслу не нужны
                if (chosen.any { dot(vector, centredAt(it)) > 0.78f }) continue
                val root = keywords[index].lowercase().take(5)
                if (out.containsKey(root)) continue
                out[root] = keywords[index] to worst[index]
                chosen += index
            }
            return out.values.toList()
        }

        var result = choose(0.12f)
        if (result.size < 3) result = choose(0.20f)
        // согласие: насколько высоко худшее совпадение у лучших тегов
        val agreement = if (result.isEmpty()) 0f else worst.max()
        return SharedCore(result.map { it.first }, result.map { it.second }, agreement)
    }


    /**
     * Старший задаёт поле, Младший говорит внутри него.
     * Из слов Младшего остаются только те, что совместимы с полем.
     */
    private fun domainCore(
        major: Pair<Int, Boolean>,
        minor: Pair<Int, Boolean>,
        questionVector: FloatArray,
        limit: Int
    ): SharedCore {
        val dimension = vectors[0].size
        // положение учитывается: прямая — светлые смыслы, перевёрнутая — теневые
        fun rangeOf(card: Int, reversed: Boolean): IntRange {
            val start = offsets[card]
            val light = countsLight[card]
            val shadow = countsShadow[card]
            return if (reversed) (start + light) until (start + light + shadow)
            else start until (start + light)
        }
        fun centreOf(card: Int, reversed: Boolean): FloatArray {
            val centre = FloatArray(dimension)
            for (index in rangeOf(card, reversed)) {
                val vector = centredAt(index)
                for (d in 0 until dimension) centre[d] += vector[d]
            }
            val norm = kotlin.math.sqrt(centre.sumOf { (it * it).toDouble() }).toFloat() + 1e-9f
            for (d in 0 until dimension) centre[d] /= norm
            return centre
        }

        val domainCentre = centreOf(major.first, major.second)
        val contentCentre = centreOf(minor.first, minor.second)

        // домен — только от Старшего: слово задано вручную в domains.txt
        val minorRangeForDomain = rangeOf(major.first, major.second)
        val manual = Domains.of(major.first)
        val domainPick = (if (manual != null) {
            minorRangeForDomain.firstOrNull { keywords[it].equals(manual, ignoreCase = true) }
        } else null)
            ?: minorRangeForDomain.maxByOrNull { dot(centredAt(it), domainCentre) }
            ?: return SharedCore(emptyList(), emptyList(), 0f)
        val fieldScore = dot(centredAt(domainPick), domainCentre)

        // Внешнее кольцо — слова самого Старшего (его поле).
        // Внутреннее — слова Младшего. Ничего не заимствуется: у каждой карты свой голос.
        val domainWords = rangeOf(major.first, major.second)
            .sortedByDescending { dot(centredAt(it), domainCentre) }
            .map { keywords[it] }
            .distinctBy { it.lowercase().take(5) }
            .take(5)

        val innerWords = rangeOf(minor.first, minor.second)
            .sortedByDescending { dot(centredAt(it), contentCentre) }
            .map { keywords[it] }
            .distinctBy { it.lowercase().take(5) }
            .take(3)

        val out = LinkedHashMap<String, String>()
        innerWords.forEach { out[it.lowercase().take(5)] = it }
        val tags = out.values.toList()
        val scores = List(tags.size) { fieldScore }
        return SharedCore(tags, scores, fieldScore, domainWords)
    }

    /** Убираем общее направление корпуса: иначе выигрывают «никакие» слова. */
    private fun centredAt(index: Int): FloatArray = centredVectors[index]

    private fun centred(vector: FloatArray): FloatArray {
        if (mean == null) return vector
        val out = FloatArray(vector.size)
        for (i in vector.indices) out[i] = vector[i] - mean[i]
        val norm = kotlin.math.sqrt(out.sumOf { (it * it).toDouble() }).toFloat() + 1e-9f
        for (i in out.indices) out[i] /= norm
        return out
    }

    private fun dot(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) sum += a[i] * b[i]
        return sum
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

            val mean = FloatArray(cols)
            for (row in 0 until rows) for (column in 0 until cols) mean[column] += vectors[row][column]
            for (column in 0 until cols) mean[column] /= rows.toFloat()

            val centredVectors = Array(rows) { row ->
                val out = FloatArray(cols)
                for (column in 0 until cols) out[column] = vectors[row][column] - mean[column]
                var length = 0f
                for (value in out) length += value * value
                length = kotlin.math.sqrt(length) + 1e-9f
                for (column in 0 until cols) out[column] /= length
                out
            }

            return KeywordBook(
                vectors, shadowFlags, keywords, offsets, countsLight, countsShadow,
                frequency, mean, centredVectors
            )
        }
    }
}
