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