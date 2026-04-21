package dev.serge.chatapplication.screen.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.UUID

class GroupManager {

    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private val currentUid = auth.currentUser?.uid ?: ""

    fun createGroup(
        name: String,
        memberId: List<String>,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val groupId = UUID.randomUUID().toString()
            val members = mutableMapOf<String, Boolean>()

            members[currentUid] = true
            
            memberId.forEach { members[it] = true}

            val group = ChatGroup(
                id = groupId,
                name = name,
                createdBy = currentUid,
                createdAt = System.currentTimeMillis(),
                members = members,
                isPrivate = false
            )
            db.child("groups").child(groupId).setValue(group)
                .addOnSuccessListener { onSuccess(groupId) }
                .addOnFailureListener { onError(it.message ?: "Failed to create group") }
        } catch (e: Exception) {
            onError(e.message ?: "Error creating group")
        }
    }
}