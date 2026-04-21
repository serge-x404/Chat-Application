package dev.serge.chatapplication.screen.auth

data class ChatGroup(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val createdBy: String = "",
    val createdAt: Long = 0,
    val members: Map<String, Boolean> = emptyMap(),
    val profileImage: String = "",
    val isPrivate: Boolean = false
)