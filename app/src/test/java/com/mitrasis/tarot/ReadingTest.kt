package com.mitrasis.tarot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Проверяет согласование прилагательного и структуру текста. */
class ReadingTest {

    private fun answer(vararg keywords: String): CardAnswer {
        val lines = keywords.mapIndexed { index, keyword ->
            CardLine(Deck.byId(index).title, keyword, reversed = false)
        }
        return CardAnswer(
            card = Deck.byId(0),
            reversed = false,
            probability = 0.5f,
            runnerUp = null,
            alternatives = emptyList(),
            lines = lines.toList()
        )
    }

    @Test
    fun `множественное число даёт множественное прилагательное`() {
        val text = Reading.compose(answer("новости", "перемена")).joinToString(" ")
        assertTrue("нет множественного прилагательного: $text", text.contains("ые ") || text.contains("ие "))
    }

    @Test
    fun `женский род склоняется в женский`() {
        val text = Reading.compose(answer("помощь")).joinToString(" ")
        assertTrue("нет женского прилагательного: $text", text.contains("ая "))
    }

    @Test
    fun `текст — одна фраза со всеми картами`() {
        val paragraphs = Reading.compose(answer("уход", "переход", "обрыв"))
        assertEquals(1, paragraphs.size)
        val text = paragraphs.first()
        assertTrue("нет всех трёх значений: $text", text.contains("уход") && text.contains("переход") && text.contains("обрыв"))
    }
}
