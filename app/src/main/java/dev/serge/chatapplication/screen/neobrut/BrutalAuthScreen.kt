package dev.serge.chatapplication.screen.neobrut

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.serge.chatapplication.screen.auth.AuthUiState



@Composable
fun PhoneInput(
    value: String,
    onValueChange: (String) -> Unit
) {
    BrutalNumberField(
        value = value,
        onValueChange = onValueChange,
        placeholder = "PHONE NUMBER",
    )
}

@Composable
fun OtpInput(
    otp: String,
    onOtpChange: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(6) { index ->
            val char = otp.getOrNull(index)?.toString() ?: ""

            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .border(3.dp, MaterialTheme.colorScheme.surface)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = char,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
    BasicTextField(
        value = otp,
        onValueChange = {
            if (it.length <= 6 && it.all { char -> char.isDigit() }) onOtpChange(it)
        },
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.surface,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .focusRequester(focusRequester)
            .size(0.dp)
    )
}
@Composable
fun UserInput(
    value: String,
    onValueChange: (String) -> Unit
) {
    BrutalUserTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = "USERNAME",
    )
}