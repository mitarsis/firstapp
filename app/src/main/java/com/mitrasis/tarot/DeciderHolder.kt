package com.mitrasis.tarot

import android.content.Context

/**
 * Держит ONNX-сессию в памяти между вопросами: загрузка 465 МБ весов
 * занимает секунды, и делать её на каждый вопрос — мазохизм.
 * Выгружается явно: кнопкой или при закрытии активити.
 */
object DeciderHolder {

    private var instance: GlinerDecider? = null

    fun get(context: Context): GlinerDecider {
        val app = context.applicationContext
        return instance ?: GlinerDecider(app).also { instance = it }
    }

    fun isLoaded(): Boolean = instance != null

    fun close() {
        instance?.close()
        instance = null
    }
}
