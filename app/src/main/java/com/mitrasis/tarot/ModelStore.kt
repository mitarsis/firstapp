package com.mitrasis.tarot

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Модель не влезает в APK (473 МБ), поэтому качается при первом запуске
 * из GitHub Release "model" и лежит в filesDir/gliner2.
 */
object ModelStore {

    private const val BASE = "https://github.com/mitarsis/firstapp/releases/download/model/"

    /** имя файла -> размер в байтах (для прогресса) */
    private val FILES = linkedMapOf(
        "encoder_w4.onnx" to 922_770L,
        "encoder_w4.onnx.data" to 464_858_112L,
        "classifier_fp16.onnx" to 1_303L,
        "classifier_fp16.onnx.data" to 7_096_320L
    )

    val totalBytes: Long = FILES.values.sum()

    private const val BIG_FILE = "encoder_w4.onnx.data"
    private const val MIN_BIG_FILE_BYTES = 100_000_000L

    fun dir(ctx: Context): File = File(ctx.filesDir, "gliner2").apply { if (!exists()) mkdirs() }

    fun encoderPath(ctx: Context): File = File(dir(ctx), "encoder_w4.onnx")
    fun classifierPath(ctx: Context): File = File(dir(ctx), "classifier_fp16.onnx")

    /**
     * Проверяем по диску, а не по ожиданию: файл есть и он не обрывок.
     * Точное совпадение размеров было плохой идеей — перезаливка релиза
     * ломала проверку, и модель качалась заново.
     */
    fun isReady(ctx: Context): Boolean {
        val names = FILES.keys
        if (!names.all { File(dir(ctx), it).let { f -> f.isFile && f.length() > 0 } }) return false
        val weights = File(dir(ctx), BIG_FILE)
        return weights.isFile && weights.length() > MIN_BIG_FILE_BYTES
    }

    fun installedBytes(ctx: Context): Long = FILES.keys.sumOf { name ->
        File(dir(ctx), name).let { if (it.exists()) it.length() else 0L }
    }

    fun deleteAll(ctx: Context) {
        dir(ctx).listFiles()?.forEach { it.delete() }
    }

    /**
     * Убирает всё, чего нет в списке нужных файлов: недокачанные .part,
     * файлы старых версий, дубликаты после перезаливок релиза.
     */
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

    /** Что реально лежит на диске — для диагностики. */
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

    /**
     * Качает недостающие файлы. Идемпотентно: уже скачанное пропускается,
     * недокачанное лежит как .part и начинается заново.
     */
    suspend fun download(ctx: Context, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        var done = 0L
        for ((name, size) in FILES) {
            val target = File(dir(ctx), name)
            if (target.isFile && target.length() > 0) {
                done += size
                onProgress(done, totalBytes)
                continue
            }
            val tmp = File(dir(ctx), "$name.part")
            if (tmp.exists()) tmp.delete()

            val connection = URL(BASE + name).openConnection() as HttpURLConnection
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
