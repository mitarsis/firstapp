package com.mitrasis.tarot

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Модель эмбеддингов качается при первом запуске и лежит в filesDir/gliner2.
 * e5-small в int8 — около 120 МБ, это вчетверо меньше прежней сборки Gliner.
 */
object ModelStore {

    private const val BASE = "https://huggingface.co/Xenova/multilingual-e5-small/resolve/main/"

    private val FILES = linkedMapOf(
        "model_e5_int8.onnx" to "onnx/model_int8.onnx"
    )

    private const val MIN_MODEL_BYTES = 50_000_000L

    /** Оценка для прогресса: реальный размер ~120 МБ. */
    val totalBytes: Long = 120_000_000L

    fun dir(ctx: Context): File = File(ctx.filesDir, "gliner2").apply { if (!exists()) mkdirs() }

    fun encoderPath(ctx: Context): File = File(dir(ctx), "model_e5_int8.onnx")

    fun isReady(ctx: Context): Boolean {
        val file = encoderPath(ctx)
        return file.isFile && file.length() > MIN_MODEL_BYTES
    }

    fun installedBytes(ctx: Context): Long = FILES.keys.sumOf { name ->
        File(dir(ctx), name).let { if (it.exists()) it.length() else 0L }
    }

    fun deleteAll(ctx: Context) {
        dir(ctx).listFiles()?.forEach { it.delete() }
    }

    /** Убирает всё, чего нет в списке нужных файлов: .part, остатки прошлых схем. */
    fun cleanup(ctx: Context): List<String> {
        val keep = FILES.keys
        val removed = mutableListOf<String>()
        dir(ctx).listFiles()?.forEach { file ->
            if (file.name !in keep) {
                if (file.delete()) removed.add(file.name)
            }
        }
        return removed
    }

    fun inventory(ctx: Context): String {
        val files = dir(ctx).listFiles()?.sortedBy { it.name } ?: return "папки нет"
        if (files.isEmpty()) return "папка пуста"
        return files.joinToString("\n") { file ->
            String.format(java.util.Locale.ROOT, "%.1f МБ  %s", file.length() / 1_000_000.0, file.name)
        } + String.format(
            java.util.Locale.ROOT,
            "\nитого на диске: %.1f МБ",
            files.sumOf { it.length() } / 1_000_000.0
        )
    }

    suspend fun download(ctx: Context, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        var done = 0L
        for ((name, remote) in FILES) {
            val target = File(dir(ctx), name)
            if (target.isFile && target.length() > MIN_MODEL_BYTES) {
                done += target.length()
                onProgress(done, target.length())
                continue
            }
            val tmp = File(dir(ctx), "$name.part")
            if (tmp.exists()) tmp.delete()

            val connection = URL(BASE + remote).openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 120_000
            connection.instanceFollowRedirects = true
            try {
                connection.inputStream.use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(1 shl 16)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            onProgress(done, totalBytes)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
    }
}
