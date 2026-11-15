package com.mitrasis.tarot

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.Locale

/**
 * Настоящий движок: GLiNER2 multi-v1 (int8 + 4-битные эмбеддинги) через ONNX Runtime.
 *
 * Вход  : "(" [P] "category" "(" ([L] метка)* ")" ")" [SEP_TEXT] слово слово...
 *         слова — в нижнем регистре, каждое кодируется отдельно (как в эталоне).
 * Выход : логиты по меткам -> softmax.
 *
 * Двухступенчато: сначала тема (7 меток), затем все 78 карт с темой в тексте.
 * На замерах это давало лучшие ответы, чем один проход по 78 меткам.
 */
class GlinerDecider(private val ctx: Context) : CardDecider {

    override val name: String = "GLiNER2 multi-v1 · int8/w4"

    private val tokenizer: UnigramTokenizer by lazy {
        ctx.assets.open(TOKENIZER_ASSET).use { UnigramTokenizer.fromStream(it) }
    }

    private var env: OrtEnvironment? = null
    private var encoder: OrtSession? = null
    private var classifier: OrtSession? = null

    @Synchronized
    fun ensureLoaded() {
        if (encoder != null && classifier != null) return
        val environment = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setInterOpNumThreads(1)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        encoder = environment.createSession(ModelStore.encoderPath(ctx).absolutePath, options)
        classifier = environment.createSession(ModelStore.classifierPath(ctx).absolutePath, options)
        env = environment
    }

    @Synchronized
    fun close() {
        encoder?.close()
        classifier?.close()
        encoder = null
        classifier = null
    }

    override fun answer(question: String): CardAnswer {
        ensureLoaded()
        val clean = question.trim()
        require(clean.isNotEmpty()) { "пустой вопрос" }

        val topic = classify(clean, TOPICS).first().first
        val payload = "Вопрос о следующем: $topic. $clean"

        val scores = classify(payload, Deck.labelsRu)
        val best = scores[0]
        val second = scores.getOrNull(1)

        return CardAnswer(
            card = Deck.byId(Deck.labelsRu.indexOf(best.first)),
            reversed = false,
            probability = best.second,
            runnerUp = second?.let { Deck.byId(Deck.labelsRu.indexOf(it.first)) to it.second },
            topic = topic
        )
    }

    /** Метка -> вероятность, по убыванию. */
    private fun classify(text: String, labels: List<String>): List<Pair<String, Float>> {
        val built = buildInput(text, labels)
        val environment = env ?: throw IllegalStateException("окружение ONNX не готово")
        val encoderSession = encoder ?: throw IllegalStateException("энкодер не загружен")
        val classifierSession = classifier ?: throw IllegalStateException("классификатор не загружен")

        val sequenceLength = built.inputIds.size
        val hidden: FloatArray

        OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(built.inputIds),
            longArrayOf(1, sequenceLength.toLong())
        ).use { idsTensor ->
            OnnxTensor.createTensor(
                environment,
                LongBuffer.wrap(LongArray(sequenceLength) { 1L }),
                longArrayOf(1, sequenceLength.toLong())
            ).use { maskTensor ->
                encoderSession.run(
                    mapOf("input_ids" to idsTensor, "attention_mask" to maskTensor)
                ).use { result ->
                    val buffer = (result[0] as OnnxTensor).floatBuffer
                    hidden = FloatArray(buffer.remaining())
                    buffer.get(hidden)
                }
            }
        }

        val markers = built.labelPositions.size
        val gathered = FloatArray(markers * HIDDEN_SIZE)
        built.labelPositions.forEachIndexed { index, position ->
            System.arraycopy(hidden, position * HIDDEN_SIZE, gathered, index * HIDDEN_SIZE, HIDDEN_SIZE)
        }

        val logits: FloatArray
        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(gathered),
            longArrayOf(markers.toLong(), HIDDEN_SIZE.toLong())
        ).use { embeddingTensor ->
            classifierSession.run(mapOf("hidden_state" to embeddingTensor)).use { result ->
                val buffer = (result[0] as OnnxTensor).floatBuffer
                logits = FloatArray(buffer.remaining())
                buffer.get(logits)
            }
        }

        val probabilities = softmax(logits)
        return labels.indices
            .map { labels[it] to probabilities[it] }
            .sortedByDescending { it.second }
    }

    private class BuiltInput(
        val inputIds: LongArray,
        val labelPositions: List<Int>
    )

    private fun buildInput(text: String, labels: List<String>): BuiltInput {
        val ids = ArrayList<Int>(512)

        fun addEncoded(value: String) {
            for (id in tokenizer.encode(value)) ids.add(id)
        }

        addEncoded("(")
        ids.add(TOKEN_P)
        addEncoded("category")
        addEncoded("(")

        val labelPositions = ArrayList<Int>(labels.size)
        for (label in labels) {
            labelPositions.add(ids.size)
            ids.add(TOKEN_L)
            addEncoded(label)
        }

        addEncoded(")")
        addEncoded(")")
        ids.add(TOKEN_SEP_TEXT)

        val lowered = text.lowercase(Locale.ROOT)
        for (match in WORD_PATTERN.findAll(lowered)) {
            addEncoded(match.value)
        }

        return BuiltInput(LongArray(ids.size) { ids[it].toLong() }, labelPositions)
    }

    companion object {
        private const val TOKENIZER_ASSET = "tokenizer.bin"
        private const val HIDDEN_SIZE = 768
        private const val TOKEN_SEP_TEXT = 250103
        private const val TOKEN_P = 250104
        private const val TOKEN_L = 250108

        /** Порт WORD_PATTERN из эталона: (?U) включает юникодные \w, иначе кириллица не ловится. */
        private val WORD_PATTERN = Regex(
            pattern = "(?U)(?:https?://[^\\s]+|www\\.[^\\s]+)" +
                "|[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}" +
                "|@[a-z0-9_]+" +
                "|\\w+(?:[-_]\\w+)*" +
                "|\\S",
            option = RegexOption.IGNORE_CASE
        )

        private val TOPICS = listOf(
            "отношения и любовь, партнёр, ссоры",
            "деньги, доход, долги, работа и карьера",
            "здоровье, тело, усталость",
            "выбор между двумя вариантами, решение",
            "потеря, конец, расставание",
            "семья, дом, быт",
            "сам вопрос непонятен или спрашивающий лукавит"
        )
    }
}
