package dev.serge.chatapplication.screen.neobrut

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.serge.chatapplication.Message

@Composable
fun BrutalMessageCard(message: Message) {

    val alignment = if (message.isMe) Arrangement.End else Arrangement.Start

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = alignment
    ) {
        Box(
            modifier = Modifier
                .padding(8.dp)
                .border(3.dp, MaterialTheme.colorScheme.surface)
                .background(MaterialTheme.colorScheme.secondary)
                .padding(12.dp)
        ) {
            Text(
                text = message.text,
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        }
    }
}