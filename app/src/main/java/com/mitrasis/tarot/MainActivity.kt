package com.mitrasis.tarot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
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
    val scope = rememberCoroutineScope()
    val decider = remember { GlinerDecider(context) }

    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<CardAnswer?>(null) }
    var ready by remember { mutableStateOf(ModelStore.isReady(context)) }
    var downloading by remember { mutableStateOf(false) }
    var answered by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }

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
            text = "78 карт как классы. Модель не гадает — она выбирает карту.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
        )

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
                            error = "загрузка сорвалась: ${t.message}"
                        } finally {
                            downloading = false
                        }
                    }
                }
            )
            return@Column
        }

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
                        answer = withContext(Dispatchers.Default) { decider.answer(text) }
                    } catch (t: Throwable) {
                        error = "модель не ответила: ${t.message}"
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
                text = "Первый ответ дольше: модель грузится в память (10-20 с), дальше быстрее.",
                style = MaterialTheme.typography.labelSmall
            )
        }

        Spacer(Modifier.height(24.dp))

        answer?.let { current ->
            AnswerCard(current)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { answer = null; question = "" }) {
                Text("Спросить другое")
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "Движок: ${decider.name} · модель ${formatMb(ModelStore.installedBytes(context))}",
            style = MaterialTheme.typography.labelSmall
        )
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
private fun AnswerCard(answer: CardAnswer) {
    val card = answer.card
    val keywords = if (answer.reversed) card.shadow else card.light

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("ОТВЕТ", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(10.dp))
            Text(
                text = card.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = card.titleEn,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(3.dp)
                    )
            ) {}
            Spacer(Modifier.height(8.dp))
            Text(
                text = "уверенность ${(answer.probability * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = keywords.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            if (answer.topic.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "тема: ${answer.topic}",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
            answer.runnerUp?.let { (other, p) ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "следующая гипотеза: ${other.title}, ${(p * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun formatMb(bytes: Long): String =
    String.format(Locale.ROOT, "%.0f МБ", bytes / 1_000_000.0)
