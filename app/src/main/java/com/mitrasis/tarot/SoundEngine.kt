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
    private var flipTrack: AudioTrack? = null
    private var pool: SoundPool? = null
    private var drawId = 0
    private val clinks = Array(4) { ArrayList<AudioTrack>() }
    private const val BLOCK = 256
    @Volatile private var motionSpeed = 0f
    @Volatile private var rendererRunning = false
    private var renderer: Thread? = null
    private val grains = ArrayList<Grain>()
    private var grainSeed = 777
    private var masterPhase = 0.0

    private class Grain(val freq: Double, var phase: Double, var age: Double, val decay: Double, val amp: Double)

    private fun grainRandom(): Double {
        grainSeed = grainSeed * 1103515245 + 12345
        return ((grainSeed ushr 16) and 0x7FFF) / 32767.0
    }

    /** Один блок звука: искры рождаются тем чаще, чем быстрее движется облако. */
    private fun render(buffer: ShortArray) {
        val speed = motionSpeed
        val energy = (speed / 900f).coerceIn(0f, 1f)
        val spawnChance = 0.02f + 0.55f * energy
        for (i in buffer.indices) {
            if (grainRandom() < spawnChance / BLOCK.toFloat() * 8f) {
                if (grains.size < 26) {
                    val freq = 700.0 + grainRandom() * 2600.0
                    grains.add(
                        Grain(
                            freq = freq,
                            phase = 0.0,
                            age = 0.0,
                            decay = 0.06 + grainRandom() * 0.22,
                            amp = 0.06 + grainRandom() * 0.10
                        )
                    )
                }
            }
            if (energy < 0.01f) { buffer[i] = 0; continue }
            var value = 0.0
            for (g in grains) {
                val envelope = exp(-6.0 * g.age / g.decay)
                value += sin(g.phase) * envelope * g.amp
                value += sin(g.phase * 2.01) * envelope * g.amp * 0.25
                g.phase += 2 * PI * g.freq / RATE
                g.age += 1.0 / RATE
            }
            grains.removeAll { it.age > it.decay * 1.6 }
            // низкий воздух, чтобы не было пустоты между искрами
            masterPhase += 2 * PI * 55.0 / RATE
            value += sin(masterPhase) * 0.02 * energy
            val gain = 0.75 * energy
            buffer[i] = (value * gain * 9000).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    fun init(context: Context) {
        enabled = context.getSharedPreferences("tarot", Context.MODE_PRIVATE)
            .getBoolean(ENABLED_KEY, true)
        if (initialised) return
        initialised = true
        chimeTrack = prepare(portal())
        whooshTrack = prepare(noise(0.30))
        clickTrack = prepare(tone(listOf(880.0 to 0.5), 0.08))
        layerTrack = prepare(tone(listOf(523.25 to 0.5, 784.0 to 0.28), 0.5))
        flipTrack = prepare(riffle())
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
        // девять колокольчиков: тег, проходящий у зрителя, бьёт своим тоном
        // у каждого угла свой регистр: низкий, средний, ещё ниже, высокий
        val bases = doubleArrayOf(288.0, 432.0, 180.0, 640.0)
        for (timbre in 0 until 4) {
            for (step in 0 until 9) {
                val freq = bases[timbre] * Math.pow(1.0 + step * 0.11, 1.0)
                clinks[timbre].add(prepare(bell(freq, timbre)) ?: continue)
            }
        }

    }

    fun isEnabled(): Boolean = enabled

    fun flip(context: Context): Boolean {
        enabled = !enabled
        context.getSharedPreferences("tarot", Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED_KEY, enabled).apply()
        return enabled
    }

    fun chime() = fire(chimeTrack)

    fun swipe() = fire(whooshTrack)

    fun tick() {
        if (!enabled) return
        fire(clickTrack)
    }

    fun flip() = fire(flipTrack)

    /** Касание: тег прошёл у передней границы сферы. */
    fun clink(index: Int, speed: Float, sector: Int = 0) {
        if (!enabled) return
        val bank = clinks[Math.floorMod(sector, clinks.size)]
        if (bank.isEmpty()) return
        val track = bank[Math.floorMod(index, bank.size)]
        val gain = (0.22f + 0.70f * (speed / 1600f).coerceIn(0f, 1f))
        runCatching {
            track.stop()
            track.reloadStaticData()
            track.setVolume(gain)
            track.play()
        }
    }

    /**
     * Кибермистический блип: две расстроенные синусоиды дают биения,
     * кольцевая модуляция — стеклянный призвук, лёгкий биткруш — цифру.
     */
    private fun bell(freq: Double, timbre: Int): ShortArray {
        val seconds = 0.34
        val count = (RATE * seconds).toInt()
        val out = ShortArray(count)
        var a = 0.0
        var b = 0.0
        var mod = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / RATE
            a += 2 * PI * freq / RATE
            b += 2 * PI * freq * 1.055 / RATE
            mod += 2 * PI * 4.5 / RATE
            val beating = (sin(a) + sin(b)) * 0.5
            // у каждого угла свой характер
            val shimmer = when (timbre) {
                0 -> sin(a * 3.01) * 0.14                 // верх-лево: стекло
                1 -> sin(a * 2.02) * 0.22                 // верх-право: металл
                2 -> sin(a * 0.5) * 0.30                  // низ-лево: суб
                else -> (sin(a * 4.03) + sin(a * 5.01)) * 0.10  // низ-право: воздух
            }
            val ring = beating * (0.75 + 0.25 * sin(mod))
            val envelope = exp(-7.5 * t) * (1.0 - exp(-700.0 * t))
            val sub = sin(2 * PI * (freq / 4) * t) * exp(-6.0 * t) * 0.22
            val raw = (ring + shimmer) * envelope + sub
            // лёгкий биткруш, чтобы звук остался «цифровым»
            val crushed = Math.round(raw * 24.0) / 24.0
            out[i] = (crushed * 11000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /** Живой звук движения: плотность и яркость зависят от скорости вращения. */
    fun motion(speed: Float) {
        motionSpeed = speed
    }

    /** Тишина: при уходе в фон, выключении звука, остановке облака. */
    fun silence() {
        motionSpeed = 0f
        stopRenderer()
    }

    private fun startRenderer() {
        if (rendererRunning) return
        rendererRunning = true
        renderer = Thread {
            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(BLOCK * 2 * 4)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            }.getOrNull()

            if (track == null) {
                rendererRunning = false
                return@Thread
            }
            track.play()
            val buffer = ShortArray(BLOCK)
            while (rendererRunning) {
                render(buffer)
                runCatching { track.write(buffer, 0, buffer.size) }
            }
            runCatching { track.stop(); track.release() }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun stopRenderer() {
        rendererRunning = false
        renderer?.join(300)
        renderer = null
        grains.clear()
    }

    fun release() {
        stopRenderer()
        runCatching {
            listOf(chimeTrack, whooshTrack, clickTrack, layerTrack, flipTrack).forEach { it?.release() }
            clinks.forEach { bank -> bank.forEach { runCatching { it.release() } }; bank.clear() }
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


}
