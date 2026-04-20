package dev.serge.chatapplication.screen

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import dev.serge.chatapplication.screen.auth.AuthManager
import dev.serge.chatapplication.screen.auth.AuthUiState
import dev.serge.chatapplication.screen.neobrut.BrutalButton
import dev.serge.chatapplication.screen.neobrut.BrutalLoader
import dev.serge.chatapplication.screen.neobrut.BrutalTextField
import dev.serge.chatapplication.screen.neobrut.OtpInput
import dev.serge.chatapplication.screen.neobrut.PhoneInput
import dev.serge.chatapplication.screen.neobrut.UserInput
import java.util.concurrent.TimeUnit

@Composable
fun BrutalAuthScreen(
    navigateToChat: () -> Unit
) {

    var state by remember {
        mutableStateOf(AuthUiState())
    }

    val context = LocalContext.current
    val activity = context as Activity

    val authManager = remember { AuthManager(context) }

    var isLoading by remember { mutableStateOf(false) }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            BrutalLoader(modifier = Modifier)
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = if (!state.isOtpSent) "LOGIN" else "VERIFY",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )
        Column {

            if (!state.isOtpSent) {
                UserInput(
                    value = state.userName,
                    onValueChange = {
                        val textValidate = it.filter { char -> char.isLetterOrDigit() }
                        state = state.copy(userName = textValidate)
                    }
                )
                Spacer(Modifier.height(8.dp))
                PhoneInput(
                    value = state.phone,
                    onValueChange = {
                        val numberValidate = it.filter { char -> char.isDigit() }
                        state = state.copy(phone = numberValidate)
                    }
                )

                val isValidPhone = state.phone.length == 10
                val isValidName = state.userName.isNotBlank()

                Spacer(modifier = Modifier.height(24.dp))

                BrutalButton(
                    text = "SEND OTP",
                    onClick = {
                        if (!isValidPhone || !isValidName) return@BrutalButton

                        isLoading = true
                        sendOtp(
                            phone = "+91${state.phone}",
                            activity = activity,
                            onCodeSent = { verificationId ->
                                state = state.copy(
                                    verificationId = verificationId,
                                    isOtpSent = true
                                )
                                isLoading = false
                            },
                            onAutoVerified = {
                                isLoading = false
                                navigateToChat
                            }
                        )
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.tertiary
                )

            } else {
                OtpInput(
                    otp = state.otp,
                    onOtpChange = {
                        state = state.copy(otp = it)
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                BrutalButton(
                    text = "VERIFY",
                    onClick = {
                        Log.d("Verify", "Verify clicked ${state.otp}, ${state.verificationId}")
                        if (state.otp.length < 6) {
                            Log.d("Verify","OTP too short ${state.otp.length}")
                            return@BrutalButton
                        }

                        val verificationId = state.verificationId
                        if (verificationId == null) {
                            Log.d("Verify","verificationId is null")
                            return@BrutalButton
                        }

                        authManager.verifyOtp(
                            verificationId = verificationId,
                            code = state.otp,
                            onSuccess = {
                                Log.d("Verify", "onSuccess called -navigating")
                                navigateToChat()
                            },
                            onError = { Log.e("Verify", it) },
                            userName = state.userName
                        )
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
        Text(
            text = "SECURE LOGIN",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

fun sendOtp(
    phone: String,
    activity: Activity,
    onCodeSent: (String) -> Unit,
    onAutoVerified: () -> Unit
) {
    val options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
        .setPhoneNumber(phone)
        .setTimeout(60L, TimeUnit.SECONDS)
        .setActivity(activity)
        .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {

                FirebaseAuth.getInstance()
                    .signInWithCredential(credential)
                    .addOnSuccessListener { onAutoVerified() }
                    .addOnFailureListener { Log.e("OTP","Auto-verification failed: ${it.message}") }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.e("OTP","Failed ${e.message}")
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                onCodeSent(verificationId)
            }
        }).build()
    PhoneAuthProvider.verifyPhoneNumber(options)
}