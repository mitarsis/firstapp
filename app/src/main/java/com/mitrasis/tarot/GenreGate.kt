package com.mitrasis.tarot

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Жанр ответа: от него зависит форма текста. */
enum class Genre { FORECAST, DIAGNOSIS, ADVICE, TIMING }

/** Ось, на которой держится весь разбор. */
enum class Axis { BOUNDARIES, MOVEMENT, LOSS, EXCHANGE, WAITING }

/**
 * Определяет жанр вопроса и его ось по эмбеддингу.
 *
 * Раньше форма текста была одна на все вопросы: и на «стоит ли», и на «почему».
 * Теперь четыре эталонных вопроса задают жанр, а пять — ось.
 */
object GenreGate {

    private var genres: Array<FloatArray> = emptyArray()
    private var axes: Array<FloatArray> = emptyArray()

    fun load(ctx: Context) {
        if (genres.isNotEmpty()) return
        val raw = ctx.assets.open("genres.bin").use { it.readBytes() }
        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4).also { buffer.get(it) }
        require(String(magic) == "GAX1") { "неизвестный формат genres.bin" }
        val genreCount = buffer.int
        val axisCount = buffer.int
        val dim = buffer.int
        genres = Array(genreCount) { FloatArray(dim) }
        axes = Array(axisCount) { FloatArray(dim) }
        repeat(genreCount) { i ->
            val row = FloatArray(dim)
            buffer.asFloatBuffer().get(row)
            buffer.position(buffer.position() + dim * 4)
            genres[i] = normalise(row)
        }
        repeat(axisCount) { i ->
            val row = FloatArray(dim)
            buffer.asFloatBuffer().get(row)
            buffer.position(buffer.position() + dim * 4)
            axes[i] = normalise(row)
        }
    }

    fun decide(vector: FloatArray): Pair<Genre, Axis> {
        if (genres.isEmpty()) return Genre.FORECAST to Axis.MOVEMENT
        val genre = Genre.entries[argmax(vector, genres)]
        val axis = Axis.entries[argmax(vector, axes)]
        return genre to axis
    }

    private fun argmax(vector: FloatArray, table: Array<FloatArray>): Int {
        var best = 0
        var bestScore = -2f
        table.forEachIndexed { index, row ->
            val score = cosine(vector, row)
            if (score > bestScore) {
                bestScore = score
                best = index
            }
        }
        return best
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        return dot / (kotlin.math.sqrt(na) * kotlin.math.sqrt(nb) + 1e-9f)
    }

    private fun normalise(row: FloatArray): FloatArray {
        var sum = 0f
        for (v in row) sum += v * v
        val norm = kotlin.math.sqrt(sum) + 1e-9f
        for (i in row.indices) row[i] /= norm
        return row
    }
}
