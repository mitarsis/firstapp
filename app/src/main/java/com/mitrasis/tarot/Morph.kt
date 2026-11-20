package com.mitrasis.tarot

import android.content.Context

/**
 * Падежи для словаря тегов.
 *
 * Формы посчитаны заранее (pymorphy3) и лежат в assets/morph.txt:
 * лемма|nomn|gent|datv|accs|ablt|loct|pl_nomn|pl_gent|pl_datv|pl_ablt
 */
object Morph {

    enum class Case { NOM, GEN, DAT, ACC, INS, PRE }

    private val table = HashMap<String, Array<String>>()
    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            ctx.assets.open("morph.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split("|")
                    if (parts.size < 11) return@forEach
                    table[parts[0]] = (1 until 11).map { parts[it] }.toTypedArray()
                }
            }
        }
    }

    /** Форма слова; если слова нет в таблице — возвращается как есть. */
    fun form(word: String, case: Case, plural: Boolean = false): String {
        val key = word.lowercase().trim('.', ',')
        val row = table[key] ?: return word
        val index = when (case) {
            Case.NOM -> if (plural) 6 else 0
            Case.GEN -> if (plural) 7 else 1
            Case.DAT -> if (plural) 8 else 2
            Case.ACC -> if (plural) 3 else 3
            Case.INS -> if (plural) 9 else 4
            Case.PRE -> if (plural) 5 else 5
        }
        return row.getOrElse(index) { word }
    }
}
