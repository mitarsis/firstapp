package com.mitrasis.tarot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

/**
 * Проверяет, что слова, похожие на названия карт, затираются.
 * Без этого модель отвечает картой «Солнце» на вопрос про Солнце —
 * это не ответ, а совпадение строк.
 *
 * Фикстура: base64(вопрос) \t base64(после маски)
 */
class CardWordMaskTest {

    private fun cases(): List<Pair<String, String>> {
        val file = File("src/test/resources/mask_cases.tsv")
        assertTrue("нет фикстуры: ${file.absolutePath}", file.exists())
        val decoder = Base64.getDecoder()
        return file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }.map { line ->
            val (question, expected) = line.split("\t", limit = 2)
            String(decoder.decode(question), Charsets.UTF_8) to
                String(decoder.decode(expected), Charsets.UTF_8)
        }
    }

    @Test
    fun `прячет названия карт и не трогает обычные слова`() {
        val mismatches = StringBuilder()
        for ((question, expected) in cases()) {
            val actual = CardWordMask.mask(question)
            if (actual != expected) {
                mismatches.append("\n  вопрос: ").append(question)
                mismatches.append("\n    ждали: ").append(expected)
                mismatches.append("\n    вышло: ").append(actual)
            }
        }
        File("test-mismatches.txt").writeText(mismatches.toString(), Charsets.UTF_8)
        assertEquals("расхождения в маске:$mismatches", 0, mismatches.length)
    }

    @Test
    fun `обычный вопрос остаётся целым`() {
        val question = "Стоит ли мне уйти с этой работы?"
        assertEquals(question, CardWordMask.mask(question))
    }
}
