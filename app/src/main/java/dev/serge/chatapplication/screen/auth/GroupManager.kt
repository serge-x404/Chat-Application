package dev.serge.chatapplication.screen.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
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

            val group = Group(
                id = groupId,
                name = name,
                createdBy = currentUid,
                createdAt = System.currentTimeMillis(),
                members = members
            )
            db.child("groups").child(groupId).setValue(group)
                .addOnSuccessListener { onSuccess(groupId) }
                .addOnFailureListener { onError(it.message ?: "Failed to create group") }
        } catch (e: Exception) {
            onError(e.message ?: "Error creating group")
        }
    }

    fun getUserGroups(onGroups: (List<Group>) -> Unit) {
        db.child("groups")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val groups = snapshot.children
                        .mapNotNull { it.getValue(Group::class.java) }
                        .filter { it.members.containsKey(currentUid) }
                    onGroups(groups)
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("Group", "getUserGroups failed: ${error.message}")
                }
            })
    }

    fun listenToGroupMessages(
        groupId: String,
        onMessages: (List<GroupMessage>) -> Unit
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = snapshot.children
                    .mapNotNull { it.getValue(GroupMessage::class.java) }
                    .sortedBy { it.timestamp }
                onMessages(messages)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("Group", "listenToGroupMessages failed: ${error.message}")
            }
        }

        db.child("groupMessages").child(groupId)
            .addValueEventListener(listener)
        return listener
    }

    fun removeMessageListener(groupId: String, listener: ValueEventListener) {
        db.child("groupMessages").child(groupId)
            .removeEventListener(listener)
    }
    fun getLastGroupMessage(
        groupId: String,
        onMessage: (GroupMessage?) -> Unit
    ) {
        db.child("groupMessages").child(groupId)
            .limitToLast(1)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val message = snapshot.children.lastOrNull()
                        ?.getValue(GroupMessage::class.java)
                    onMessage(message)
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("Group", "getLastGroupMessage failed: ${error.message}")
                }
            })
    }
}