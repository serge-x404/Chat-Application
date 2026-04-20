package dev.serge.chatapplication.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.serge.chatapplication.screen.neobrut.BrutalCard
import dev.serge.chatapplication.screen.neobrut.BrutalHomeTopBar

@Composable
fun HomeScreen(
    navigateToChatScreen: (String, String) -> Unit,
    navigateToAuth: () -> Unit
) {
    Column(
        modifier = Modifier
            .systemBarsPadding()
            .navigationBarsPadding()
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BrutalHomeTopBar(onLogout = navigateToAuth)
        UserScreen { chatId, userName ->
            navigateToChatScreen(chatId,userName)
        }
//        Spacer(Modifier.height(20.dp))
//        BrutalCard(
//            {
//                Column(
//                    modifier = Modifier.fillMaxWidth()
//                ) {
//                    Row(
//                        modifier = Modifier.fillMaxWidth(),
//                        horizontalArrangement = Arrangement.SpaceBetween,
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Text(
//                            "Kabir".uppercase(),
//                            fontWeight = FontWeight.Bold,
//                            fontSize = 18.sp
//                        )
//                        Icon(
//                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
//                            contentDescription = null
//                        )
//                    }
//                }
//            },
//            navigateToChatScreen
//        )
    }
}