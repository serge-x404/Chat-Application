package dev.serge.chatapplication.screen.neobrut

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth

@Composable
fun BrutalHomeTopBar(
    title: String = "CHATS",
    onAddClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onLogout: () -> Unit = {}
) {

    var showDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("chatApp", Context.MODE_PRIVATE )

    if (showDialog) {
        BrutalLogoutDialog(
            show = showDialog,
            onConfirm = {
                FirebaseAuth.getInstance().signOut()
                sharedPreferences.edit { putBoolean("isUserLoggedIn",false) }
                showDialog = false
                onLogout()
            },
            onDismiss = { showDialog = false }
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(6.dp, 6.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .offset((-6).dp, (-6).dp)
                .border(3.dp, MaterialTheme.colorScheme.surface)
                .background(MaterialTheme.colorScheme.secondary)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Black,
                fontSize = 26.sp,
                modifier = Modifier.weight(1f)
            )
            BrutalIconButton(
                text = "LOGOUT",
                onClick = { showDialog = true}
            )
        }
    }
}