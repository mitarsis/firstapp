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
    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations) {
            DeciderHolder.close()
        }
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TarotTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = TABLE) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        TableBackdrop()
                        TarotApp()
                    }
                }
            }
        }
    }
}

/** Стол, а не фон приложения: тёмная поверхность с редкими искрами. */
private data class Spark(val x: Float, val y: Float, val radius: Float, val alpha: Float)

@Composable
private fun TableBackdrop() {
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
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(brush = Brush.radialGradient(
            colors = listOf(Color(0xFF1A1620), TABLE),
            center = Offset(size.width * 0.5f, size.height * 0.38f),
            radius = size.height * 0.75f
        ))
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
fun TarotApp() {
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
    var layers by remember { mutableStateOf(3) }
    var showGallery by remember { mutableStateOf(false) }
    val tilt = rememberTilt()
    var channelsAnimating by remember { mutableStateOf(false) }
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "channels")
    val rotatingChannels by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 4200,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
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

        // Карта — и есть интерфейс
        Box(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .aspectRatio(0.62f)
                .graphicsLayer {
                    // объём: наклон всего блока вслед за телефоном
                    val active = focusLayer < 0
                    val tiltY = if (active) tilt.second else 0f
                    val tiltX = if (active) tilt.first else 0f
                    rotationY = flip + tiltX * 9f
                    rotationX = -tiltY * 9f
                    cameraDistance = 24f * density
                    val zoom = if (focusLayer >= 0) 1.03f else 1f
                    scaleX = zoom
                    scaleY = zoom
                }
                .drawBehind {
                    // мягкая тень под картой
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 1.02f),
                            radius = size.width * 0.7f
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(-size.width * 0.1f, size.height * 0.86f),
                        size = androidx.compose.ui.geometry.Size(size.width * 1.2f, size.height * 0.3f)
                    )
                }
                .pointerInput(answer, ready, busy, question) {
                    detectTapGestures(
                        onTap = {
                            when {
                                !ready -> download()
                                busy -> Unit
                                answer == null -> {
                                    if (question.isBlank()) note = "сначала вопрос" else draw()
                                }
                                else -> {
                                    focusLayer = (focusLayer + 2) % 4 - 1
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
                    channelShift = if (
                        channelsAnimating && shown.alternatives.isNotEmpty() && focusLayer < 0
                    ) {
                        rotatingChannels
                    } else {
                        0f
                    },
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
                color = ASH.copy(alpha = 0.6f),
                modifier = Modifier.clickable { showGallery = true }
            )
            Text(
                text = if (layers == 3) "три карты" else "две карты",
                style = TextStyle(fontSize = 10.sp, letterSpacing = 1.sp),
                color = ASH.copy(alpha = 0.6f),
                modifier = Modifier.clickable { layers = if (layers == 3) 2 else 3 }
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
private fun AnswerLabels(answer: CardAnswer?, focusLayer: Int) {
    val current = answer ?: return

    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val names = listOf(current.card) + current.alternatives.map { it.first }
        names.forEachIndexed { index, card ->
            val active = focusLayer < 0 || focusLayer == index
            val reversed = current.lines.getOrNull(index)?.reversed
                ?: (index == 0 && current.reversed)
            Text(
                text = card.title.lowercase() + "\u00A0" + (if (reversed) "↓" else "↑"),
                maxLines = 1,
                softWrap = false,
                style = TextStyle(
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.8f),
                        blurRadius = 8f
                    )
                ),
                color = if (active) {
                    Color(0xFFE8DFCB).copy(alpha = 0.85f)
                } else {
                    Color(0xFFE8DFCB).copy(alpha = 0.25f)
                }
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    if (current.shared.isNotEmpty()) {
        TagCloud(
            tags = current.shared,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        )
        val agreementText = when {
            current.agreement >= 0.15f -> "карты говорят об одном"
            current.agreement >= 0.05f -> "карты близки по смыслу"
            else -> "карты расходятся"
        }
        Text(
            text = agreementText,
            style = TextStyle(fontSize = 9.sp, letterSpacing = 1.sp),
            color = ASH.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    } else {
        Reading.compose(current).forEach { line ->
            Text(
                text = line,
                style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
                color = Color(0xFFEDE4D2),
                textAlign = TextAlign.Center
            )
        }
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
