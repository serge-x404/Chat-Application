package dev.serge.chatapplication.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.serge.chatapplication.screen.neobrut.BrutalTopBar

@Composable
fun ChatHomeScreen() {
    Column(
        modifier = Modifier
            .systemBarsPadding()
            .navigationBarsPadding()
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BrutalTopBar("Kabir")
    }
}