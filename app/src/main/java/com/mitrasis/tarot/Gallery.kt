package com.mitrasis.tarot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image

/**
 * Галерея всей колоды: 78 карт сеткой, по касанию — крупно,
 * со значением и положением в колоде.
 */
@Composable
fun CardGallery(onClose: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B090D))
    ) {
        val chosen = selected
        if (chosen != null) {
            val card = Deck.byId(chosen)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .clickable { selected = null },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))
                CardStack(
                    context = context,
                    cards = listOf(card),
                    probabilities = listOf(1f),
                    reversed = listOf(false),
                    modifier = Modifier.fillMaxWidth(0.72f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = card.title.lowercase(),
                    style = TextStyle(fontSize = 15.sp),
                    color = Color(0xFFEDE4D2)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = card.light.joinToString(" · "),
                    style = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
                    color = Color(0xFFB9AE9A),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "перевёрнутая: " + card.shadow.joinToString(" · "),
                    style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
                    color = Color(0xFF8E8375),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "коснись, чтобы вернуться",
                    style = TextStyle(fontSize = 10.sp),
                    color = Color(0xFF6E6558)
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                Text(
                    text = "колода",
                    style = TextStyle(fontSize = 13.sp, letterSpacing = 3.sp),
                    color = Color(0xFFC9A26B)
                )
                Spacer(Modifier.height(10.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(3)) {
                    items(Deck.cards) { card ->
                        val image = rememberCardImage(context, card.id)
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .fillMaxWidth()
                                .aspectRatio(0.62f)
                                .clickable { selected = card.id }
                        ) {
                            if (image != null) {
                                Image(
                                    bitmap = image,
                                    contentDescription = card.title,
                                    modifier = Modifier.fillMaxSize().background(Color(0xFF14111C)),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = onClose) {
                    Text("закрыть", color = Color(0xFFC9A26B), fontSize = 12.sp)
                }
            }
        }
    }
}
