package dev.serge.chatapplication.screen.auth

data class AuthUiState(
    val phone: String = "",
    val otp: String = "",
    val isOtpSent: Boolean = false,
    val verificationId: String? = null,
    val userName: String = ""
)
