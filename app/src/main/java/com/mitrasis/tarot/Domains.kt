package com.mitrasis.tarot

import android.content.Context

/**
 * Домен Старшего аркана — задан вручную, по одному слову на карту.
 *
 * Статистика («тег, ближайший к центру») даёт осечки: у Звезды Уэйт первым
 * словом пишет «лишение», хотя карта про надежду. Двадцать два слова,
 * проверенных по тексту и по смыслу, надёжнее любого усреднения.
 */
object Domains {

    private val table = HashMap<Int, String>()
    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            ctx.assets.open("domains.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val cut = line.indexOf('|')
                    if (cut <= 0) return@forEach
                    val index = line.substring(0, cut).trim().toIntOrNull() ?: return@forEach
                    table[index] = line.substring(cut + 1).trim()
                }
            }
        }
    }

    fun of(cardIndex: Int): String? = table[cardIndex]
}
