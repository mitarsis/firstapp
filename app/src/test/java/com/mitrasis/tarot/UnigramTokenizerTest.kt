package com.mitrasis.tarot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

/**
 * Сверяет Kotlin-порт с эталонным токенизатором HF.
 * Фикстура: app/src/test/resources/tokenizer_cases.tsv,
 * строка = base64(текст) \t id,id,id
 * Внутри 26 случаев, включая грязь: табы, NBSP, лигатуры, эмодзи,
 * полноширинные символы, zero-width, контрольные байты, композитные акценты.
 */
class UnigramTokenizerTest {

    private fun loadTokenizer(): UnigramTokenizer {
        val f = File("src/main/assets/tokenizer.bin")
        assertTrue("нет assets/tokenizer.bin: ${f.absolutePath}", f.exists())
        return f.inputStream().buffered().use { UnigramTokenizer.fromStream(it) }
    }

    private fun fixture(): List<Pair<String, IntArray>> {
        val f = File("src/test/resources/tokenizer_cases.tsv")
        assertTrue("нет фикстуры: ${f.absolutePath}", f.exists())
        return f.readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() }
            .map { line ->
                val (b64, idsRaw) = line.split("\t", limit = 2)
                val text = String(Base64.getDecoder().decode(b64), Charsets.UTF_8)
                val ids = idsRaw.split(",").map { it.trim().toInt() }.toIntArray()
                text to ids
            }
    }

    @Test
    fun `совпадает с эталонным токенизатором`() {
        val tokenizer = loadTokenizer()
        val cases = fixture()
        val failures = StringBuilder()

        for ((text, expected) in cases) {
            val actual = tokenizer.encode(text)
            if (!actual.contentEquals(expected)) {
                failures.append("\n  текст: ").append(text.replace("\n", "\\n"))
                failures.append("\n    ждали: ").append(expected.joinToString(","))
                failures.append("\n    вышло: ").append(actual.joinToString(","))
                failures.append("\n    куски: ").append(tokenizer.pieces(text).joinToString("|"))
            }
        }

        File("test-mismatches.txt").writeText(failures.toString(), Charsets.UTF_8)
        if (failures.isNotEmpty()) {
            throw AssertionError("расхождения с эталоном:$failures")
        }
        println("токенизатор: ${cases.size} случаев совпали полностью")
    }

    @Test
    fun `единичный пример`() {
        val tokenizer = loadTokenizer()
        assertArrayEquals(intArrayOf(689, 10132, 1068), tokenizer.encode("Смерть"))
    }
}
