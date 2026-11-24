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
    private var appContext: Context? = null

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
        appContext = context.applicationContext
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
        // ля-минорная опора: A3, C4, E4, A4 — звучат как один инструмент
        // ля-минорная пентатоника: A C D E G по двум октавам.
        // Угол сферы выбирает октаву, поэтому любые два звука сочетаются.
        val ratios = doubleArrayOf(1.0, 1.2, 1.335, 1.5, 1.78, 2.0, 2.4, 2.67, 3.0)
        val octaves = doubleArrayOf(1.0, 2.0, 0.5, 1.5)
        for (timbre in 0 until 4) {
            for (step in 0 until 9) {
                val freq = 220.0 * ratios[step] * octaves[timbre]
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

    fun layer() {
        if (!enabled) return
        fire(layerTrack)
    }

    fun flip() {
        fire(flipTrack)
        haptic(22)
    }

    /** Лёгкая вибрация: короткий тик, сила зависит от скорости. */
    private fun haptic(ms: Long) {
        if (!enabled) return
        val context = appContext ?: return
        runCatching {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    android.os.VibrationEffect.createOneShot(ms, 90)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(ms)
            }
        }
    }

    /** Касание: тег прошёл у передней границы сферы. */
    fun clink(index: Int, speed: Float, sector: Int = 0) {
        if (!enabled) return
        val bank = clinks[Math.floorMod(sector, clinks.size)]
        if (bank.isEmpty()) return
        val track = bank[Math.floorMod(index, bank.size)]
        val gain = (0.22f + 0.70f * (speed / 1600f).coerceIn(0f, 1f))
        haptic((5 + 9f * (speed / 1600f).coerceIn(0f, 1f)).toLong())
        runCatching {
            track.stop()
            track.reloadStaticData()
            track.setVolume(gain)
            track.play()
        }
    }

    /**
     * Чистый стеклянный удар: короткий, с мягкой атакой, синус плюс призвук
     * строго по гармоникам лада. Без расстройки и модуляции — ноты не спорят.
     */
    private fun bell(freq: Double, timbre: Int): ShortArray {
        val seconds = 0.22
        val count = (RATE * seconds).toInt()
        val out = ShortArray(count)
        val extra = when (timbre) {
            0 -> 3.0     // октава + квинта
            1 -> 2.0     // октава
            2 -> 0.5     // суб
            else -> 4.0  // две октавы
        }
        var phase = 0.0
        var extraPhase = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / RATE
            phase += 2 * PI * freq / RATE
            extraPhase += 2 * PI * freq * extra / RATE
            val envelope = exp(-11.0 * t) * (1.0 - exp(-1500.0 * t))
            val value = (sin(phase) * 0.85 + sin(extraPhase) * 0.12) * envelope
            out[i] = (value * 12000).toInt().coerceIn(-32767, 32767).toShort()
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
    /**
     * Ответ на вопрос: холодный кристаллический удар. Короткий шумовой фронт,
     * затем три расстроенных тона в малой секунде — тревожно, без «вздоха».
     */
    private fun portal(): ShortArray {
        val seconds = 1.1
        val count = (RATE * seconds).toInt()
        val out = ShortArray(count)
        var p1 = 0.0
        var p2 = 0.0
        var p3 = 0.0
        var state = 4242
        for (i in 0 until count) {
            val t = i.toDouble() / RATE
            p1 += 2 * PI * 196.0 / RATE
            p2 += 2 * PI * 207.65 / RATE
            p3 += 2 * PI * 392.0 / RATE
            state = state * 1103515245 + 12345
            val white = ((state ushr 16) and 0xFF) / 255.0 * 2 - 1
            val body = sin(p1) * 0.45 + sin(p2) * 0.35 + sin(p3) * 0.18
            val envelope = exp(-3.4 * t)
            val strike = white * exp(-120.0 * t) * 0.6
            val sub = sin(2 * PI * 49.0 * t) * exp(-8.0 * t) * 0.25
            out[i] = ((body * envelope + strike + sub) * 0.75 * 9000)
                .toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /** Шелест переворота: шум с быстрым спадом и низким «хлопком» в конце. */
    private fun riffle(): ShortArray {
        val seconds = 0.34
        val count = (RATE * seconds).toInt()
        val out = ShortArray(count)
        var state = 5150
        var band = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / RATE
            state = state * 1103515245 + 12345
            val white = ((state ushr 16) and 0xFF) / 255.0 * 2 - 1
            band = band * 0.55 + white * 0.45
            val envelope = exp(-9.0 * t) * (1.0 - exp(-400.0 * t))
            val thump = sin(2 * PI * 90.0 * t) * exp(-30.0 * t) * 0.5
            out[i] = ((band * 0.7 * envelope + thump) * 8000)
                .toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

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
