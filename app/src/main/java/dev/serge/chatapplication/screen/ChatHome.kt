package dev.serge.chatapplication.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.serge.chatapplication.Message
import dev.serge.chatapplication.screen.neobrut.BrutalMessageCard
import dev.serge.chatapplication.screen.neobrut.BrutalTextField
import dev.serge.chatapplication.screen.neobrut.BrutalTopBar

@Composable
fun ChatHomeScreen(
    back: () -> Unit
) {

    var inputText by rememberSaveable { mutableStateOf("") }

    val messages = remember { mutableStateListOf<Message>() }
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .systemBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(
                alpha = 0.8f
            ))
    ) {
        BrutalTopBar("Kabir", onBackClick = back)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            state = listState
        ) {
            items(messages) {message ->
                BrutalMessageCard(message)
            }
        }
        BrutalTextField(
            inputText,
            { inputText = it },
            {
                if (inputText.isNotBlank()) {
                    messages.add(
                        Message(
                            id = messages.size,
                            text = inputText,
                            isMe = true
                        )
                    )
                    inputText = ""
                }
            },
            "Message..."
        )
    }
}