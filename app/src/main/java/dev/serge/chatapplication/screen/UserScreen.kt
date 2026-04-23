package dev.serge.chatapplication.screen

import android.icu.text.SimpleDateFormat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import dev.serge.chatapplication.screen.auth.Group
import dev.serge.chatapplication.screen.auth.ChatManager
import dev.serge.chatapplication.screen.auth.ChatPreview
import dev.serge.chatapplication.screen.auth.GroupManager
import dev.serge.chatapplication.screen.auth.GroupPreview
import dev.serge.chatapplication.screen.neobrut.BrutalLoader
import java.util.Date
import java.util.Locale

@Composable
fun UserScreen(
    onUserClick: (String, String, String, Boolean) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    val chatManager = remember { ChatManager() }
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    var chatPreview by remember { mutableStateOf<List<ChatPreview>>(emptyList()) }
    var groupPreview by remember { mutableStateOf<List<GroupPreview>>(emptyList()) }
    val groupManager = remember { GroupManager() }
    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }

    LaunchedEffect(Unit) {
        var chatsLoaded = false
        var groupsLoaded = false
        chatManager.getAllUsers(currentUid) {allUsers ->
            val previews = mutableListOf<ChatPreview>()

            allUsers.forEach { user ->
                val chatId = listOf(currentUid, user.uid).sorted().joinToString("_")

                chatManager.getLatestMessage(chatId) { message ->
                    val preview = ChatPreview(
                        chatId = chatId,
                        userId = user.uid,
                        userName = user.userName,
                        lastMessage = message?.text ?: "No messages yet",
                        lastMessageTime = message?.timeStamp ?: 0,
                        lastMessageFrom = message?.senderId ?: ""
                    )
                    previews.add(preview)

                    if (previews.size == allUsers.size) {
                        chatPreview = previews.sortedByDescending { it.lastMessageTime }
                        chatsLoaded = true
                        if (groupsLoaded) isLoading = false
                    }
                }
            }
        }
        groupManager.getUserGroups {loadedGroups ->
            val gPreviews = mutableListOf<GroupPreview>()

            loadedGroups.forEach { group ->
                groupManager.getLastGroupMessage(group.id) {message ->
                    val gPreview = GroupPreview(
                        id = group.id,
                        name = group.name,
                        lastMessage = message?.text ?: "No messages yet",
                        lastMessageTime = message?.timestamp ?: group.createdAt,
                        lastMessageFrom = message?.senderName ?: "",
                        memberCount = group.members.size
                    )
                    gPreviews.add(gPreview)

                    if (gPreviews.size == loadedGroups.size) {
                        groupPreview = gPreviews.sortedByDescending { it.lastMessageTime }
                        groupsLoaded = true
                        if (chatsLoaded) isLoading = false
                    }
                }
            }
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            BrutalLoader(modifier = Modifier)
        }
    }
    else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            LazyColumn {
                items(chatPreview, key = { it.chatId }) { preview ->
                    ChatListItem(
                        preview = preview,
                        onClick = {
                            onUserClick(
                                preview.chatId,
                                preview.userName,
                                preview.userId,
                                false
                            )
                        }
                    )
                }

                items(groupPreview, key = {it.id}) {gPreview ->
                    GroupListItem(
                        preview = gPreview,
                        onClick = {
                            onUserClick(
                                gPreview.id,
                                gPreview.name,
                                gPreview.id,
                                true
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatListItem(
    preview: ChatPreview,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val offset by animateDpAsState(
        targetValue = if (isPressed) 0.dp else 4.dp,
        animationSpec = tween(80)
    )

    Box(
        modifier = Modifier
            .padding(8.dp)
            .offset {
                IntOffset(offset.roundToPx(), offset.roundToPx())
            }
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(-offset.roundToPx(), -offset.roundToPx())
                }
                .border(3.dp, MaterialTheme.colorScheme.surface)
                .background(MaterialTheme.colorScheme.tertiary)
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            Column {
                Text(
                    text = preview.userName.uppercase(),
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = preview.lastMessage,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatMessageTime(preview.lastMessageTime),
                        fontWeight = FontWeight.Normal,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

fun formatMessageTime(timestamp: Long): String {
    if (timestamp == 0L) return ""

    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 604_800_000 -> "${diff / 86_400_000}d ago"
        else -> SimpleDateFormat("MM/dd/yy", Locale.getDefault()).format(Date(timestamp))
    }
}


@Composable
fun GroupListItem(
    preview: GroupPreview,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val offset by animateDpAsState(
        targetValue = if (isPressed) 0.dp else 4.dp,
        animationSpec = tween(80)
    )

    Box(
        modifier = Modifier
            .padding(8.dp)
            .offset {
                IntOffset(offset.roundToPx(), offset.roundToPx())
            }
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(-offset.roundToPx(), -offset.roundToPx())
                }
                .border(3.dp, MaterialTheme.colorScheme.surface)
                .background(MaterialTheme.colorScheme.tertiary)
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            Column {
                Text(
                    text = preview.name.uppercase(),
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(
                        text = if (preview.lastMessageFrom.isNotEmpty()) {
                            "${preview.lastMessageFrom}: ${preview.lastMessage}"
                        } else preview.lastMessage,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatMessageTime(preview.lastMessageTime),
                        fontWeight = FontWeight.Normal,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}