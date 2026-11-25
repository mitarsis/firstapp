package com.mitrasis.tarot

import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val TABLE = Color(0xFF0B090D)
private val FOIL = Color(0xFFC9A26B)
private val ASH = Color(0xFFB9AE9A)

class MainActivity : ComponentActivity() {

    override fun onStop() {
        SoundEngine.silence()
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations) {
            DeciderHolder.close()
            SoundEngine.release()
        }
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // полный экран: контент под системными панелями, а сами панели скрыты
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        SoundEngine.init(this)
        setContent {
            TarotTheme {
                var modeLayers by remember { mutableStateOf(3) }
                Surface(modifier = Modifier.fillMaxSize(), color = TABLE) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        TableBackdrop(modeLayers)
                        StarField()
                        TarotApp(layers = modeLayers, onLayersChange = { modeLayers = it })
                    }
                }
            }
        }
    }
}

/** Стол, а не фон приложения: тёмная поверхность с редкими искрами. */
private data class Spark(val x: Float, val y: Float, val radius: Float, val alpha: Float)

@Composable
private fun TableBackdrop(mode: Int = 3) {
    val sparks = remember {
        val random = Random(77)
        List(90) {
            Spark(
                x = random.nextFloat(),
                y = random.nextFloat(),
                radius = 0.6f + random.nextFloat(),
                alpha = 0.05f + random.nextFloat() * 0.12f
            )
        }
    }
    // медленное вращение и дыхание: подложка живёт, но не отвлекает
    val spell by androidx.compose.animation.core.rememberInfiniteTransition(label = "spell").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(48000, easing = androidx.compose.animation.core.LinearEasing)
        ),
        label = "spell"
    )
    val breath = 0.5f + 0.5f * kotlin.math.sin(spell * kotlin.math.PI.toFloat() / 180f * 3f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        // цветокоррекция по режиму: триангуляция холоднее, домен теплее
        val warm = if (mode == 2) 0.55f else 0f
        val top = androidx.compose.ui.graphics.lerp(Color(0xFF1A1620), Color(0xFF231A16), warm)
        val bottom = androidx.compose.ui.graphics.lerp(TABLE, Color(0xFF17110E), warm)
        drawRect(brush = Brush.radialGradient(
            colors = listOf(top, bottom),
            center = Offset(size.width * 0.5f, size.height * 0.38f),
            radius = size.height * 0.75f
        ))

        val center = Offset(size.width * 0.5f, size.height * 0.38f)

        // вращающееся сияние: два сектора уходят по кругу
        rotate(spell, pivot = center) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF6D3BD8).copy(alpha = 0.10f),
                        Color.Transparent,
                        Color(0xFF1FA3B8).copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center
                ),
                radius = size.height * (0.55f + 0.03f * breath),
                center = center,
                blendMode = androidx.compose.ui.graphics.BlendMode.Screen
            )
        }

        // дышащие кольца
        for (ring in 0 until 3) {
            val phaseBreath = 0.5f + 0.5f * kotlin.math.sin(
                spell * kotlin.math.PI.toFloat() / 180f * (2f + ring) + ring
            )
            drawCircle(
                color = FOIL.copy(alpha = 0.035f + 0.03f * phaseBreath),
                radius = size.height * (0.18f + 0.10f * ring + 0.02f * phaseBreath),
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f)
            )
        }
        sparks.forEach { spark ->
            drawCircle(
                color = FOIL.copy(alpha = spark.alpha),
                radius = spark.radius,
                center = Offset(spark.x * size.width, spark.y * size.height)
            )
        }
    }
}

/** Наклон телефона: -1..1 по осям. Датчик акселерометра, разрешение не нужно. */
@Composable
private fun rememberTilt(): Pair<Float, Float> {
    val context = LocalContext.current
    var tilt by remember { mutableStateOf(0f to 0f) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val manager = context.getSystemService(android.content.Context.SENSOR_SERVICE)
            as? android.hardware.SensorManager
        val sensor = manager?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        var smoothX = 0f
        var smoothY = 0f
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                val rawX = (-event.values[0] / 9.81f).coerceIn(-1f, 1f)
                val rawY = (event.values[1] / 9.81f).coerceIn(-1f, 1f)
                smoothX += (rawX - smoothX) * 0.15f
                smoothY += (rawY - smoothY) * 0.15f
                tilt = smoothX to smoothY
            }

            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) = Unit
        }
        if (manager != null && sensor != null) {
            manager.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { manager?.unregisterListener(listener) }
    }
    return tilt
}

@Composable
fun TarotApp(layers: Int = 3, onLayersChange: (Int) -> Unit = {}) {
    val context = LocalContext.current
    val crashFile = remember { File(context.filesDir, TarotAppClass.CRASH_FILE) }
    var crash by remember { mutableStateOf(crashFile.takeIf { it.exists() }?.readText()) }
    val crashText = crash
    if (crashText != null) {
        CrashScreen(
            trace = crashText,
            onShare = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, crashText)
                }
                context.startActivity(Intent.createChooser(intent, "Отправить"))
            },
            onClear = {
                crashFile.delete()
                crash = null
            }
        )
        return
    }

    val scope = rememberCoroutineScope()
    val vibrator = remember {
        context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
    }
    fun pulsingTap() {
        try {
            if (vibrator == null || !vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(16, 90))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(16)
            }
        } catch (t: Throwable) {
            // вибрация — украшение, из-за неё падать нельзя
        }
    }

    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<CardAnswer?>(null) }
    var ready by remember { mutableStateOf(ModelStore.isReady(context)) }
    var downloading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var note by remember { mutableStateOf("") }
    var showDiag by remember { mutableStateOf(false) }
    var focusLayer by remember { mutableStateOf(-1) }
    var showGallery by remember { mutableStateOf(false) }
    val tilt = rememberTilt()
    var channelsAnimating by remember { mutableStateOf(false) }
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "channels")
    val rotatingChannels by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 6400,
                easing = androidx.compose.animation.core.LinearEasing
            )
        ),
        label = "channelRotation"
    )
    val flip by animateFloatAsState(
        targetValue = if (answer != null) 180f else 0f,
        animationSpec = androidx.compose.animation.core.tween(420),
        label = "flip"
    )
    var diag by remember { mutableStateOf<List<String>>(emptyList()) }

    // переворот карты: шелест в момент, когда карта встаёт
    androidx.compose.runtime.LaunchedEffect(answer != null) {
        if (answer != null) SoundEngine.flip()
    }

    fun draw() {
        val text = question.trim()
        if (text.isEmpty() || busy) return
        note = ""
        busy = true
        focusLayer = -1
        pulsingTap()
        scope.launch {
            try {
                answer = withContext(Dispatchers.Default) {
                    DeciderHolder.get(context).answer(text, layers)
                }
            } catch (o: OutOfMemoryError) {
                note = "не хватило памяти — закрой другие приложения"
            } catch (t: Throwable) {
                note = "не смогла: ${t.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    fun download() {
        if (downloading) return
        downloading = true
        note = ""
        scope.launch {
            try {
                ModelStore.download(context) { done, total ->
                    progress = if (total > 0) done.toFloat() / total else 0f
                }
                ready = ModelStore.isReady(context)
            } catch (t: Throwable) {
                note = "загрузка сорвалась"
            } finally {
                downloading = false
            }
        }
    }

    fun diagnostics() {
        showDiag = true
        diag = emptyList()
        scope.launch {
            val steps = mutableListOf<String>()
            fun say(line: String) {
                steps.add(line)
                diag = steps.toList()
                Log.i("TarotDiag", line)
            }
            try {
                val page = try {
                    android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE).toString()
                } catch (t: Throwable) {
                    "?"
                }
                say("${Build.SUPPORTED_ABIS.joinToString("/")} · ${page} · sdk ${Build.VERSION.SDK_INT}")
                say(if (ModelStore.isReady(context)) "модель на месте" else "модели нет")
                say(ModelStore.inventory(context).replace("\n", " · "))
                if (ModelStore.isReady(context)) {
                    withContext(Dispatchers.Default) {
                        val decider = DeciderHolder.get(context)
                        decider.ensureLoaded()
                        say(decider.describeInputs())
                        val probe = decider.answer("стоит ли мне уйти с работы?", 3)
                        say("пробный ответ: ${probe.card.title} ${(probe.probability * 100).toInt()}%")
                    }
                }
            } catch (t: Throwable) {
                say("ошибка: ${t.javaClass.simpleName}")
            }
        }
    }

    BackHandler(enabled = answer != null || question.isNotEmpty()) {
        answer = null
        question = ""
        note = ""
    }

    if (showGallery) {
        CardGallery(onClose = { showGallery = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.5f))

        // Плавный наклон: значения сглаживаются, поэтому гироскоп не дёргает карту
        val tiltActive = focusLayer < 0
        val smoothY by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (tiltActive) flip + tilt.first * 14f else flip,
            animationSpec = androidx.compose.animation.core.tween(180),
            label = "rotY"
        )
        val breathScale by androidx.compose.animation.core.rememberInfiniteTransition(label = "breath")
            .animateFloat(
                initialValue = 0.992f,
                targetValue = 1.012f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    androidx.compose.animation.core.tween(6200, easing = androidx.compose.animation.core.LinearEasing),
                    androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "breath"
            )
        val smoothX by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (tiltActive) -tilt.second * 14f else 0f,
            animationSpec = androidx.compose.animation.core.tween(180),
            label = "rotX"
        )

        // вспышка в момент, когда карта проходит ребром к зрителю
        val flipGlow = (kotlin.math.sin(flip * Math.PI.toFloat() / 180f)).coerceIn(0f, 1f)
        if (flipGlow > 0.05f) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .aspectRatio(0.62f)
            ) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFF2A93B).copy(alpha = 0.28f * flipGlow),
                            Color.Transparent
                        )
                    )
                )
            }
        }

        // Карта — и есть интерфейс
        Box(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .aspectRatio(0.62f)
                .graphicsLayer {
                    // объём: наклон всего блока вслед за телефоном
                    rotationY = smoothY
                    rotationX = smoothX
                    cameraDistance = 24f * density
                    val zoom = if (focusLayer >= 0) 1.03f else 1f
                    scaleX = zoom * breathScale
                    scaleY = zoom * breathScale
                }
                .pointerInput(answer, ready, busy, question) {
                    detectTapGestures(
                        onTap = {
                            when {
                                !ready -> download()
                                busy -> Unit
                                answer == null -> {
                                    if (question.isBlank()) note = "сначала вопрос" else {
                                        draw()
                                        SoundEngine.chime()
                                    }
                                }
                                else -> {
                                    val count = (answer?.alternatives?.size ?: 0) + 1
                                    focusLayer = if (focusLayer >= count - 1) -1 else focusLayer + 1
                                    SoundEngine.layer()
                                    pulsingTap()
                                }
                            }
                        },
                        onLongPress = {
                            // включаем/выключаем перелив каналов
                            val multiple = (answer?.alternatives?.isNotEmpty() == true)
                            if (multiple && focusLayer < 0) {
                                channelsAnimating = !channelsAnimating
                                pulsingTap()
                            }
                        }
                    )
                }
                .pointerInput(answer, ready, busy, question) {
                    val trace = mutableListOf<Offset>()
                    detectDragGestures(
                        onDragStart = { offset -> trace.clear(); trace.add(offset) },
                        onDrag = { change, _ ->
                            val last = trace.lastOrNull()
                            if (last == null || (change.position - last).getDistance() > 12f) {
                                trace.add(change.position)
                            }
                        },
                        onDragEnd = {
                            if (ready && !busy && question.isNotBlank() && looksLikePentagram(trace, size)) {
                                draw()
                            }
                            trace.clear()
                        }
                    )
                }
        ) {
            val shift = if (channelsAnimating && answer?.alternatives?.isNotEmpty() == true && focusLayer < 0)
                rotatingChannels else 0f
            val shown = answer
            if (shown == null) {
                CardBack(
                    question = question,
                    onQuestion = { question = it },
                    ready = ready,
                    downloading = downloading,
                    progress = progress
                )
            } else {
                val trio = listOf(shown.card) + shown.alternatives.map { it.first }
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }
                ) {
                CardStack(
                    context = context,
                    cards = trio,
                    probabilities = listOf(shown.probability) + shown.alternatives.map { it.second },
                    reversed = shown.lines.map { it.reversed },
                    focusIndex = focusLayer,
                    parallax = if (focusLayer >= 0) 0f to 0f else (tilt.first * 0.3f) to (tilt.second * 0.3f),
                    channelShift = shift,
                    agreement = shown.agreement,

                    modifier = Modifier.fillMaxSize()
                )
                }
            }

            // сброс — поверх карты, иначе его перекрывает рисунок
            if (shown != null) {
                Text(
                    text = "↺",
                    style = TextStyle(fontSize = 22.sp),
                    color = FOIL.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clickable {
                            answer = null
                            question = ""
                            note = ""
                            focusLayer = -1
                        }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            !ready && !downloading -> Text(
                "коснись карты — скачаю толкователь",
                style = TextStyle(fontSize = 12.sp, letterSpacing = 1.sp),
                color = ASH
            )
            downloading -> Text(
                "качаю ${(progress * 100).toInt()}%",
                style = TextStyle(fontSize = 12.sp, letterSpacing = 1.sp),
                color = ASH
            )
            busy -> Text(
                if (answer == null) "смотрю…" else "пересматриваю…",
                style = TextStyle(fontSize = 12.sp, letterSpacing = 2.sp),
                color = ASH
            )
            answer != null -> AnswerLabels(answer, focusLayer)
            else -> Text(
                "напиши вопрос на карте и коснись её",
                style = TextStyle(fontSize = 12.sp, letterSpacing = 1.sp),
                color = ASH
            )
        }

        if (note.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(note, style = TextStyle(fontSize = 11.sp), color = Color(0xFFC97A6A))
        }

        if (showDiag) {
            Spacer(Modifier.height(12.dp))
            Text(
                diag.joinToString("\n").ifBlank { "…" },
                style = TextStyle(fontSize = 9.sp, lineHeight = 13.sp),
                color = ASH.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "галерея",
                style = TextStyle(fontSize = 10.sp, letterSpacing = 1.sp),
                color = ASH.copy(alpha = 0.55f),
                modifier = Modifier.clickable { showGallery = true }
            )
            Text(
                text = "триангуляция",
                style = TextStyle(fontSize = 10.sp, letterSpacing = 1.sp),
                color = if (layers == 3) Color(0xFFEDE4D2) else ASH.copy(alpha = 0.4f),
                modifier = Modifier.clickable {
                    if (layers != 3) { onLayersChange(3); answer = null; question = ""; SoundEngine.tick() }
                }
            )
            var soundOn by remember { mutableStateOf(SoundEngine.isEnabled()) }
            Text(
                text = if (soundOn) "звук вкл" else "звук выкл",
                style = TextStyle(fontSize = 10.sp, letterSpacing = 1.sp),
                color = ASH.copy(alpha = if (soundOn) 0.8f else 0.4f),
                modifier = Modifier.clickable { soundOn = SoundEngine.flip(context) }
            )
            Text(
                text = "демо",
                style = TextStyle(fontSize = 10.sp, letterSpacing = 1.sp),
                color = ASH.copy(alpha = 0.55f),
                modifier = Modifier.clickable { SoundEngine.demo() }
            )
            Text(
                text = "домен",
                style = TextStyle(fontSize = 10.sp, letterSpacing = 1.sp),
                color = if (layers == 2) Color(0xFFEDE4D2) else ASH.copy(alpha = 0.4f),
                modifier = Modifier.clickable {
                    if (layers != 2) { onLayersChange(2); answer = null; question = ""; SoundEngine.tick() }
                }
            )
        }
        Spacer(Modifier.height(6.dp))

        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            style = TextStyle(fontSize = 9.sp, letterSpacing = 1.sp),
            color = ASH.copy(alpha = 0.35f),
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        scope.launch {
                            withContext(Dispatchers.Default) { DeciderHolder.close() }
                            answer = null
                        }
                    },
                    onLongPress = { diagnostics() }
                )
            }
        )
    }
}

/**
 * Грубое распознавание пентаграммы: пять-шесть резких поворотов,
 * замкнутый контур и достаточная длина линии.
 */
private fun looksLikePentagram(points: List<Offset>, size: androidx.compose.ui.unit.IntSize): Boolean {
    if (points.size < 8) return false
    val width = size.width.toFloat()
    val height = size.height.toFloat()
    if (width <= 0f || height <= 0f) return false

    var length = 0f
    for (index in 1 until points.size) {
        length += (points[index] - points[index - 1]).getDistance()
    }
    if (length < (width + height) * 1.2f) return false

    var corners = 0
    for (index in 1 until points.size - 1) {
        val a = points[index] - points[index - 1]
        val b = points[index + 1] - points[index]
        val na = a.getDistance()
        val nb = b.getDistance()
        if (na < 1f || nb < 1f) continue
        val cos = ((a.x * b.x + a.y * b.y) / (na * nb)).coerceIn(-1f, 1f)
        val angle = Math.toDegrees(kotlin.math.acos(cos).toDouble())
        if (angle > 55.0) corners++
    }
    val closed = (points.last() - points.first()).getDistance() < minOf(width, height) * 0.35f
    return corners in 4..7 && closed
}

@Composable
private fun AnswerLabels(answer: CardAnswer?, focusLayer: Int, shift: Float = 0f) {
    val current = answer ?: return

    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val names = listOf(current.card) + current.alternatives.map { it.first }
        names.forEachIndexed { index, card ->
            val active = focusLayer < 0 || focusLayer == index
            val reversed = current.lines.getOrNull(index)?.reversed
                ?: (index == 0 && current.reversed)
            val tint = androidx.compose.ui.graphics.lerp(
                channelColor(index, shift, current.lines.size), Color(0xFF8D867B), 0.45f
            )
            val showArrow = true
            Text(
                text = card.title.lowercase() + if (showArrow) "\u00A0" + (if (reversed) "↓" else "↑") else "",
                maxLines = 2,
                softWrap = true,
                color = tint.copy(alpha = if (active) 0.98f else 0.45f),
                style = TextStyle(
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = if (active) tint.copy(alpha = 0.85f) else tint.copy(alpha = 0.35f),
                        blurRadius = if (active) 14f else 8f
                    )
                )
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    if (current.shared.isNotEmpty()) {
        TagCloud(
            tags = current.shared,
            scores = current.sharedScores,
            outer = current.outerTags,
            // цвет — это карта, а не слово: старший янтарный, младший циановый
            tint = if (current.outerTags.isNotEmpty())
                channelColor(1, shift, 2) else Color(0xFFE8E0D2),
            outerTint = channelColor(0, shift, 2),
            expanded = current.outerTags.isNotEmpty(),
            onFling = { },
            onMotion = { speed -> SoundEngine.motion(speed) },
            onClink = { index, speed, sector, volume -> SoundEngine.clink(index, speed, sector, volume) },
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        )
        val agreementText = when {
            current.outerTags.isNotEmpty() -> {
                val field = current.outerTags.firstOrNull() ?: ""
                val inner = current.shared.take(2).joinToString(", ")
                "поле — $field; в нём — $inner"
            }
            current.agreement >= 0.125f -> "карты говорят об одном"
            current.agreement >= 0.065f -> "карты близки по смыслу"
            else -> "карты расходятся"
        }
        Text(
            text = agreementText,
            style = TextStyle(fontSize = 9.sp, letterSpacing = 1.sp),
            color = ASH.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = if (focusLayer < 0) "коснись — разложить слои · пентаграмма — заново"
        else "слой ${focusLayer + 1} · коснись ещё раз",
        style = TextStyle(fontSize = 9.sp, letterSpacing = 1.sp),
        color = ASH.copy(alpha = 0.45f),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun Dots(number: Int, probability: Float, tint: Color, active: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (active) 1f else 0.35f)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(tint, CircleShape)
        )
        Text(
            text = " ${(probability * 100).toInt()}%",
            style = TextStyle(fontSize = 12.sp, letterSpacing = 0.5.sp),
            color = ASH
        )
    }
}

/** Обратная сторона: рубашка, на которой пишут вопрос. */
@Composable
private fun CardBack(
    question: String,
    onQuestion: (String) -> Unit,
    ready: Boolean,
    downloading: Boolean,
    progress: Float
) {
    val flamePhase by androidx.compose.animation.core.rememberInfiniteTransition(label = "flame").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(8000, easing = androidx.compose.animation.core.LinearEasing)
        ),
        label = "flamePhase"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF241B2C), Color(0xFF120E18))),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.05f)
            )
            val center = Offset(size.width / 2f, size.height * 0.42f)
            val radius = size.width * 0.26f
            repeat(3) { ring ->
                drawCircle(
                    color = FOIL.copy(alpha = 0.22f - ring * 0.05f),
                    radius = radius * (1f - ring * 0.22f),
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.width * 0.004f)
                )
            }
            val path = androidx.compose.ui.graphics.Path()
            for (point in 0 until 5) {
                val angle = Math.toRadians(144.0 * point - 90.0)
                val x = center.x + (radius * cos(angle)).toFloat()
                val y = center.y + (radius * sin(angle)).toFloat()
                if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, FOIL.copy(alpha = 0.35f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.width * 0.003f))

            // пламя бежит по контуру звезды: три языка, идущих друг за другом
            val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
            val length = measure.length
            if (length > 0f) {
                for (tongue in 0 until 3) {
                    val head = ((flamePhase + tongue * 0.33f) % 1f) * length
                    val piece = androidx.compose.ui.graphics.Path()
                    val tongueLength = length * 0.10f
                    if (head + tongueLength <= length) {
                        measure.getSegment(head, head + tongueLength, piece, true)
                    } else {
                        // язык переходит через стык пути — дорисовываем с начала
                        measure.getSegment(head, length, piece, true)
                        val rest = androidx.compose.ui.graphics.Path()
                        measure.getSegment(0f, head + tongueLength - length, rest, true)
                        piece.addPath(rest)
                    }
                    val core = androidx.compose.ui.graphics.Path()
                    val coreEnd = head + length * 0.04f
                    if (coreEnd <= length) {
                        measure.getSegment(head, coreEnd, core, true)
                    } else {
                        measure.getSegment(head, length, core, true)
                        val rest = androidx.compose.ui.graphics.Path()
                        measure.getSegment(0f, coreEnd - length, rest, true)
                        core.addPath(rest)
                    }

                    drawPath(
                        piece,
                        color = Color(0xFFF2A93B).copy(alpha = 0.18f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = size.width * 0.022f,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        ),
                        blendMode = androidx.compose.ui.graphics.BlendMode.Screen
                    )
                    drawPath(
                        core,
                        color = Color(0xFFFFD79A).copy(alpha = 0.95f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = size.width * 0.006f,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        ),
                        blendMode = androidx.compose.ui.graphics.BlendMode.Screen
                    )
                }
            }

            // решётка «под тиснение»
            val step = size.width / 9f
            var offset = -size.height
            while (offset < size.width + size.height) {
                drawLine(
                    color = FOIL.copy(alpha = 0.07f),
                    start = Offset(offset, 0f),
                    end = Offset(offset + size.height, size.height),
                    strokeWidth = size.width * 0.002f
                )
                drawLine(
                    color = FOIL.copy(alpha = 0.05f),
                    start = Offset(offset, size.height),
                    end = Offset(offset + size.height, 0f),
                    strokeWidth = size.width * 0.002f
                )
                offset += step
            }
            // внутренняя рамка рубашки
            drawRoundRect(
                color = FOIL.copy(alpha = 0.3f),
                topLeft = Offset(size.width * 0.045f, size.width * 0.045f),
                size = androidx.compose.ui.geometry.Size(
                    size.width - size.width * 0.09f,
                    size.height - size.width * 0.09f
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.035f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.width * 0.0035f)
            )

            if (downloading) {
                drawLine(
                    color = FOIL.copy(alpha = 0.6f),
                    start = Offset(size.width * 0.1f, size.height * 0.9f),
                    end = Offset(size.width * (0.1f + 0.8f * progress), size.height * 0.9f),
                    strokeWidth = size.width * 0.006f
                )
            }
        }

        BasicTextField(
            value = question,
            onValueChange = onQuestion,
            textStyle = TextStyle(
                fontSize = 16.sp,
                color = Color(0xFFEDE4D2),
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            ),
            cursorBrush = SolidColor(FOIL),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 34.dp)
                .heightIn(min = 48.dp, max = 116.dp),
            maxLines = 3,
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (question.isBlank()) {
                        Text(
                            text = if (ready) "вопрос" else "",
                            style = TextStyle(fontSize = 15.sp, color = ASH.copy(alpha = 0.5f)),
                            textAlign = TextAlign.Center
                        )
                    }
                    inner()
                }
            }
        )
    }
}

@Composable
private fun CrashScreen(trace: String, onShare: () -> Unit, onClear: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("ПРОШЛЫЙ ЗАПУСК УПАЛ", style = TextStyle(fontSize = 13.sp, letterSpacing = 2.sp), color = Color(0xFFC97A6A))
        Spacer(Modifier.height(12.dp))
        Text(trace, style = TextStyle(fontSize = 9.sp, lineHeight = 14.sp), color = ASH)
        Spacer(Modifier.height(20.dp))
        Text("отправить", style = TextStyle(fontSize = 13.sp), color = FOIL, modifier = Modifier.clickable { onShare() })
        Spacer(Modifier.height(14.dp))
        Text("стереть и попробовать снова", style = TextStyle(fontSize = 13.sp), color = FOIL, modifier = Modifier.clickable { onClear() })
    }
}

/** Медленно мерцающая пыль на фоне: 48 точек, каждая со своим ритмом. */
@Composable
private fun StarField() {
    val twinkle by androidx.compose.animation.core.rememberInfiniteTransition(label = "stars").animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(14000, easing = androidx.compose.animation.core.LinearEasing)
        ),
        label = "phase"
    )
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val count = 64
        for (i in 0 until count) {
            val hash = ((i + 1).toLong() * 2654435761L) % 1000003L
            val driftX = kotlin.math.sin(twinkle + i * 0.7f) * size.width * 0.012f
            val driftY = kotlin.math.cos(twinkle + i * 1.1f) * size.height * 0.010f
            val x = ((hash % 1000L).toFloat() / 1000f) * size.width + driftX
            val y = (((hash / 1000L) % 1000L).toFloat() / 1000f) * size.height + driftY
            val rhythm = 0.35f + ((i % 7) / 7f)
            val alpha = (0.16f + 0.24f * (0.5f + 0.5f * kotlin.math.sin(twinkle + i.toFloat())))
                .coerceIn(0f, 0.45f)
            val radius = if (i % 4 == 0) 2.1f else 1.3f
            drawCircle(
                color = Color(0xFFEDE4D2).copy(alpha = alpha),
                radius = radius,
                center = androidx.compose.ui.geometry.Offset(x, y)
            )
        }
    }
}
