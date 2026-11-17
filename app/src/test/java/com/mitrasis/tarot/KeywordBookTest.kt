package com.mitrasis.tarot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Проверяет, что файл тегов читается и что светлые и теневые значения
 * действительно различаются — это ядро нового чтения.
 */
class KeywordBookTest {

    private fun load(): KeywordBook {
        val file = File("src/main/assets/keywords.bin")
        assertTrue("нет assets/keywords.bin: ${file.absolutePath}", file.exists())
        return file.inputStream().buffered().use { KeywordBook.fromStream(it) }
    }

    @Test
    fun `файл тегов читается для всех 78 карт`() {
        val book = load()
        val vector = FloatArray(384) { 1f / 384f }
        for (cardIndex in 0 until 78) {
            val upright = book.bestKeyword(cardIndex, vector, reversed = false)
            val reversed = book.bestKeyword(cardIndex, vector, reversed = true)
            assertTrue("карта $cardIndex без светлого тега", upright.first.isNotBlank())
            assertTrue("карта $cardIndex без теневого тега", reversed.first.isNotBlank())
        }
    }

    @Test
    fun `светлый и теневой теги различаются`() {
        val book = load()
        val vector = FloatArray(384) { 1f / 384f }
        var different = 0
        for (cardIndex in 0 until 78) {
            val upright = book.bestKeyword(cardIndex, vector, false).first
            val reversed = book.bestKeyword(cardIndex, vector, true).first
            if (upright != reversed) different++
        }
        assertTrue("светлые и теневые теги почти не различаются: $different из 78", different > 60)
    }

    @Test
    fun `тег возвращается с осмысленной близостью`() {
        val book = load()
        val vector = FloatArray(384) { if (it % 3 == 0) 1f else 0f }
        val result = book.bestKeyword(0, vector, false)
        assertNotEquals("", result.first)
        assertTrue(result.second > -1f)
    }
}
