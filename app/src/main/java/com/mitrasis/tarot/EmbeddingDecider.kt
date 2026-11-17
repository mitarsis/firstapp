package com.mitrasis.tarot

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Движок на эмбеддингах (multilingual-e5-small, int8, 384 измерения).
 *
 * Идея проще предыдущей: вопрос кодируется один раз, векторы всех 78 карт
 * посчитаны заранее и лежат в assets/cards.bin. Ответ — косинусная близость.
 *
 * Почему так лучше прежнего: Gliner считала похожесть вопроса на текст метки
 * и на любом вопросе выдавала одну и ту же карту (Смерть, потом Жрицу, потом
 * Тройку Пентаклей). Здесь сравнение идёт между двумя векторами смысла,
 * а не между строкой вопроса и фразой метки.
 */
class EmbeddingDecider(private val ctx: Context) : CardDecider, AutoCloseable {

    override val name: String = "e5-small · int8 · 384d"

    private val tokenizer: UnigramTokenizer by lazy {
        ctx.assets.open(TOKENIZER_ASSET).use { UnigramTokenizer.fromStream(it) }
    }

    private val cards: CardsCloud by lazy {
        ctx.assets.open(CARDS_ASSET).use { CardsCloud.fromStream(it, Deck.labelsRu) }
    }

    private val keywords: KeywordBook by lazy {
        ctx.assets.open(KEYWORDS_ASSET).use { KeywordBook.fromStream(it) }
    }

    private val summaries: SummaryBook by lazy {
        val cardsMatrix = Array(cards.size) { cards.vectorOf(it) }
        ctx.assets.open(SUMMARIES_ASSET).use { SummaryBook.fromStream(it, cardsMatrix) }
    }

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null

    @Synchronized
    fun ensureLoaded() {
        if (session != null) return
        val environment = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setInterOpNumThreads(1)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        session = environment.createSession(ModelStore.encoderPath(ctx).absolutePath, options)
        env = environment
    }

    /**
     * Жребий — действительно жребий: три карты вытягиваются случайно.
     * Модель не выбирает карту, а оценивает, насколько выпавшая карта созвучна
     * вопросу: берёт её тег, ближайший к вопросу, и показывает эту близость.
     */
    override fun answer(question: String): CardAnswer {
        ensureLoaded()
        val clean = question.trim()
        require(clean.isNotEmpty()) { "пустой вопрос" }

        val vector = embed(QUESTION_PREFIX + clean)
        val random = kotlin.random.Random(System.nanoTime())

        // Жребий решает, из чего выбирать: двенадцать случайных карт.
        // Модель решает, что из них ближе к вопросу: три самых созвучных.
        val pool = (0 until cards.size).shuffled(random).take(POOL_SIZE)

        val entries = pool.map { index ->
            val (keyword, score) = keywords.bestKeywordWithScore(index, vector)
            val card = Deck.byId(index)
            val reversed = random.nextFloat() < REVERSED_PERCENT / 100f
            Triple(
                CardLine(
                    title = card.title,
                    keyword = if (reversed) card.shadow.firstOrNull().orEmpty() else keyword,
                    reversed = reversed
                ),
                relevance(score),
                index
            )
        }.sortedByDescending { it.second }.take(3)

        val main = entries.first()
        return CardAnswer(
            card = Deck.byId(main.third),
            reversed = main.first.reversed,
            variant = random.nextInt(1 shl 20),
            probability = main.second,
            runnerUp = entries.getOrNull(1)?.let { Deck.byId(it.third) to it.second },
            topic = CardMeanings.all.getOrNull(main.third)?.full.orEmpty(),
            alternatives = entries.drop(1).map { Deck.byId(it.third) to it.second },
            lines = entries.map { it.first }
        )
    }

    /** Сырой косинус (обычно 0.62-0.92) превращаем в понятную долю. */
    private fun relevance(cosine: Float): Float =
        ((cosine - 0.62f) / 0.30f).coerceIn(0.02f, 1f)

    /** Итог расклада: фраза, ближайшая к трём картам с учётом их веса. */
    private fun summarize(trio: List<Pair<Int, Float>>): String {
        val valid = trio.filter { it.first in 0 until cards.size }
        if (valid.isEmpty()) return ""
        val seed = valid.joinToString("|") { it.first.toString() }.hashCode() + valid.size
        return summaries.nearestForCards(valid.map { cards.vectorOf(it.first) to it.second }, seed)
    }

    /**
     * Ориентация карты. Модель её не предсказывает — это честный жребий,
     * детерминированный от пары «вопрос + карта»: тот же вопрос даёт ту же
     * ориентацию, но на разных вопросах распределение примерно 35/65.
     */
    private fun isReversed(question: String, cardMeaning: String): Boolean {
        val hash = (question.lowercase().trim() + "|" + cardMeaning).hashCode()
        return (hash and 0x7fffffff) % 100 < REVERSED_PERCENT
    }

    /** Вектор текста: усреднение скрытых состояний по маске, потом нормировка. */
    private fun embed(text: String): FloatArray {
        val ids = tokenizer.encode(text).map { it.toLong() }.toLongArray()
        val truncated = if (ids.size > MAX_TOKENS) ids.copyOf(MAX_TOKENS) else ids
        val length = truncated.size

        val environment = env ?: throw IllegalStateException("окружение ONNX не готово")
        val ownSession = session ?: throw IllegalStateException("модель не загружена")

        val hidden: FloatArray
        OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(truncated),
            longArrayOf(1, length.toLong())
        ).use { idsTensor ->
            OnnxTensor.createTensor(
                environment,
                LongBuffer.wrap(LongArray(length) { 1L }),
                longArrayOf(1, length.toLong())
            ).use { maskTensor ->
                val feeds = mutableMapOf("input_ids" to idsTensor, "attention_mask" to maskTensor)
                if (session?.inputNames?.contains("token_type_ids") == true) {
                    OnnxTensor.createTensor(
                        environment,
                        LongBuffer.wrap(LongArray(length) { 0L }),
                        longArrayOf(1, length.toLong())
                    ).use { typeTensor ->
                        feeds["token_type_ids"] = typeTensor
                        hidden = runSession(ownSession, feeds, length)
                    }
                } else {
                    hidden = runSession(ownSession, feeds, length)
                }
            }
        }

        val vector = FloatArray(HIDDEN_SIZE)
        for (position in 0 until length) {
            for (dimension in 0 until HIDDEN_SIZE) {
                vector[dimension] += hidden[position * HIDDEN_SIZE + dimension]
            }
        }
        var norm = 0f
        for (value in vector) norm += value * value
        norm = kotlin.math.sqrt(norm).takeIf { it > 0f } ?: 1f
        for (index in vector.indices) vector[index] /= norm
        return vector
    }

    private fun runSession(
        ownSession: OrtSession,
        feeds: Map<String, OnnxTensor>,
        length: Int
    ): FloatArray {
        ownSession.run(feeds).use { result ->
            val buffer = (result[0] as OnnxTensor).floatBuffer
            val flat = FloatArray(buffer.remaining())
            buffer.get(flat)
            return flat
        }
    }

    fun describeInputs(): String = try {
        ensureLoaded()
        "входы: ${session?.inputNames?.joinToString(",")} · карт: ${cards.size}"
    } catch (t: Throwable) {
        "ошибка: ${t.javaClass.simpleName}: ${t.message}"
    }

    @Synchronized
    override fun close() {
        session?.close()
        session = null
    }

    companion object {
        private const val TOKENIZER_ASSET = "tokenizer.bin"
        private const val CARDS_ASSET = "cards.bin"
        private const val SUMMARIES_ASSET = "summaries.bin"
        private const val KEYWORDS_ASSET = "keywords.bin"
        private const val HIDDEN_SIZE = 384
        private const val MAX_TOKENS = 128
        private const val QUESTION_PREFIX = "query: "

        /** Доля перевёрнутых карт, в процентах. */
        private const val REVERSED_PERCENT = 35

        /** Сколько близких карт участвует в распределении. */
        private const val SHORTLIST = 5
        private const val POOL_SIZE = 12

        /** Насколько резко разводим близкие косинусы в вероятности. */
        private const val SHARPNESS = 120f
    }
}

/**
 * Матрица векторов карт: "CARV1", rows:i32, cols:i32, далее float32 little-endian.
 */
class CardsCloud private constructor(
    private val vectors: Array<FloatArray>,
    private val meanings: List<String>
) {

    val size: Int get() = vectors.size

    fun similarities(vector: FloatArray): FloatArray =
        FloatArray(vectors.size) { index ->
            var sum = 0f
            val card = vectors[index]
            for (dimension in card.indices) sum += card[dimension] * vector[dimension]
            sum
        }

    fun meaningOf(index: Int): String = meanings.getOrElse(index) { "" }

    fun vectorOf(index: Int): FloatArray = vectors[index]

    companion object {
        fun fromStream(stream: java.io.InputStream, titles: List<String>): CardsCloud {
            val bytes = stream.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(5)
            buffer.get(magic)
            require(String(magic, Charsets.US_ASCII) == "CARV1") { "bad cards magic" }
            val rows = buffer.int
            val cols = buffer.int
            val vectors = Array(rows) { FloatArray(cols) }
            for (row in 0 until rows) {
                for (column in 0 until cols) {
                    vectors[row][column] = buffer.float
                }
            }
            val meanings = CardMeanings.all.map { it.full }
            return CardsCloud(vectors, meanings)
        }
    }
}
