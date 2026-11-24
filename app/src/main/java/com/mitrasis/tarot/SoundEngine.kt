package com.mitrasis.tarot

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Звук синтезируется в коде — никаких файлов.
 *
 * Треки создаются один раз при старте и лежат готовыми: иначе на каждый
 * сигнал уходит время на создание AudioTrack, и звук отстаёт от жеста.
 */
object SoundEngine {

    private const val RATE = 44100
    private const val ENABLED_KEY = "sound_enabled"

    private var enabled = true
    private var initialised = false

    private var chimeTrack: AudioTrack? = null
    private var whooshTrack: AudioTrack? = null
    private var clickTrack: AudioTrack? = null
    private var ambientTrack: AudioTrack? = null

    fun init(context: Context) {
        enabled = context.getSharedPreferences("tarot", Context.MODE_PRIVATE)
            .getBoolean(ENABLED_KEY, true)
        if (initialised) return
        initialised = true
        chimeTrack = prepare(tone(listOf(392.0 to 0.55, 587.3 to 0.35), 0.45))
        whooshTrack = prepare(noise(0.30))
        clickTrack = prepare(tone(listOf(880.0 to 0.5), 0.08))
        ambientTrack = prepare(ambient(4.0))?.apply { setVolume(0.22f) }
        if (enabled) startAmbient()
    }

    fun isEnabled(): Boolean = enabled

    fun flip(context: Context): Boolean {
        enabled = !enabled
        context.getSharedPreferences("tarot", Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED_KEY, enabled).apply()
        if (enabled) startAmbient() else stopAmbient()
        return enabled
    }

    fun chime() = fire(chimeTrack)

    fun swipe() = fire(whooshTrack)

    fun tick() = fire(clickTrack)

    fun startAmbient() {
        if (!enabled) return
        runCatching {
            val track = ambientTrack ?: return
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
                track.reloadStaticData()
                track.setLoopPoints(0, ambientLength, -1)
                track.play()
            }
        }
    }

    fun stopAmbient() {
        runCatching { ambientTrack?.pause() }
    }

    fun release() {
        runCatching {
            listOf(chimeTrack, whooshTrack, clickTrack, ambientTrack).forEach { it?.release() }
        }
        initialised = false
    }

    private var ambientLength = 0

    private fun fire(track: AudioTrack?) {
        if (!enabled || track == null) return
        runCatching {
            track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    private fun prepare(samples: ShortArray): AudioTrack? = runCatching {
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
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size)
        track
    }.getOrNull()

    /** Аккорд из синусов с экспоненциальным затуханием. */
    private fun tone(partials: List<Pair<Double, Double>>, seconds: Double): ShortArray {
        val count = (RATE * seconds).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / RATE
            val envelope = exp(-4.5 * t / seconds)
            var value = 0.0
            for ((freq, weight) in partials) value += sin(2 * PI * freq * t) * weight
            out[i] = (value * envelope * 8000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /** Мягкий шорох: шум, срезанный фильтром, с плавным входом и выходом. */
    private fun noise(seconds: Double): ShortArray {
        val count = (RATE * seconds).toInt()
        val out = ShortArray(count)
        var previous = 0.0
        var state = 12345
        for (i in 0 until count) {
            state = state * 1103515245 + 12345
            val white = ((state ushr 16) and 0xFF) / 255.0 * 2 - 1
            previous = previous * 0.90 + white * 0.10
            val t = i.toDouble() / RATE
            val envelope = sin(PI * (t / seconds)) * exp(-1.6 * t / seconds)
            out[i] = (previous * envelope * 7000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /**
     * Фон: тихая цифровая «среда» — редкие щелчки, шорох плёнки и низкий гул.
     * Зацикливается бесшовно, поэтому обрезаем по целому числу периодов гула.
     */
    private fun ambient(seconds: Double): ShortArray {
        val count = (RATE * seconds).toInt()
        ambientLength = count
        val out = ShortArray(count)
        var state = 987654321
        var filtered = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / RATE
            state = state * 1103515245 + 12345
            val white = ((state ushr 16) and 0xFF) / 255.0 * 2 - 1
            filtered = filtered * 0.995 + white * 0.005
            // низкий гул 55 Гц + пятая, чтобы не было «писка»
            val hum = sin(2 * PI * 55.0 * t) * 0.05 + sin(2 * PI * 82.5 * t) * 0.02
            // редкие цифровые щелчки
            var glitch = 0.0
            if (state % 40000 < 60) {
                glitch = (1.0 - (state % 40000) / 60.0) * 0.35
            }
            val value = hum + filtered * 2.2 + glitch * white
            out[i] = (value * 3600).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }
}
