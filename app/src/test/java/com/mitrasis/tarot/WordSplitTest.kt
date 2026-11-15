package com.mitrasis.tarot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

/**
 * Проверяет разрезание текста на слова перед отправкой в модель.
 * Раньше здесь стоял инлайн-флаг (?U), который Android не понимает —
 * паттерн падал на компиляции. Тест ловит такие вещи на JVM в CI.
 *
 * Фикстура: base64(текст) \t base64(слова через \n)
 */
class WordSplitTest {

    private fun cases(): List<Pair<String, List<String>>> {
        val f = File("src/test/resources/word_split_cases.tsv")
        assertTrue("нет фикстуры: ${f.absolutePath}", f.exists())
        val decoder = Base64.getDecoder()
        return f.readLines(Charsets.UTF_8).filter { it.isNotBlank() }.map { line ->
            val (textB64, wordsB64) = line.split("\t", limit = 2)
            val text = String(decoder.decode(textB64), Charsets.UTF_8)
            val words = String(decoder.decode(wordsB64), Charsets.UTF_8)
                .split("\n")
                .filter { it.isNotEmpty() }
            text to words
        }
    }

    @Test
    fun `режет текст так же, как эталон`() {
        val mismatches = StringBuilder()
        for ((text, expected) in cases()) {
            val actual = PromptBuilder.splitWords(text)
            if (actual != expected) {
                mismatches.append("\n  текст: ").append(text)
                mismatches.append("\n    ждали: ").append(expected)
                mismatches.append("\n    вышло: ").append(actual)
            }
        }
        File("test-mismatches.txt").writeText(mismatches.toString(), Charsets.UTF_8)
        assertEquals("расхождения в разбиении слов:$mismatches", 0, mismatches.length)
    }
}
