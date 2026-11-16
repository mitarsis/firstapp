package com.mitrasis.tarot

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.random.Random

private val INK = Color(0xFF2A2118)
private val PAPER = Color(0xFFF2E9D6)
private val PAPER_DEEP = Color(0xFFE4D8BE)
private val SEPIA = Color(0xFF7A6242)

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
                Surface(modifier = Modifier.fillMaxSize(), color = PAPER) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PaperBackdrop()
                        TarotApp()
                    }
                }
            }
        }
    }
}

/** Лист бумаги: мягкие пятна, зерно и тёмные края. Никаких картинок. */
@Composable
private fun PaperBackdrop() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(PAPER, PAPER_DEEP)
            )
        )
        val random = Random(1911)
        repeat(1400) {
            val x = random.nextFloat() * size.width
            val y = random.nextFloat() * size.height
            drawCircle(
                color = SEPIA.copy(alpha = 0.05f + random.nextFloat() * 0.05f),
                radius = 0.7f + random.nextFloat() * 1.1f,
                center = androidx.compose.ui.geometry.Offset(x, y)
            )
        }
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(Color.Transparent, SEPIA.copy(alpha = 0.22f)),
                center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.4f),
                radius = size.height * 0.85f
            )
        )
    }
}

/** Тонкая линейка, как оттиск пера. */
@Composable
private fun InkRule(modifier: Modifier = Modifier, strong: Boolean = false) {
    Canvas(modifier = modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = INK.copy(alpha = if (strong) 0.55f else 0.25f),
            start = androidx.compose.ui.geometry.Offset(0f, 0.5f),
            end = androidx.compose.ui.geometry.Offset(size.width, 0.5f),
            strokeWidth = if (strong) 1.6f else 1f
        )
    }
}

/** Кнопка — не плашка, а надпись с двойным подчёркиванием. */
@Composable
private fun InkButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .alpha(if (enabled) 1f else 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text.uppercase(),
            style = TextStyle(
                fontSize = 15.sp,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Medium
            ),
            color = INK
        )
        Spacer(Modifier.height(6.dp))
        Canvas(modifier = Modifier.fillMaxWidth(0.55f).height(5.dp)) {
            drawLine(
                color = SEPIA,
                start = androidx.compose.ui.geometry.Offset(0f, 1f),
                end = androidx.compose.ui.geometry.Offset(size.width, 1f),
                strokeWidth = 1.4f
            )
            drawLine(
                color = SEPIA.copy(alpha = 0.6f),
                start = androidx.compose.ui.geometry.Offset(size.width * 0.12f, 4f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.88f, 4f),
                strokeWidth = 1f
            )
        }
    }
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
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<CardAnswer?>(null) }
    var ready by remember { mutableStateOf(ModelStore.isReady(context)) }
    var downloading by remember { mutableStateOf(false) }
    var answered by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var diagnostics by remember { mutableStateOf<List<String>?>(null) }
    var diagRunning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { runCatching { ModelStore.cleanup(context) } }
    }

    val scrollState = rememberScrollState()
    LaunchedEffect(answer) {
        if (answer != null) {
            delay(120)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 26.dp, vertical = 28.dp)
    ) {
        Text(
            text = "ТАРО-ДЕЦИДЕР",
            style = TextStyle(fontSize = 23.sp, letterSpacing = 6.sp, fontWeight = FontWeight.Normal),
            color = INK
        )
        Spacer(Modifier.height(10.dp))
        InkRule(strong = true)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "семьдесят восемь карт как векторы смысла.\nмодель не гадает — она ищет ближайшее значение",
            style = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
            color = SEPIA
        )
        Spacer(Modifier.height(28.dp))

        diagnostics?.let { lines ->
            DiagnosticsBlock(lines, diagRunning)
            Spacer(Modifier.height(20.dp))
        }

        if (!ready) {
            Text("МОДЕЛЬ НЕ УСТАНОВЛЕНА", style = TextStyle(fontSize = 13.sp, letterSpacing = 2.sp), color = INK)
            Spacer(Modifier.height(8.dp))
            Text(
                "для ответов нужен локальный классификатор: ${formatMb(ModelStore.totalBytes)}.\n" +
                    "качается один раз, дальше всё работает без сети",
                style = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
                color = SEPIA
            )
            Spacer(Modifier.height(18.dp))
            if (downloading) {
                Text(
                    "${(progress * 100).toInt()}% · ${formatMb((ModelStore.totalBytes * progress).toLong())}",
                    style = TextStyle(fontSize = 13.sp),
                    color = INK
                )
                Spacer(Modifier.height(10.dp))
                InkRule()
            } else {
                InkButton("Скачать модель") {
                    error = null
                    downloading = true
                    scope.launch {
                        try {
                            ModelStore.download(context) { done, total ->
                                progress = if (total > 0) done.toFloat() / total else 0f
                            }
                            ready = ModelStore.isReady(context)
                        } catch (t: Throwable) {
                            error = "загрузка сорвалась: ${t.javaClass.simpleName}: ${t.message}"
                        } finally {
                            downloading = false
                        }
                    }
                }
            }
        } else {
            Text(
                "ВОПРОС",
                style = TextStyle(fontSize = 11.sp, letterSpacing = 3.sp),
                color = SEPIA
            )
            Spacer(Modifier.height(4.dp))
            BasicTextField(
                value = question,
                onValueChange = {
                    question = it
                    answer = null
                    answered = false
                    error = null
                },
                textStyle = TextStyle(fontSize = 17.sp, color = INK, lineHeight = 24.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(SEPIA),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
                decorationBox = { inner ->
                    Column {
                        Box(modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            if (question.isBlank()) {
                                Text(
                                    "напиши, что тревожит",
                                    style = TextStyle(fontSize = 17.sp, color = SEPIA.copy(alpha = 0.55f))
                                )
                            }
                            inner()
                        }
                        InkRule()
                    }
                }
            )

            Spacer(Modifier.height(26.dp))
            InkButton(
                text = if (answered) "модель думает…" else "ответить картой",
                enabled = question.isNotBlank() && !answered
            ) {
                val text = question.trim()
                if (text.isEmpty()) return@InkButton
                error = null
                answered = true
                answer = null
                scope.launch {
                    try {
                        answer = withContext(Dispatchers.Default) {
                            DeciderHolder.get(context).answer(text)
                        }
                    } catch (t: Throwable) {
                        error = "модель не ответила:\n" + t.stackTraceToString().take(3000)
                    } finally {
                        answered = false
                    }
                }
            }
        }

        Spacer(Modifier.height(26.dp))

        answer?.let { current ->
            AnswerBlock(current)
            Spacer(Modifier.height(10.dp))
            InkButton("спросить другое") {
                answer = null
                question = ""
            }
        }

        error?.let {
            Spacer(Modifier.height(16.dp))
            InkRule()
            Spacer(Modifier.height(8.dp))
            Text(it, style = TextStyle(fontSize = 11.sp, lineHeight = 16.sp), color = Color(0xFF8C3B2E))
        }

        Spacer(Modifier.height(28.dp))
        InkRule()
        Spacer(Modifier.height(10.dp))
        Text(
            text = "v${BuildConfig.VERSION_NAME} · модель ${formatMb(ModelStore.installedBytes(context))} из ${formatMb(ModelStore.totalBytes)}",
            style = TextStyle(fontSize = 10.sp, letterSpacing = 1.sp),
            color = SEPIA.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                "диагностика",
                style = TextStyle(fontSize = 11.sp),
                color = SEPIA,
                modifier = Modifier.clickable {
                    if (diagRunning) return@clickable
                    diagRunning = true
                    diagnostics = emptyList()
                    scope.launch {
                        val steps = mutableListOf<String>()
                        fun report(line: String) {
                            steps.add(line)
                            diagnostics = steps.toList()
                            Log.i("TarotDiag", line)
                        }
                        try {
                            val page = try {
                                android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE).toString()
                            } catch (t: Throwable) {
                                "?"
                            }
                            report("abi ${Build.SUPPORTED_ABIS.joinToString("/")} · страница $page · sdk ${Build.VERSION.SDK_INT}")
                            report("модель: ${if (ModelStore.isReady(context)) "на месте" else "нет"}")
                            report(ModelStore.inventory(context))
                            report("удалено: ${ModelStore.cleanup(context).ifEmpty { listOf("нечего") }.joinToString()}")
                            val tokenizer = withContext(Dispatchers.IO) {
                                context.assets.open("tokenizer.bin").use { UnigramTokenizer.fromStream(it) }
                            }
                            report("токенизатор: «Смерть» -> ${tokenizer.encode("Смерть").joinToString(",")}")
                            if (ModelStore.isReady(context)) {
                                withContext(Dispatchers.Default) {
                                    val decider = DeciderHolder.get(context)
                                    decider.ensureLoaded()
                                    report(decider.describeInputs())
                                    val probe = decider.answer("стоит ли мне уйти с работы?")
                                    report("пробный ответ: ${probe.card.title} ${(probe.probability * 100).toInt()}%")
                                }
                            }
                        } catch (t: Throwable) {
                            report("ошибка: ${t.javaClass.name}: ${t.message}")
                        } finally {
                            diagRunning = false
                        }
                    }
                }
            )
            Text(
                "выгрузить модель",
                style = TextStyle(fontSize = 11.sp),
                color = SEPIA,
                modifier = Modifier.clickable {
                    scope.launch {
                        withContext(Dispatchers.Default) { DeciderHolder.close() }
                        answer = null
                        error = "модель выгружена из памяти"
                    }
                }
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun AnswerBlock(answer: CardAnswer) {
    val context = LocalContext.current
    val trio = listOf(answer.card) + answer.alternatives.map { it.first }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .align(Alignment.CenterHorizontally)
        ) {
            CardStack(
                context = context,
                cards = trio,
                probabilities = listOf(answer.probability) + answer.alternatives.map { it.second },
                reversed = answer.lines.map { it.reversed },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(20.dp))
        val reversedFlags = answer.lines.map { it.reversed }
        LegendLine(1, answer.card.title, answer.probability, positionTint(0, reversedFlags.getOrElse(0) { false }))
        answer.alternatives.forEachIndexed { index, (other, probability) ->
            LegendLine(
                index + 2, other.title, probability,
                positionTint(index + 1, reversedFlags.getOrElse(index + 1) { false })
            )
        }

        Spacer(Modifier.height(18.dp))
        InkRule()
        Spacer(Modifier.height(14.dp))
        Reading.compose(answer).forEach { paragraph ->
            Text(
                text = paragraph,
                style = TextStyle(fontSize = 17.sp, lineHeight = 26.sp, letterSpacing = 0.3.sp),
                color = INK
            )
        }
    }
}

@Composable
private fun LegendLine(number: Int, title: String, probability: Float, dot: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(7.dp).background(dot, CircleShape))
        Text(
            text = "  $number · $title",
            style = TextStyle(fontSize = 13.sp, letterSpacing = 0.5.sp),
            color = INK
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "${(probability * 100).toInt()}%",
            style = TextStyle(fontSize = 12.sp),
            color = SEPIA
        )
    }
}

@Composable
private fun DiagnosticsBlock(lines: List<String>, running: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("ДИАГНОСТИКА", style = TextStyle(fontSize = 11.sp, letterSpacing = 2.sp), color = SEPIA)
        Spacer(Modifier.height(6.dp))
        if (lines.isEmpty() && running) {
            Text("идёт проверка…", style = TextStyle(fontSize = 11.sp), color = SEPIA)
        }
        lines.forEach { line ->
            Text(line, style = TextStyle(fontSize = 10.sp, lineHeight = 15.sp), color = INK.copy(alpha = 0.8f))
        }
        Spacer(Modifier.height(8.dp))
        InkRule()
    }
}

@Composable
private fun CrashScreen(trace: String, onShare: () -> Unit, onClear: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(26.dp)
    ) {
        Text("ПРОШЛЫЙ ЗАПУСК УПАЛ", style = TextStyle(fontSize = 15.sp, letterSpacing = 2.sp), color = Color(0xFF8C3B2E))
        Spacer(Modifier.height(10.dp))
        InkRule(strong = true)
        Spacer(Modifier.height(10.dp))
        Text(
            trace,
            style = TextStyle(fontSize = 10.sp, lineHeight = 15.sp),
            color = INK.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(20.dp))
        InkButton("отправить") { onShare() }
        Spacer(Modifier.height(14.dp))
        InkButton("стереть и попробовать снова") { onClear() }
    }
}

private fun formatMb(bytes: Long): String =
    String.format(Locale.ROOT, "%.0f МБ", bytes / 1_000_000.0)
