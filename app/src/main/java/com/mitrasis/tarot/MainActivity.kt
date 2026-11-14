package com.mitrasis.tarot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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

private data class ReadingData(
    val question: String,
    val spread: Spread,
    val drawn: List<Drawn>,
    val result: DecisionResult,
    val passage: String
)

@Composable
fun TarotApp() {
    var question by remember { mutableStateOf("") }
    var spreadIndex by remember { mutableIntStateOf(1) }
    var reading by remember { mutableStateOf<ReadingData?>(null) }

    val current = reading
    if (current == null) {
        SetupScreen(
            question = question,
            onQuestionChange = { question = it },
            spreadIndex = spreadIndex,
            onSpreadChange = { spreadIndex = it },
            onDraw = {
                val spread = SPREADS[spreadIndex]
                val drawn = Deck.draw(spread)
                val passage = buildPassage(question, spread, drawn)
                val schema = buildSchema(spread, drawn)
                val result = StubDecider(drawn).decide(passage, schema)
                reading = ReadingData(question, spread, drawn, result, passage)
            }
        )
    } else {
        ReadingScreen(
            data = current,
            onReset = { reading = null }
        )
    }
}

@Composable
private fun SetupScreen(
    question: String,
    onQuestionChange: (String) -> Unit,
    spreadIndex: Int,
    onSpreadChange: (Int) -> Unit,
    onDraw: () -> Unit
) {
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
            text = "78 карт, схема типизированных вопросов и модель, которая не притворяется, что умеет писать прозу",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
        )

        OutlinedTextField(
            value = question,
            onValueChange = onQuestionChange,
            label = { Text("Твой вопрос") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )

        Spacer(Modifier.height(20.dp))
        Text("Расклад", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        SPREADS.forEachIndexed { index, spread ->
            val selected = index == spreadIndex
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSpreadChange(index) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(spread.name, fontWeight = FontWeight.SemiBold)
                    Text(spread.hint, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = spread.positions.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDraw,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Тянуть карты", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Классификатор: заглушка. Позже здесь будет GLiNER2.5-Decide, локально через ONNX.",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun ReadingScreen(
    data: ReadingData,
    onReset: () -> Unit
) {
    val paragraphs = remember(data) {
        interpret(data.question, data.spread, data.drawn, data.result)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = data.spread.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Расклад из ${data.drawn.size} карт · уверенность ${(data.result.confidence * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        data.drawn.forEach { d ->
            DrawnCardRow(d)
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))
        Text("Вердикт", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        paragraphs.forEach { p ->
            Text(
                text = p,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("Ответы модели по схеме", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        data.result.answers.forEach { answer ->
            AnswerBlock(answer)
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Пассаж, который уйдёт в модель", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(6.dp))
                Text(data.passage, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onReset,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Новый расклад")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DrawnCardRow(d: Drawn) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .background(
                        color = if (d.reversed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        shape = RoundedCornerShape(2.dp)
                    )
            ) {}
            Spacer(Modifier.width(12.dp))
            Column {
                Text(d.position.uppercase(), style = MaterialTheme.typography.labelSmall)
                Text(d.card.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${d.card.arcana} · ${d.orientation}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = d.activeKeywords().take(3).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AnswerBlock(answer: Answer) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = answer.question.prompt,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        answer.scores.forEach { score ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = score.option,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(112.dp)
                )
                LinearProgressIndicator(
                    progress = { score.probability },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                )
                Text(
                    text = "${(score.probability * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .width(48.dp)
                        .padding(start = 8.dp)
                )
            }
        }
    }
}
