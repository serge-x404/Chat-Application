package dev.serge.chatapplication.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
    headlineLarge = TextStyle(
        fontSize = 36.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.sp
    ),

    headlineMedium = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.ExtraBold
    ),

    titleLarge = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold
    ),

    bodyLarge = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium
    ),

    bodyMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal
    ),

    labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )
)