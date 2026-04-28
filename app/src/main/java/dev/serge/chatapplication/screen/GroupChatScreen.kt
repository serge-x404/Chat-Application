package dev.serge.chatapplication.screen

import android.util.Log
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import dev.serge.chatapplication.screen.auth.GroupManager
import dev.serge.chatapplication.screen.auth.GroupMessage
import dev.serge.chatapplication.screen.neobrut.BrutalGroupBar
import dev.serge.chatapplication.screen.neobrut.BrutalGroupCard
import dev.serge.chatapplication.screen.neobrut.BrutalTextField

@Composable
fun GroupChatScreen(
    groupId: String,
    back: () -> Unit
) {

    var groupName by remember { mutableStateOf("Group") }
    var inputText by rememberSaveable { mutableStateOf("") }
    val messages = remember { mutableStateOf<List<GroupMessage>>(emptyList()) }
    val listState = rememberLazyListState()
    val groupManager = remember { GroupManager() }
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    var memberCount by remember { mutableStateOf(0) }
    var currentUserName by remember { mutableStateOf("User") }

    LaunchedEffect(groupId) {
        groupManager.getGroup(groupId) { group ->
            group?.let {
                groupName = it.name
                memberCount = it.members.size
            }
        }

        val db = FirebaseDatabase.getInstance().reference
        db.child("users").child(currentUid).child("userName")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val name = snapshot.getValue(String::class.java)
                    currentUserName = name ?: "User"
                }
                override fun onCancelled(error: DatabaseError) {
                    currentUserName = "User"
                }
            })


        FirebaseAuth.getInstance().currentUser?.displayName?.let {
            currentUserName = it
        }
    }

    DisposableEffect(groupId) {
        val listener = groupManager.listenToGroupMessages(groupId) { newMessages ->
            messages.value = newMessages
        }
        onDispose { groupManager.removeMessageListener(groupId, listener) }
    }

    LaunchedEffect(messages.value.size) {
        if (messages.value.isNotEmpty()) {
            listState.animateScrollToItem(messages.value.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f))
    ) {
        BrutalGroupBar(groupName, memberCount, modifier = Modifier, onBackClick = back)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            state = listState
        ) {
            items(messages.value, key = { it.id }) { message ->
                BrutalGroupCard(
                    message = message,
                    isMe = message.senderId == currentUid
                )
            }
        }

        BrutalTextField(
            inputText,
            { inputText = it },
            {
                if (inputText.isNotBlank()) {
                    groupManager.sendGroupMessage(
                        groupId = groupId,
                        text = inputText,
                        senderName = currentUserName,
                        onSuccess = { inputText = "" },
                        onError = { error ->
                            Log.e("Group", "Failed to send message: $error")
                        }
                    )
                }
            },
            "Message.."
        )
    }
}