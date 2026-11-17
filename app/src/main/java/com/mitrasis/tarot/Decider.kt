package com.mitrasis.tarot

import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/**
 * Один вопрос — одна карта. Это single-label классификация по 78 меткам.
 *
 * Контракт ровно как у GLiNER2.5-Decide:
 *   вход  — текст вопроса + список меток (названия карт);
 *   выход — вероятности по меткам, ответ = argmax.
 *
 * Реализация подменяется целиком: заглушка сейчас, ONNX-граф потом.
 */
interface CardDecider {
    val name: String
    fun answer(question: String, layers: Int = 3): CardAnswer
}

/**
 * Заглушка. Детерминированная: один и тот же вопрос даёт одну и ту же карту.
 * Нужна только чтобы UI жил и чтобы форматы данных устоялись до модели.
 */
class StubCardDecider : CardDecider {

    override val name: String = "заглушка"

    override fun answer(question: String, layers: Int): CardAnswer {
        val normalized = question.trim().lowercase()
        val seed = if (normalized.isEmpty()) {
            0L
        } else {
            normalized.fold(17L) { acc, ch -> acc * 31L + ch.code }
        }
        val random = Random(seed)
        val labels = Deck.labelsRu

        val topIndex = ((seed % labels.size) + labels.size).toInt() % labels.size
        val topProbability = 0.28f + random.nextFloat() * 0.40f

        val rest = labels.indices.filter { it != topIndex }
        val raw = rest.map { -ln(0.05 + random.nextDouble()) }
        val sum = raw.sum().takeIf { it > 0.0 } ?: 1.0
        val remaining = 1f - topProbability

        val scored = rest.mapIndexed { i, index ->
            index to (remaining * (raw[i] / sum)).toFloat()
        }
        val runnerUp = scored.maxByOrNull { it.second }

        return CardAnswer(
            card = Deck.byId(topIndex),
            reversed = random.nextFloat() < 0.35f,
            probability = topProbability,
            runnerUp = runnerUp?.let { Deck.byId(it.first) to it.second }
        )
    }
}

/** Softmax пригодится и заглушке, и настоящему движку. */
fun softmax(logits: FloatArray, temperature: Float = 1f): FloatArray {
    if (logits.isEmpty()) return FloatArray(0)
    val scaled = logits.map { it / temperature }
    val max = scaled.max()
    val exps = scaled.map { exp((it - max).toDouble()) }
    val sum = exps.sum().takeIf { it > 0.0 } ?: 1.0
    return FloatArray(logits.size) { (exps[it] / sum).toFloat() }
}
