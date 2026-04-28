package dev.serge.chatapplication.screen.auth

data class Group(
    val id: String = "",
    val name: String = "",
    val createdBy: String = "",
    val createdAt: Long = 0,
    val members: Map<String, GroupMember> = emptyMap()
)

data class GroupMember(
    val userId: String = "",
    val userName: String = ""
)