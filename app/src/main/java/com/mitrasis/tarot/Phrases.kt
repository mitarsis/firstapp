package com.mitrasis.tarot

import android.content.Context

/**
 * Банк связок из assets/phrases.txt.
 *
 * Формат строки: слот|шаблон. Шаблон содержит плейсхолдеры:
 * {v1} {v2} {v3} — именительный, {d2} {d3} — дательный,
 * {g1} — родительный, {scene} — образ, {adj} — эпитет, {a} {b} — антонимы.
 */
object Phrases {

    private val slots = HashMap<String, List<String>>()
    private var loaded = false

    fun load(ctx: Context) {
        ctxRef = ctx
        if (loaded) return
        loaded = true
        runCatching {
            ctx.assets.open("phrases.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val cut = line.indexOf('|')
                    if (cut <= 0) return@forEach
                    val key = line.substring(0, cut).trim()
                    val text = line.substring(cut + 1).trim()
                    if (text.isEmpty()) return@forEach
                    slots[key] = (slots[key] ?: emptyList()) + text
                }
            }
        }
    }

    fun of(key: String): List<String> = slots[key] ?: emptyList()

    private val skeletons = HashMap<String, List<List<String>>>()

    /** Порядок блоков: сначала по жанру, если нет — общий набор. */
    fun skeleton(genre: String, seed: Int): List<String> {
        if (skeletons.isEmpty()) {
            runCatching {
                ctxRef?.assets?.open("skeletons.txt")?.bufferedReader()?.useLines { lines ->
                    lines.forEach { line ->
                        val cut = line.indexOf('|')
                        if (cut <= 0) return@forEach
                        val key = line.substring(0, cut).trim()
                        val parts = line.substring(cut + 1).split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        if (parts.isEmpty()) return@forEach
                        skeletons[key] = (skeletons[key] ?: emptyList()) + listOf(parts)
                    }
                }
            }
        }
        val set = skeletons[genre]?.takeIf { it.isNotEmpty() }
            ?: skeletons["any"]?.takeIf { it.isNotEmpty() }
            ?: return listOf("chain", "scene", "verdict", "close")
        return set[Math.floorMod(seed, set.size)]
    }

    var ctxRef: Context? = null

    fun fill(template: String, values: Map<String, String>): String {
        var text = template
        for ((key, value) in values) {
            text = text.replace("{$key}", value)
        }
        return text
    }
}
