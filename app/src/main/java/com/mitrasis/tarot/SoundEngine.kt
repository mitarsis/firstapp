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
    private val clinkTracks = arrayOfNulls<AudioTrack>(4)
    private val pcmCache = HashMap<Int, ShortArray>()
    private val melodyStep = IntArray(3)
    private var lastClinkAt = 0L

    /** Дорийский лад от ля: A B C D E F♯ G по двум октавам. */
    private val PENTA = doubleArrayOf(
        1.0, 1.122, 1.189, 1.335, 1.498, 1.682, 1.888,
        2.0, 2.245, 2.378, 2.67, 2.997
    )
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
        layerTrack = prepare(tone(listOf(523.25 to 0.5, 659.25 to 0.30), 0.5))
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
        // дорийский лад от ля: A B C D E F# G, затем октава и дальше.
        // Полутонов меньше, чем в обычном миноре, поэтому светлее и «стариннее».
        // по одной дорожке на угол: звук рендерится под каждое касание,
        // поэтому нота может быть любой — без банка из 12 записей
        val capacity = (RATE * 0.5).toInt()
        for (sector in 0 until 4) {
            clinkTracks[sector] = runCatching {
                AudioTrack.Builder()
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
                    .setBufferSizeInBytes(capacity * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            }.getOrNull()
        }
    }

    fun isEnabled(): Boolean = enabled

    /** Прослушка: пять ударов подряд по возрастанию, с вибрацией на каждом. */
    fun demo() {
        if (!enabled) return
        Thread {
            for (step in 0 until 6) {
                clink(step % 3, 700f, step % 4, 0.9f, 0f)
                Thread.sleep(340)
            }
            Thread.sleep(300)
        }.start()
    }

    /** Голый синус — для сравнения «до». */
    private fun simpleTone(freq: Double, seconds: Double): ShortArray {
        val count = (RATE * seconds).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / RATE
            out[i] = (sin(2 * PI * freq * t) * exp(-9.0 * t) * 11000)
                .toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

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
    fun clink(index: Int, speed: Float, sector: Int = 0, volume: Float = 1f, pan: Float = 0f) {
        if (!enabled) return
        // на быстром вращении касания идут чаще, чем ухо терпит:
        // держим минимальный интервал, иначе звучит как перемотка
        val now = System.currentTimeMillis()
        val minGap = if (speed > 900f) 130L else 85L
        if (now - lastClinkAt < minGap) return
        lastClinkAt = now
        val safeSector = Math.floorMod(sector, 4)
        val voice = Math.floorMod(index, 3)
        val track = clinkTracks[safeSector] ?: return

        // мелодия: каждый следующий удар этого голоса идёт на ступень выше по ладу
        val step = melodyStep[voice]
        melodyStep[voice] = (step + 1) % PENTA.size
        val register = doubleArrayOf(1.0, 1.5, 2.0)[voice]
        val freq = 330.0 * PENTA[step] * register

        val key = (voice * 100) + step
        val samples = pcmCache.getOrPut(key) { bell(freq, safeSector) }

        val gain = (0.45f + 0.55f * (speed / 1600f).coerceIn(0f, 1f)) * volume.coerceIn(0f, 1f)
        haptic((5 + 9f * (speed / 1600f).coerceIn(0f, 1f)).toLong())

        runCatching { if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop() }
        runCatching { track.reloadStaticData() }
        runCatching { track.write(samples, 0, samples.size) }
        val place = pan.coerceIn(-1f, 1f)
        val left = ((1f - place) * 0.5f + 0.15f).coerceIn(0f, 1f) * gain
        val right = ((1f + place) * 0.5f + 0.15f).coerceIn(0f, 1f) * gain
        runCatching { track.setStereoVolume(left, right) }
        runCatching { track.play() }
    }

    /**
     * Объёмный удар: негармонические призвуки как у настоящего колокола,
     * шумовая атака, лёгкое расщепление и короткое эхо — чтобы не было плоско.
     */
    private fun bell(freq: Double, timbre: Int): ShortArray {
        val seconds = 0.42
        val count = (RATE * seconds).toInt()
        val dry = DoubleArray(count)
        // партиалы колокола: не кратные, поэтому звук «металлический», а не писк
        val partials = doubleArrayOf(1.0, 2.0, 2.42, 3.01, 4.16, 5.43)
        val weights = doubleArrayOf(0.40, 0.48, 0.44, 0.36, 0.26, 0.18)
        val octave = when (timbre) {
            0 -> 1.0
            1 -> 2.0
            2 -> 0.5
            else -> 1.5
        }
        var state = 2024 + freq.toInt()
        for (i in 0 until count) {
            val t = i.toDouble() / RATE
            var value = 0.0
            for (k in partials.indices) {
                val f = freq * octave * partials[k]
                if (f > 12000) continue
                value += sin(2 * PI * f * t + k) * weights[k] * exp(-(2.4 + k * 1.6) * t)
            }
            // расщепление: второй голос чуть выше — стеклянное биение
            value += sin(2 * PI * freq * octave * 1.003 * t) * 0.25 * exp(-7.0 * t)
            // шумовая атака молоточка
            state = state * 1103515245 + 12345
            val white = ((state ushr 16) and 0xFF) / 255.0 * 2 - 1
            value += white * exp(-220.0 * t) * 0.05
            dry[i] = value * (1.0 - exp(-1500.0 * t))
        }
        // короткое эхо: две задержки дают ощущение объёма
        val wet = dry.copyOf()
        applyEcho(wet, (RATE * 0.031).toInt(), 0.30)
        applyEcho(wet, (RATE * 0.067).toInt(), 0.18)
        val out = ShortArray(count)
        var peak = 0.0
        for (v in wet) if (kotlin.math.abs(v) > peak) peak = kotlin.math.abs(v)
        val norm = if (peak > 0.001) 0.85 / peak else 1.0
        for (i in 0 until count) {
            out[i] = (wet[i] * norm * 15000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun applyEcho(buffer: DoubleArray, delay: Int, feedback: Double) {
        if (delay <= 0) return
        for (i in delay until buffer.size) {
            buffer[i] += buffer[i - delay] * feedback
        }
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
            clinkTracks.forEach { runCatching { it?.release() } }
            pcmCache.clear()
            pool?.release()
            pool = null
        }
        initialised = false
    }

    private fun fire(track: AudioTrack?) {
        if (!enabled || track == null) return
        // stop() на свежей дорожке бросает исключение — из-за этого play() не вызывался
        runCatching { if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop() }
        runCatching { track.reloadStaticData() }
        runCatching { track.play() }
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
            // дорийские ступени A–D–E вместо хроматической пары
            p1 += 2 * PI * 220.0 / RATE
            p2 += 2 * PI * 293.66 / RATE
            p3 += 2 * PI * 329.63 / RATE
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
