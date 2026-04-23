package dev.serge.chatapplication.screen.neobrut

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun BrutalLogoutDialog(
    show: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return

    Dialog(onDismissRequest = onDismiss) {

        Box(
            modifier = Modifier
                .offset(6.dp, 6.dp)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .offset((-6).dp, (-6).dp)
                    .border(3.dp, MaterialTheme.colorScheme.surface)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(16.dp)
            ) {

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "LOGOUT?",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "You will be signed out.",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(18.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    BrutalButton(
                        text = "CANCEL",
                        onClick = onDismiss,
                        modifier = Modifier,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    BrutalButton(
                        text = "LOGOUT",
                        onClick = onConfirm,
                        modifier = Modifier,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}