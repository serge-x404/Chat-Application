package dev.serge.chatapplication.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.serge.chatapplication.screen.neobrut.BrutalTextField
import dev.serge.chatapplication.screen.neobrut.BrutalTopBar

@Composable
fun ChatHomeScreen(
    back: () -> Unit
) {

    var inputText by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .systemBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BrutalTopBar("Kabir", onBackClick = back)
        Spacer(modifier = Modifier.weight(1f))
        BrutalTextField(inputText, { inputText = it }, {}, "Message...")
    }
}