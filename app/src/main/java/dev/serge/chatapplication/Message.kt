package dev.serge.chatapplication

data class Message(
    val id: Int,
    val text: String,
    val isMe: Boolean
)
