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

    fun dir(ctx: Context): File = File(ctx.filesDir, "gliner2").apply { if (!exists()) mkdirs() }

    fun encoderPath(ctx: Context): File = File(dir(ctx), "encoder_w4.onnx")
    fun classifierPath(ctx: Context): File = File(dir(ctx), "classifier_fp16.onnx")

    fun isReady(ctx: Context): Boolean = FILES.keys.all { name ->
        val f = File(dir(ctx), name)
        f.exists() && f.length() > 0
    }

    fun installedBytes(ctx: Context): Long = FILES.keys.sumOf { name ->
        File(dir(ctx), name).let { if (it.exists()) it.length() else 0L }
    }

    fun deleteAll(ctx: Context) {
        dir(ctx).listFiles()?.forEach { it.delete() }
    }

    /**
     * Качает недостающие файлы. Идемпотентно: уже скачанное пропускается,
     * недокачанное лежит как .part и начинается заново.
     */
    suspend fun download(ctx: Context, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        var done = 0L
        for ((name, size) in FILES) {
            val target = File(dir(ctx), name)
            if (target.exists() && target.length() == size) {
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
