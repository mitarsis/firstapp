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

        val vector = embed(QUESTION_PREFIX + clean)
        val similarities = cards.similarities(vector)
        val order = similarities.indices.sortedByDescending { similarities[it] }

        val best = order.first()
        if (similarities[best] < NO_ANSWER_THRESHOLD) {
            return CardAnswer(
                card = Deck.byId(0),
                reversed = false,
                probability = similarities[best],
                runnerUp = null,
                topic = "",
                alternatives = emptyList(),
                noAnswer = true
            )
        }

        // Сэмплирование только по тем картам, что близки к лучшей — иначе
        // пришлось бы тянуть карту с вдвое меньшим сходством.
        val threshold = similarities[best] * DROP_RATIO
        val pool = order.take(TOP_K).filter { similarities[it] >= threshold }.ifEmpty { order.take(1) }
        val pickedIndex = pool[kotlin.random.Random.nextInt(pool.size)]

        val alternatives = order
            .filter { it != pickedIndex }
            .take(2)
            .map { Deck.byId(it) to similarities[it].coerceIn(0f, 1f) }

        return CardAnswer(
            card = Deck.byId(pickedIndex),
            reversed = false,
            probability = similarities[pickedIndex].coerceIn(0f, 1f),
            runnerUp = alternatives.firstOrNull(),
            topic = cards.meaningOf(pickedIndex),
            alternatives = alternatives
        )
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
        private const val HIDDEN_SIZE = 384
        private const val MAX_TOKENS = 128
        private const val QUESTION_PREFIX = "query: "

        /** Ниже этого косинуса считаем, что карты не про вопрос. */
        private const val NO_ANSWER_THRESHOLD = 0.72f

        /** Сэмплируем только карты, чьё сходство не хуже этой доли от лучшей. */
        private const val DROP_RATIO = 0.93f
        private const val TOP_K = 5
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
