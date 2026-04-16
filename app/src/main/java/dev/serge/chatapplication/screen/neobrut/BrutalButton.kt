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

@Composable
fun BrutalButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .offset(2.dp, 2.dp)
            .background(MaterialTheme.colorScheme.background)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .offset((-2).dp, (-2).dp)
                .border(3.dp, MaterialTheme.colorScheme.background)
                .background(MaterialTheme.colorScheme.tertiary)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = text,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onTertiary
            )
        }
    }
}