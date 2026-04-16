package dev.serge.chatapplication.screen.neobrut

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BrutalHomeTopBar(
    title: String = "CHATS",
    onAddClick: () -> Unit = {},
    onSearchClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(6.dp, 6.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .offset((-6).dp, (-6).dp)
                .border(3.dp, MaterialTheme.colorScheme.surface)
                .background(MaterialTheme.colorScheme.secondary)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Black,
                fontSize = 26.sp,
                modifier = Modifier.weight(1f)
            )
            BrutalIconButton(
                text = "SEARCH",
                onClick = onSearchClick
            )
            Spacer(modifier = Modifier.width(8.dp))
            BrutalIconButton(
                text = "ADD",
                onClick = onAddClick
            )
        }
    }
}