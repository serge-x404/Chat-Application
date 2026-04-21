package dev.serge.chatapplication.screen.auth

data class GroupPreview(
    val id: String,
    val name: String,
    val lastMessage: String = "",
    val lastMessageTime: Long = 0,
    val lastMessageFrom: String = "",
    val memberCount: Int = 0
)
