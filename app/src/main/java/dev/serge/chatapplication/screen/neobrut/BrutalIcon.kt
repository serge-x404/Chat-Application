package dev.serge.chatapplication.screen.neobrut

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BrutalIconButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .offset(4.dp, 4.dp)
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .offset((-4).dp, (-4).dp)
                .border(3.dp, MaterialTheme.colorScheme.surface)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}