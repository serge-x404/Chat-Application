package dev.serge.chatapplication.screen.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

data class Message(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val timeStamp: Long = 0
)

class ChatManager {

    private val db = FirebaseDatabase.getInstance().reference
    private val currentId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    fun sendMessage(chatId: String, text: String) {
        val ref = db.child("chats")
            .child(chatId)
            .child("messages")
            .push()

        val message = Message(
            id = ref.key ?: "",
            senderId = currentId,
            text = text,
            timeStamp = System.currentTimeMillis()
        )
        ref.setValue(message)
    }

    fun listenToMessage(chatId: String, onMessage: (List<Message>) -> Unit): ValueEventListener {

        val listener = object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = snapshot.children.mapNotNull {
                    it.getValue(Message::class.java)
                }
                onMessage(messages)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Error",error.message)
            }
        }
        db.child("chats").child(chatId).child("messages")
            .addValueEventListener(listener)
        return listener
    }

    fun removeListener(chatId: String, listener: ValueEventListener) {
        db.child("chats").child(chatId).child("messages")
            .removeEventListener(listener)
    }

    fun getAllUsers(currentUid: String, onUsers: (List<User>) -> Unit) {
        db.child("users")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val users = snapshot.children.mapNotNull { userSnapshot ->
                        userSnapshot.getValue(User::class.java)?.takeIf { it.uid != currentUid }
                    }
                    onUsers(users)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Users",error.message)
                }
            })
    }
}