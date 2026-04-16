package dev.serge.chatapplication.screen.neobrut

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BrutalCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .padding(16.dp)
            .offset(4.dp, 4.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .offset((-4).dp, (-4).dp)
                .border(3.dp, MaterialTheme.colorScheme.surface)
                .background(MaterialTheme.colorScheme.tertiary)
                .padding(16.dp)
        ) {
            content()
        }
    }
}