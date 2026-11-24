package com.mitrasis.tarot

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Звук генерируется на месте: никаких файлов в assets.
 *
 * Три коротких сигнала — удар при вытяжке, шорох при броске облака и щелчок
 * смены режима. Всё синтезируется синусами с затуханием, буферы считаются
 * один раз и потом проигрываются.
 */
object SoundEngine {

    private const val RATE = 44100
    private const val ENABLED_KEY = "sound_enabled"

    private var enabled = true
    private var chime: ShortArray? = null
    private var whoosh: ShortArray? = null
    private var click: ShortArray? = null

    fun init(context: Context) {
        enabled = context.getSharedPreferences("tarot", Context.MODE_PRIVATE)
            .getBoolean(ENABLED_KEY, true)
        if (chime == null) {
            chime = tone(listOf(392.0 to 0.55, 587.3 to 0.35), 0.45)
            whoosh = noise(0.28)
            click = tone(listOf(880.0 to 0.5), 0.09)
        }
    }

    fun isEnabled(): Boolean = enabled

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        context.getSharedPreferences("tarot", Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED_KEY, value).apply()
        if (value) tick(context)
    }

    fun flip(context: Context): Boolean {
        setEnabled(context, !enabled)
        return enabled
    }

    fun chime() = play(chime)

    fun swipe() = play(whoosh)

    fun tick(context: Context) = play(click)

    private fun play(buffer: ShortArray?) {
        if (!enabled || buffer == null) return
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(buffer, 0, buffer.size)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                track.play()
            } else {
                @Suppress("DEPRECATION")
                track.play()
            }
            // освобождаем после проигрывания
            Thread {
                Thread.sleep((buffer.size * 1000L / RATE) + 120L)
                runCatching { track.release() }
            }.start()
        } catch (t: Throwable) {
            // звук — украшение, падать из-за него нельзя
        }
    }

    /** Аккорд из синусов с экспоненциальным затуханием. */
    private fun tone(partials: List<Pair<Double, Double>>, seconds: Double): ShortArray {
        val count = (RATE * seconds).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / RATE
            val envelope = exp(-4.5 * t / seconds)
            var value = 0.0
            for ((freq, weight) in partials) {
                value += sin(2 * PI * freq * t) * weight
            }
            out[i] = (value * envelope * 8000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /** Мягкий шорох: отфильтрованный шум с затуханием. */
    private fun noise(seconds: Double): ShortArray {
        val count = (RATE * seconds).toInt()
        val out = ShortArray(count)
        var previous = 0.0
        var state = 12345
        for (i in 0 until count) {
            state = state * 1103515245 + 12345
            val white = ((state ushr 16) and 0xFF) / 255.0 * 2 - 1
            previous = previous * 0.92 + white * 0.08
            val t = i.toDouble() / RATE
            val envelope = sin(PI * (t / seconds)) * exp(-2.0 * t / seconds)
            out[i] = (previous * envelope * 6000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    fun vibrate(context: Context, ms: Long = 18) {
        runCatching {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(ms)
            }
        }
    }
}
