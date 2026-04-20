package dev.serge.chatapplication.screen.auth

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.database.FirebaseDatabase

class AuthManager(context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    private val sharedPreferences = context.getSharedPreferences("chatApp",Context.MODE_PRIVATE)

    fun verifyOtp(
        verificationId: String,
        userName: String,
        code: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        auth.signInWithCredential(credential)
            .addOnCompleteListener {task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser

                    user?.let {
                        saveUser(
                            it.uid,
                            it.phoneNumber ?: "",
                            userName
                        ) {
                            onSuccess()
                        }
                    }
                } else {
                    onError(task.exception?.message ?: "Verification failed")
                }
            }
    }

    private fun saveUser(
        uid: String,
        phone: String,
        userName: String,
        onComplete: () -> Unit
    ) {
        val userRef = db.child("users").child(uid)

        userRef.get().addOnSuccessListener { snapshot ->

            if (!snapshot.exists()) {

                val user = mapOf(
                    "uid" to uid,
                    "phone" to phone,
                    "userName" to userName,
                    "createdAt" to System.currentTimeMillis()
                )

                userRef.setValue(user)
                    .addOnSuccessListener {
                        sharedPreferences.edit { putBoolean("isUserLoggedIn", true) }
                        onComplete()
                    }
                    .addOnFailureListener { onComplete() }
            }
            else {
                sharedPreferences.edit { putBoolean("isUserLoggedIn", true).apply() }
                onComplete()
            }
        }
            .addOnFailureListener { onComplete() }
    }
}