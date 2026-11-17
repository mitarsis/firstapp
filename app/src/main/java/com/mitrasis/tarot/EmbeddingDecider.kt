package com.mitrasis.tarot

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.LruCache
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Движок: e5-small (int8, 384 измерения).
 *
 * Порядок работы:
 *  1. Вопрос кодируется в вектор (кэшируется: повторная вытяжка по тому же
 *     вопросу не считает его заново).
 *  2. Из колоды тасуются POOL_SIZE случайных карт — жребий.
 *  3. Для каждой карты берётся её тег, ближайший к вопросу (светлый для прямой,
 *     теневой для перевёрнутой), — это и есть вклад модели.
 *  4. Три карты с лучшей близостью идут в ответ, первая — самая созвучная.
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

    // Вектор вопроса переиспользуется при повторных вытяжках
    private val vectorCache = object : LruCache<String, FloatArray>(8) {
        override fun sizeOf(key: String, value: FloatArray): Int = value.size * 4 / 1024 + 1
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

    override fun answer(question: String): CardAnswer {
        ensureLoaded()
        val clean = question.trim()
        require(clean.isNotEmpty()) { "пустой вопрос" }

        val vector = vectorCache.get(clean) ?: embed(QUESTION_PREFIX + clean).also {
            vectorCache.put(clean, it)
        }

        val random = kotlin.random.Random(System.nanoTime())
        val pool = (0 until cards.size).shuffled(random).take(POOL_SIZE)

        val entries = pool.map { index ->
            val reversed = random.nextFloat() < REVERSED_PERCENT / 100f
            val (keyword, score) = keywords.bestKeyword(index, vector, reversed)
            val card = Deck.byId(index)
            Triple(
                CardLine(
                    title = card.title,
                    keyword = keyword.ifBlank {
                        if (reversed) card.shadow.firstOrNull().orEmpty()
                        else card.light.firstOrNull().orEmpty()
                    },
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
                if (ownSession.inputNames.contains("token_type_ids")) {
                    OnnxTensor.createTensor(
                        environment,
                        LongBuffer.wrap(LongArray(length) { 0L }),
                        longArrayOf(1, length.toLong())
                    ).use { typeTensor ->
                        feeds["token_type_ids"] = typeTensor
                        hidden = runSession(ownSession, feeds)
                    }
                } else {
                    hidden = runSession(ownSession, feeds)
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

    private fun runSession(ownSession: OrtSession, feeds: Map<String, OnnxTensor>): FloatArray {
        ownSession.run(feeds).use { result ->
            val buffer = (result[0] as OnnxTensor).floatBuffer
            val flat = FloatArray(buffer.remaining())
            buffer.get(flat)
            return flat
        }
    }

    fun describeInputs(): String = try {
        ensureLoaded()
        "входы: ${session?.inputNames?.joinToString(",")} · карт: ${cards.size} · тегов: ${keywords.hashCode()}"
    } catch (t: Throwable) {
        "ошибка: ${t.javaClass.simpleName}: ${t.message}"
    }

    @Synchronized
    override fun close() {
        session?.close()
        session = null
        vectorCache.evictAll()
    }

    companion object {
        private const val TOKENIZER_ASSET = "tokenizer.bin"
        private const val CARDS_ASSET = "cards.bin"
        private const val KEYWORDS_ASSET = "keywords.bin"
        private const val HIDDEN_SIZE = 384
        private const val MAX_TOKENS = 128
        private const val QUESTION_PREFIX = "query: "
        private const val POOL_SIZE = 12
        private const val REVERSED_PERCENT = 35
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
