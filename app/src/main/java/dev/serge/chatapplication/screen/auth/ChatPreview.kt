package dev.serge.chatapplication.screen.auth

data class ChatPreview(
    val chatId: String,
    val userId: String,
    val userName: String,
    val lastMessage: String = "",
    val lastMessageTime: Long = 0,
    val lastMessageFrom: String = ""
)
