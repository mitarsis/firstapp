package com.mitrasis.tarot

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
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
    private var layerTrack: AudioTrack? = null
    private var spinTrack: AudioTrack? = null
    private var spinLength = 0
    private var spinPlaying = false
    private var pool: SoundPool? = null
    private var drawId = 0
    private var layerId = 0
    private var modeId = 0

    fun init(context: Context) {
        enabled = context.getSharedPreferences("tarot", Context.MODE_PRIVATE)
            .getBoolean(ENABLED_KEY, true)
        if (initialised) return
        initialised = true
        chimeTrack = prepare(tone(listOf(392.0 to 0.55, 587.3 to 0.35), 0.45))
        whooshTrack = prepare(noise(0.30))
        clickTrack = prepare(tone(listOf(880.0 to 0.5), 0.08))
        layerTrack = prepare(tone(listOf(523.25 to 0.5, 784.0 to 0.28), 0.5))
        // звуки, сгенерированные earshot и ужатые до моно 22 кГц
        pool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        drawId = runCatching { pool!!.load(context.assets.openFd("sfx/draw.wav"), 1) }.getOrDefault(0)
        layerId = runCatching { pool!!.load(context.assets.openFd("sfx/layer.wav"), 1) }.getOrDefault(0)
        modeId = runCatching { pool!!.load(context.assets.openFd("sfx/mode.wav"), 1) }.getOrDefault(0)
        spinTrack = prepare(bells(2.4)).also { spinLength = (RATE * 2.4).toInt() }

    }

    fun isEnabled(): Boolean = enabled

    fun flip(context: Context): Boolean {
        enabled = !enabled
        context.getSharedPreferences("tarot", Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED_KEY, enabled).apply()
        return enabled
    }

    fun chime() {
        if (!enabled) return
        if (drawId != 0) pool?.play(drawId, 0.9f, 0.9f, 1, 0, 1f) else fire(chimeTrack)
    }

    fun swipe() = fire(whooshTrack)

    fun tick() {
        if (!enabled) return
        if (modeId != 0) pool?.play(modeId, 0.8f, 0.8f, 1, 0, 1f) else fire(clickTrack)
    }

    fun layer() {
        if (!enabled) return
        if (layerId != 0) pool?.play(layerId, 1f, 1f, 1, 0, 1f) else fire(layerTrack)
    }

    /** Перезвон, пока облако крутится: включаем на раскрутке, гасим на остановке. */
    fun spin(active: Boolean) {
        if (active == spinPlaying) return
        spinPlaying = active
        val track = spinTrack ?: return
        if (!enabled) return
        runCatching {
            if (active) {
                track.stop()
                track.reloadStaticData()
                track.setLoopPoints(0, spinLength, -1)
                track.setVolume(0.5f)
                track.play()
            } else {
                track.pause()
            }
        }
    }

    fun release() {
        runCatching {
            listOf(chimeTrack, whooshTrack, clickTrack, layerTrack, spinTrack).forEach { it?.release() }
            pool?.release()
            pool = null
        }
        initialised = false
    }

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
     * Перезвон без мелодии: много коротких высоких ударов со случайной высотой
     * плюс воздушный шум. Петля бесшовная — всё стихает до конца буфера.
     */
    private fun bells(seconds: Double): ShortArray {
        val count = (RATE * seconds).toInt()
        val out = DoubleArray(count)
        var state = 24680
        fun rnd(): Double {
            state = state * 1103515245 + 12345
            return ((state ushr 16) and 0x7FFF) / 32767.0
        }

        // сорок коротких искр: без гаммы, только высокие случайные частоты
        repeat(40) {
            val start = rnd() * (seconds - 0.4)
            val freq = 1400.0 + rnd() * 2400.0
            val decay = 0.10 + rnd() * 0.16
            val amp = 0.10 + rnd() * 0.12
            val startIndex = (start * RATE).toInt()
            val length = (decay * RATE).toInt()
            for (i in 0 until length) {
                val index = startIndex + i
                if (index >= count) break
                val t = i.toDouble() / RATE
                val envelope = exp(-5.5 * t / decay) * (1.0 - exp(-400.0 * t))
                out[index] += sin(2 * PI * freq * t) * envelope * amp
                out[index] += sin(2 * PI * freq * 1.51 * t) * envelope * amp * 0.3
            }
        }

        // воздух: очень тихий шум, срезанный в высокую полосу
        var prev = 0.0
        for (i in 0 until count) {
            state = state * 1103515245 + 12345
            val white = ((state ushr 16) and 0xFF) / 255.0 * 2 - 1
            prev = prev * 0.65 + white * 0.35
            out[i] += prev * 0.012
        }

        // края к нулю, петля без щелчка
        val fade = (RATE * 0.08).toInt()
        for (i in 0 until fade) {
            val k = i.toDouble() / fade
            out[i] *= k
            out[count - 1 - i] *= k
        }

        val result = ShortArray(count)
        for (i in 0 until count) {
            result[i] = (out[i] * 11000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return result
    }
}
