package dev.serge.chatapplication.screen.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.UUID
import kotlin.collections.forEach

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
            val allMembers = (memberId + currentUid).distinct()
            val members = mutableMapOf<String, GroupMember>()
            var fetchedCount = 0

            if (allMembers.isEmpty()) {
                onError("No members selected")
                return
            }

            allMembers.forEach { uid ->
                db.child("users").child(uid).child("userName")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val userName = snapshot.getValue(String::class.java) ?: "Unknown"
                        members[uid] = GroupMember(uid, userName)
                        fetchedCount++

                        if (fetchedCount == allMembers.size) {
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
                        }
                    }
                    .addOnFailureListener {
                        onError("Failed to fetch user info for $uid")
                    }
            }
        } catch (e: Exception) {
            onError(e.message ?: "Error creating group")
        }
    }

    fun getUserGroups(onGroups: (List<Group>) -> Unit) {
        db.child("groups")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val groups = mutableListOf<Group>()
                    snapshot.children.forEach { child ->
                        try {
                            val group = child.getValue(Group::class.java)
                            if (group != null && group.members.containsKey(currentUid)) {
                                groups.add(group)
                            }
                        } catch (e: Exception) {
                            Log.e("Group", "Error parsing group ${child.key}: ${e.message}")
                        }
                    }
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
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val message = snapshot.children.mapNotNull {
                        it.getValue(GroupMessage::class.java)
                    }.lastOrNull()
                    Log.d("Group", "Last message: ${message?.text}")
                    onMessage(message)
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("Group", "getLastGroupMessage failed: ${error.message}")
                    onMessage(null)
                }
            })
    }
    fun sendGroupMessage(
        groupId: String,
        text: String,
        senderName: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val messageId = UUID.randomUUID().toString()
            val message = GroupMessage(
                id = messageId,
                groupId = groupId,
                senderId = currentUid,
                senderName = senderName,
                text = text,
                timestamp = System.currentTimeMillis()
            )

            db.child("groupMessages").child(groupId).child(messageId)
                .setValue(message)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError(it.message ?: "Failed to send message") }
        } catch (e: Exception) {
            onError(e.message ?: "Error sending message")
        }
    }

    fun getGroup(
        groupId: String,
        onGroup: (Group?) -> Unit
    ) {
        db.child("groups").child(groupId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        onGroup(snapshot.getValue(Group::class.java))
                    } catch (e: Exception) {
                        Log.e("Group", "Error parsing group $groupId: ${e.message}")
                        onGroup(null)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Group","getGroup failed: ${error.message}")
                    onGroup(null)
                }
            })
    }

    fun notifyGroupCall(groupId: String, groupName: String) {
        db.child("groups").child(groupId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val group = snapshot.getValue(Group::class.java)
                    group?.members?.keys?.forEach { memberId ->
                        if (memberId != currentUid) {
                            val notification = mapOf(
                                "groupId" to groupId,
                                "groupName" to groupName,
                                "callerId" to currentUid,
                                "timestamp" to System.currentTimeMillis()
                            )
                            db.child("incoming_group_calls").child(memberId).setValue(notification)
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
}
