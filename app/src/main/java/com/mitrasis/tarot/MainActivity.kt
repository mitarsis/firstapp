package com.mitrasis.tarot

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.util.Locale

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
                Surface(modifier = Modifier.fillMaxSize()) {
                    TarotApp()
                }
            }
        }
    }
}

@Composable
fun TarotApp() {
    val context = LocalContext.current

    // Самая первая проверка: если прошлый запуск упал — показываем стектрейс,
    // и только потом трогаем ONNX и прочие опасные штуки.
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
                context.startActivity(Intent.createChooser(intent, "Отправить стектрейс"))
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

    // При старте выбрасываем остатки прошлых схем: старые модели весили сотни мегабайт.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { runCatching { ModelStore.cleanup(context) } }
    }
    var diagRunning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "ТАРО-ДЕЦИДЕР",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "78 карт как векторы смысла. Модель не гадает — она ищет ближайшее значение.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
        )

        diagnostics?.let { lines ->
            DiagnosticsCard(lines, diagRunning)
            Spacer(Modifier.height(16.dp))
        }

        if (!ready) {
            ModelCard(
                downloading = downloading,
                progress = progress,
                onDownload = {
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
            )
        } else {
            OutlinedTextField(
                value = question,
                onValueChange = {
                    question = it
                    answer = null
                    answered = false
                    error = null
                },
                label = { Text("Вопрос") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val text = question.trim()
                    if (text.isEmpty()) return@Button
                    error = null
                    answered = true
                    answer = null
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.Default) {
                                DeciderHolder.get(context).answer(text)
                            }
                            answer = result
                        } catch (t: Throwable) {
                            error = "модель не ответила:\n" + t.stackTraceToString().take(4000)
                        } finally {
                            answered = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = question.isNotBlank() && !answered
            ) {
                Text(
                    text = if (answered) "Модель думает…" else "Ответить картой",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (answered) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Первый ответ дольше: модель грузится в память.",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        answer?.let { current ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + scaleIn(initialScale = 0.92f)
            ) {
                AnswerCard(current, question)
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { answer = null; question = "" }) {
                Text("Спросить другое")
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = it,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = {
                if (diagRunning) return@TextButton
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
                        val pageSize = try {
                            Os.sysconf(OsConstants._SC_PAGESIZE).toString()
                        } catch (t: Throwable) {
                            "?"
                        }
                        report("0. abi: ${Build.SUPPORTED_ABIS.joinToString("/")} · страница: $pageSize байт · sdk ${Build.VERSION.SDK_INT}")
                        report("1. модель: ${if (ModelStore.isReady(context)) "файлы на месте" else "не скачана"}")
                        report("   путь: ${ModelStore.dir(context).absolutePath}")

                        val tokenizer = withContext(Dispatchers.IO) {
                            context.assets.open("tokenizer.bin").use { UnigramTokenizer.fromStream(it) }
                        }
                        report("2. токенизатор загружен")

                        val ids = tokenizer.encode("Смерть")
                        report("3. «Смерть» -> ${ids.joinToString(",")}")

                        val labels = Deck.labelsRu
                        report("4. метки: ${labels.size}, первая ${labels.first()}, последняя ${labels.last()}")

                        report("5. вектор вопроса: считается одним проходом")

                        if (!ModelStore.isReady(context)) {
                            report("6. сессию не проверяю: модели нет")
                        } else {
                            report("6. что лежит в папке:\n" + ModelStore.inventory(context))
                            val removed = ModelStore.cleanup(context)
                            report("6a. удалено лишнего: ${if (removed.isEmpty()) "нечего" else removed.joinToString(", ")}")
                            report("7. создаю ONNX-сессию…")
                            withContext(Dispatchers.Default) {
                                val decider = DeciderHolder.get(context)
                                decider.ensureLoaded()
                                report("8. сессия создана")
                                report("9. входы: ${decider.describeInputs()}")
                                val result = decider.answer("Стоит ли мне уйти с работы?")
                                report("10. ответ: ${result.card.title} ${(result.probability * 100).toInt()}%")
                            }
                        }
                        report("ГОТОВО")
                    } catch (t: Throwable) {
                        report("ОШИБКА:")
                        t.stackTraceToString().lines().take(30).forEach { report("  " + it) }
                    } finally {
                        diagRunning = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (diagRunning) "Проверяю…" else "Проверить движок")
        }

        TextButton(
            onClick = {
                scope.launch {
                    withContext(Dispatchers.Default) { DeciderHolder.close() }
                    answer = null
                    error = "модель выгружена из памяти"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Выгрузить модель из памяти")
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "модель ${formatMb(ModelStore.installedBytes(context))} из ${formatMb(ModelStore.totalBytes)}",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun DiagnosticsCard(lines: List<String>, running: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Диагностика", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (lines.isEmpty() && running) {
                Text("пошёл по шагам…", style = MaterialTheme.typography.labelSmall)
            }
            lines.forEach { line ->
                Text(line, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun LegendRow(marker: String, title: String, probability: Float, strong: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = marker,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "  $title  ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal
        )
        Text(
            text = "${(probability * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun CrashScreen(trace: String, onShare: () -> Unit, onClear: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "ПРОШЛЫЙ ЗАПУСК УПАЛ",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Стектрейс сохранён. Отправь его — по нему видно, что именно сломалось.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = trace,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
            Text("Отправить стектрейс")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
            Text("Стереть и попробовать снова")
        }
    }
}

@Composable
private fun ModelCard(
    downloading: Boolean,
    progress: Float,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Модель не установлена", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Для ответов нужен локальный классификатор: ${formatMb(ModelStore.totalBytes)}. " +
                    "Качается один раз, дальше всё работает без сети.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(14.dp))
            if (downloading) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${(progress * 100).toInt()}% · ${formatMb((ModelStore.totalBytes * progress).toLong())}",
                    style = MaterialTheme.typography.labelSmall
                )
            } else {
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                    Text("Скачать модель")
                }
            }
        }
    }
}

@Composable
private fun AnswerCard(answer: CardAnswer, question: String) {
    val context = LocalContext.current
    val card = answer.card
    val trio = listOf(card) + answer.alternatives.map { it.first }

    Column(modifier = Modifier.fillMaxWidth()) {

        // Transparent Tarot: три карты лежат друг на друге, штрихи складываются
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .align(Alignment.CenterHorizontally)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(1.dp)
        ) {
            CardStack(
                context = context,
                cards = trio,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(16.dp))
        LegendRow("1", card.title, answer.probability, strong = true)
        answer.alternatives.forEachIndexed { index, (other, probability) ->
            LegendRow((index + 2).toString(), other.title, probability, strong = false)
        }

        if (answer.summary.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = answer.summary.replaceFirstChar { it.uppercase() } + ".",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun formatMb(bytes: Long): String =
    String.format(Locale.ROOT, "%.0f МБ", bytes / 1_000_000.0)
