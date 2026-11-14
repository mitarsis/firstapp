package com.mitrasis.tarot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

private val decider: CardDecider = StubCardDecider()

@Composable
fun TarotApp() {
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<CardAnswer?>(null) }

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
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        OutlinedTextField(
            value = question,
            onValueChange = {
                question = it
                answer = null
            },
            label = { Text("Вопрос") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { answer = decider.answer(question) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = question.isNotBlank()
        ) {
            Text("Ответить картой", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(24.dp))

        val current = answer
        if (current != null) {
            AnswerCard(current)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { answer = null }) {
                Text("Спросить другое")
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "Движок: ${decider.name}",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun AnswerCard(answer: CardAnswer) {
    val card = answer.card
    val keywords = if (answer.reversed) card.shadow else card.light
    val accent = if (answer.reversed) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

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
            Text(
                text = "ОТВЕТ",
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = card.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = card.titleEn + " · " + if (answer.reversed) "перевёрнута" else "прямая",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(
                        color = accent,
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

            answer.runnerUp?.let { (other, p) ->
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "следующая гипотеза: ${other.title}, ${(p * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
