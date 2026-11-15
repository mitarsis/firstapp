package com.mitrasis.tarot

import android.app.Application
import android.util.Log
import java.io.File

/**
 * Ловит необработанные исключения и складывает стектрейс в filesDir/crash.log.
 * При следующем запуске MainActivity покажет его на экране — иначе причины
 * падения на телефоне без adb просто не увидеть.
 */
class TarotAppClass : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val trace = Log.getStackTraceString(throwable)
                val header = buildString {
                    append("время: ").append(System.currentTimeMillis()).append('\n')
                    append("поток: ").append(thread.name).append('\n')
                    append("версия: ").append(packageManager.getPackageInfo(packageName, 0).versionName).append('\n')
                    append("heap: ").append(Runtime.getRuntime().maxMemory() / 1_000_000).append(" МБ\n\n")
                    append(trace)
                }
                File(filesDir, CRASH_FILE).writeText(header)
            } catch (ignored: Throwable) {
                // если и это не вышло — молчим, тут уже нечего спасать
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        const val CRASH_FILE = "crash.log"
    }
}
