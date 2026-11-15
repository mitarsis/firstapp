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
 */
class GlinerDecider(private val ctx: Context) : CardDecider, AutoCloseable {

    override val name: String = "GLiNER2 multi-v1 · int8/w4"

    private val tokenizer: UnigramTokenizer by lazy {
        ctx.assets.open(TOKENIZER_ASSET).use { UnigramTokenizer.fromStream(it) }
    }
    private val promptBuilder: PromptBuilder by lazy { PromptBuilder(tokenizer) }

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

    fun describeInputs(): String = try {
        ensureLoaded()
        val enc = encoder?.inputNames?.joinToString(",") ?: "?"
        val cls = classifier?.inputNames?.joinToString(",") ?: "?"
        "encoder[$enc] classifier[$cls]"
    } catch (t: Throwable) {
        "ошибка: ${t.javaClass.simpleName}: ${t.message}"
    }

    override fun answer(question: String): CardAnswer {
        ensureLoaded()
        val clean = question.trim()
        require(clean.isNotEmpty()) { "пустой вопрос" }

        // Шаг 1: сфера вопроса.
        val topicLabels = CardMeanings.topics.map { it.first }
        val topicProbabilities = probabilities(clean, topicLabels, FloatArray(topicLabels.size))
        val topicLabel = topicLabels[topicProbabilities.indices.maxByOrNull { topicProbabilities[it] } ?: 0]
        val suit = CardMeanings.topics.first { it.first == topicLabel }.second

        // Шаг 2: облака тегов по Старшим и картам нужной масти.
        val (baseLabels, baseTitles) = CardMeanings.candidates(suit)
        val labels = baseLabels + NO_ANSWER_LABEL
        val titles = baseTitles + NO_ANSWER_LABEL
        val biases = Bias.forTitles(suit.ifEmpty { "majors" }, titles)

        val probabilities = probabilities(clean, labels, biases)

        // Топ-k сэмплирование: разнообразие без превращения в рандомайзер.
        val order = probabilities.indices.sortedByDescending { probabilities[it] }
        val top = order.take(TOP_K)
        val picked = top[weightedPick(top, probabilities)]
        val winnerTitle = titles[picked]

        if (winnerTitle == NO_ANSWER_LABEL) {
            return CardAnswer(
                card = Deck.byId(0),
                reversed = false,
                probability = probabilities[picked],
                runnerUp = null,
                topic = "",
                alternatives = emptyList(),
                noAnswer = true
            )
        }

        val alternatives = order
            .filter { it != picked && titles[it] != NO_ANSWER_LABEL }
            .take(2)
            .map { Deck.byId(Deck.labelsRu.indexOf(titles[it])) to probabilities[it] }

        return CardAnswer(
            card = Deck.byId(Deck.labelsRu.indexOf(winnerTitle)),
            reversed = false,
            probability = probabilities[picked],
            runnerUp = alternatives.firstOrNull(),
            topic = labels[picked],
            alternatives = alternatives
        )
    }

    /** Вероятности с учётом калибровки и температуры. */
    private fun probabilities(text: String, labels: List<String>, biases: FloatArray): FloatArray {
        val built = promptBuilder.build(text, labels)
        val raw = runModel(built, labels.size)
        val corrected = FloatArray(raw.size) { raw[it] - (biases.getOrNull(it) ?: 0f) }
        val scaled = FloatArray(corrected.size) { corrected[it] / TEMPERATURE }
        val max = scaled.max()
        val exps = FloatArray(scaled.size) { kotlin.math.exp((scaled[it] - max).toDouble()).toFloat() }
        val sum = exps.sum().takeIf { it > 0f } ?: 1f
        return FloatArray(exps.size) { exps[it] / sum }
    }

    private fun weightedPick(candidates: List<Int>, probabilities: FloatArray): Int {
        val weights = candidates.map { probabilities[it] }
        val total = weights.sum().takeIf { it > 0f } ?: return 0
        var threshold = kotlin.random.Random.nextFloat() * total
        for (index in candidates.indices) {
            threshold -= weights[index]
            if (threshold <= 0f) return index
        }
        return candidates.lastIndex
    }

    /** Логиты по меткам: энкодер -> склейка [L]-позиций -> классификатор. */
    private fun runModel(built: PromptBuilder.BuiltInput, markerCount: Int): FloatArray {
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

        val gathered = FloatArray(markerCount * HIDDEN_SIZE)
        built.labelPositions.forEachIndexed { index, position ->
            System.arraycopy(hidden, position * HIDDEN_SIZE, gathered, index * HIDDEN_SIZE, HIDDEN_SIZE)
        }

        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(gathered),
            longArrayOf(markerCount.toLong(), HIDDEN_SIZE.toLong())
        ).use { embeddingTensor ->
            classifierSession.run(mapOf("hidden_state" to embeddingTensor)).use { result ->
                val buffer = (result[0] as OnnxTensor).floatBuffer
                val logits = FloatArray(buffer.remaining())
                buffer.get(logits)
                return logits
            }
        }
    }

    @Synchronized
    override fun close() {
        encoder?.close()
        classifier?.close()
        encoder = null
        classifier = null
    }

    companion object {
        private const val TOKENIZER_ASSET = "tokenizer.bin"
        private const val HIDDEN_SIZE = 768
    }
}

/**
 * Сборка промпта GLiNER2. Вынесено отдельно, чтобы можно было проверить
 * без ONNX (и без модели).
 */
class PromptBuilder(private val tokenizer: UnigramTokenizer) {

    class BuiltInput(val inputIds: LongArray, val labelPositions: List<Int>)

    fun build(text: String, labels: List<String>): BuiltInput {
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

        // Вопрос обрезаем по бюджету токенов: у модели окно 512, а облака
        // тегов занимают почти всё. Лишние слова просто не влезают.
        val lowered = text.lowercase(Locale.ROOT)
        for (match in WORD_PATTERN.findAll(lowered)) {
            val before = ids.size
            addEncoded(match.value)
            if (ids.size > MAX_TOKENS) {
                while (ids.size > before) ids.removeAt(ids.size - 1)
                break
            }
        }

        return BuiltInput(LongArray(ids.size) { ids[it].toLong() }, labelPositions)
    }

    companion object {
        private const val TOKEN_SEP_TEXT = 250103
        private const val TOKEN_P = 250104
        private const val TOKEN_L = 250108
        private const val MAX_TOKENS = 500
        private const val TOP_K = 5
        private const val TEMPERATURE = 0.9f
        private const val NO_ANSWER_LABEL = "вопрос не по адресу, ответа нет"

        /**
         * Порт WORD_PATTERN из эталона.
         * Важно: \w заменён на [\p{L}\p{N}_], потому что инлайн-флаг (?U)
         * в Android-регулярке не поддерживается, а без него \w не видит кириллицу.
         */
        private val WORD_PATTERN = Regex(
            pattern = "(?:https?://[^\\s]+|www\\.[^\\s]+)" +
                "|[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}" +
                "|@[a-z0-9_]+" +
                "|[\\p{L}\\p{N}_]+(?:[-_][\\p{L}\\p{N}_]+)*" +
                "|\\S",
            option = RegexOption.IGNORE_CASE
        )

        internal fun splitWords(text: String): List<String> =
            WORD_PATTERN.findAll(text.lowercase(Locale.ROOT)).map { it.value }.toList()
    }
}
