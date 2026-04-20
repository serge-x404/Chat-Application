package dev.serge.chatapplication.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import dev.serge.chatapplication.screen.auth.ChatManager
import dev.serge.chatapplication.screen.auth.User

@Composable
fun UserScreen(
    onUserClick: (String, String) -> Unit
) {
    var users by remember { mutableStateOf<List<User>>(emptyList()) }

    val chatManager = remember { ChatManager() }
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    LaunchedEffect(Unit) {
        chatManager.getAllUsers(currentUid) { users = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "SELECT USER",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black
        )

        LazyColumn() {
            items(users, key = {it.uid}) {user ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .border(2.dp, MaterialTheme.colorScheme.surface)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(12.dp)
                        .clickable{
                            val chatId = listOf(currentUid, user.uid).sorted().joinToString("_")
                            onUserClick(chatId,user.uid)
                        }
                ) {
                    Text(
                        text = user.phone,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}