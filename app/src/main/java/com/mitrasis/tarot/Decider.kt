package com.mitrasis.tarot

import kotlin.math.exp
import kotlin.random.Random

/**
 * Схема типизированных вопросов — ровно то, что ест GLiNER2.5-Decide
 * (или её мультиязычный вариант multi-Decide). UI ничего не знает о том,
 * кто именно отвечает: заглушка сейчас, ONNX-модель потом.
 */
data class Question(
    val id: String,
    val prompt: String,
    val options: List<String>
)

data class OptionScore(
    val option: String,
    val probability: Float
)

data class Answer(
    val question: Question,
    val scores: List<OptionScore>
) {
    val winner: OptionScore get() = scores.maxByOrNull { it.probability } ?: scores.first()
}

data class DecisionResult(
    val answers: List<Answer>,
    val engine: String
) {
    val confidence: Float
        get() = answers.map { it.winner.probability }.average().toFloat()

    fun answer(questionId: String): Answer? = answers.firstOrNull { it.question.id == questionId }
}

interface Decider {
    val name: String
    fun decide(passage: String, schema: List<Question>): DecisionResult
}

/**
 * Заглушка. Никакой модели — только детерминированная псевдо-вероятностная
 * логика на основе текста пассажа и смысла карт. Нужна, чтобы довести UI
 * до рабочего состояния и потом заменить одну строчку.
 */
class StubDecider(private val drawn: List<Drawn>) : Decider {

    override val name: String = "заглушка (эвристика)"

    override fun decide(passage: String, schema: List<Question>): DecisionResult {
        val seed = passage.hashCode().toLong() * 31 + drawn.size
        val random = Random(seed)

        val lightWeight = drawn.sumOf { if (it.reversed) 0 else 1 }.toFloat()
        val shadowWeight = drawn.sumOf { if (it.reversed) 1 else 0 }.toFloat()
        val total = (drawn.size.coerceAtLeast(1)).toFloat()

        val answers = schema.map { question ->
            val raw = question.options.map { option ->
                val base = 0.4f + random.nextFloat() * 1.2f
                val bias = biasOf(option, lightWeight / total, shadowWeight / total)
                base * bias
            }
            Answer(question, softmax(question.options, raw))
        }
        return DecisionResult(answers, name)
    }

    private fun biasOf(option: String, light: Float, shadow: Float): Float = when {
        option.contains("светл") || option.contains("да") || option.contains("поддерж") ->
            0.7f + light * 1.3f
        option.contains("тревож") || option.contains("нет") || option.contains("тормоз") ->
            0.7f + shadow * 1.3f
        else -> 1.0f
    }

    private fun softmax(options: List<String>, values: List<Float>): List<OptionScore> {
        val max = values.maxOrNull() ?: 0f
        val exps = values.map { exp((it - max).toDouble()).toFloat() }
        val sum = exps.sum().takeIf { it > 0f } ?: 1f
        return options.indices.map { i ->
            OptionScore(options[i], exps[i] / sum)
        }
    }
}

/**
 * Собирает схему вопросов под конкретный расклад.
 */
fun buildSchema(spread: Spread, drawn: List<Drawn>): List<Question> {
    val list = mutableListOf<Question>()

    list += Question(
        id = "tone",
        prompt = "Общий тон расклада",
        options = listOf("светлый", "тревожный", "переломный", "пустой")
    )

    drawn.forEach { d ->
        list += Question(
            id = "pos_${d.position}",
            prompt = "Как ведёт себя позиция «${d.position}»",
            options = listOf("поддерживает", "тормозит", "запутывает", "требует действия")
        )
    }

    if (spread.positions.size > 1) {
        list += Question(
            id = "advice",
            prompt = "Что делать",
            options = listOf("ждать", "резать по живому", "договариваться", "менять тактику")
        )
    }

    list += Question(
        id = "outcome",
        prompt = "Исход",
        options = listOf("да", "нет", "не сейчас", "зависит от тебя")
    )

    return list
}

/**
 * Собирает текст пассажа для модели: вопрос пользователя + вытянутые карты.
 * Это вход, который увидит ONNX-модель на втором этапе.
 */
fun buildPassage(question: String, spread: Spread, drawn: List<Drawn>): String {
    val sb = StringBuilder()
    sb.append("Спрашивающий задаёт вопрос: ")
    sb.append(question.ifBlank { "без вопроса, просто смотрит на карты" })
    sb.append(". Расклад: ").append(spread.name).append(". ")
    drawn.forEach { d ->
        sb.append("Позиция «").append(d.position).append("»: карта ")
        sb.append(d.card.title).append(", ")
        sb.append(if (d.reversed) "перевёрнутая, значения — " else "прямая, значения — ")
        sb.append(d.activeKeywords().joinToString(", "))
        sb.append(". ")
    }
    return sb.toString()
}
