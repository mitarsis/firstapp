package com.mitrasis.tarot

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer

/**
 * Токенизатор GLiNER2 (mDeBERTa-v3): Unigram + Metaspace.
 *
 * Перенос один в один с эталонной реализации:
 *  - нормализация  : Precompiled charsmap ≈ NFKC + выкидывание control/format
 *                    + любой whitespace в пробел + схлопывание пробелов + trim
 *  - пре-токенизация: пробел -> "▁", префикс "▁" всегда (prepend_scheme=always)
 *  - модель        : Viterbi по Unigram с оценками из словаря
 *
 * Словарь лежит в assets/tokenizer.bin: "GLNV1", count:i32, unk:i32,
 * далее count раз: len:u16, utf8-байты, score:f32 (всё little-endian).
 */
class UnigramTokenizer private constructor(
    private val vocab: HashMap<String, Int>,
    private val scores: FloatArray,
    private val unkId: Int,
    private val maxPieceLen: Int
) {

    private val minScore: Float = scores.minOrNull() ?: 0f

    fun encode(text: String): IntArray {
        val ids = ArrayList<Int>(32)
        pieces(text).forEach { piece ->
            ids.add(vocab[piece] ?: unkId)
        }
        return ids.toIntArray()
    }

    /** Возвращает куски сегментации (до маппинга в id) — удобно для отладки и тестов. */
    fun pieces(text: String): List<String> {
        val normalized = clean(text)
        val prepared = METASPACE + normalized.replace(' ', METASPACE)
        val n = prepared.length
        val bestScore = FloatArray(n + 1) { NEG_INF }
        val bestPrev = IntArray(n + 1) { -1 }
        val bestPiece = arrayOfNulls<String>(n + 1)
        bestScore[0] = 0f

        for (i in 0 until n) {
            if (bestScore[i] == NEG_INF) continue
            val base = bestScore[i]
            var matched = false
            val upper = minOf(i + maxPieceLen, n)
            for (j in upper downTo i + 1) {
                val piece = prepared.substring(i, j)
                val id = vocab[piece] ?: continue
                matched = true
                val candidate = base + scores[id]
                if (candidate > bestScore[j]) {
                    bestScore[j] = candidate
                    bestPrev[j] = i
                    bestPiece[j] = piece
                }
            }
            if (!matched) {
                val candidate = base + minScore
                if (candidate > bestScore[i + 1]) {
                    bestScore[i + 1] = candidate
                    bestPrev[i + 1] = i
                    bestPiece[i + 1] = prepared.substring(i, i + 1)
                }
            }
        }

        val out = ArrayList<String>(n / 2 + 1)
        var cur = n
        while (cur > 0) {
            val prev = bestPrev[cur]
            if (prev < 0) break
            out.add(bestPiece[cur] ?: prepared.substring(prev, cur))
            cur = prev
        }
        out.reverse()
        return out
    }

    companion object {
        private const val METASPACE = "\u2581"
        private const val NEG_INF = -1e18f
        private const val MAGIC = "GLNV1"

        fun fromStream(stream: InputStream): UnigramTokenizer {
            val bytes = stream.readBytes()
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(MAGIC.length)
            buf.get(magic)
            require(String(magic, Charsets.US_ASCII) == MAGIC) { "bad tokenizer magic" }
            val count = buf.int
            val unkId = buf.int
            val vocab = HashMap<String, Int>(count * 2)
            val scores = FloatArray(count)
            var maxLen = 0
            for (i in 0 until count) {
                val len = buf.short.toInt() and 0xFFFF
                val raw = ByteArray(len)
                buf.get(raw)
                val piece = String(raw, Charsets.UTF_8)
                scores[i] = buf.float
                vocab[piece] = i
                if (piece.length > maxLen) maxLen = piece.length
            }
            return UnigramTokenizer(vocab, scores, unkId, maxLen + 2)
        }

        /**
         * Аппроксимация Precompiled charsmap из SentencePiece.
         * Порядок операций важен и повторяет эталон: сначала чистка и сжатие
         * пробелов, потом NFKC.
         */
        internal fun clean(text: String): String {
            val sb = StringBuilder(text.length + 1)
            for (ch in text) {
                val type = Character.getType(ch)
                val junk = type == Character.CONTROL.toInt() ||
                    type == Character.FORMAT.toInt() ||
                    type == Character.SURROGATE.toInt() ||
                    type == Character.PRIVATE_USE.toInt() ||
                    type == Character.UNASSIGNED.toInt()
                if (junk || ch.isWhitespace() || Character.isSpaceChar(ch)) {
                    sb.append(' ')
                } else {
                    sb.append(ch)
                }
            }
            val squeezed = sb.split(' ').filter { it.isNotEmpty() }.joinToString(" ")
            return Normalizer.normalize(squeezed, Normalizer.Form.NFKC)
        }
    }
}
