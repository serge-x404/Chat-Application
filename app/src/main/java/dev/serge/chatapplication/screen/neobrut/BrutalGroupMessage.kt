package dev.serge.chatapplication.screen.neobrut

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.serge.chatapplication.screen.auth.GroupMessage
import dev.serge.chatapplication.screen.formatMessageTime

@Composable
fun BrutalGroupCard(
    message: GroupMessage,
    isMe: Boolean
) {

    val alignment = if (isMe) Arrangement.End else Arrangement.Start

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
                .widthIn(max = 250.dp)
        ) {
            Column {
                if (!isMe) {
                    Text(
                        text = message.senderName,
                        color = Color.Black.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = message.text,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatMessageTime(message.timestamp),
                    color = Color.Black.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Normal,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}