package dev.serge.chatapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import dev.serge.chatapplication.navigation.NavGraph
import dev.serge.chatapplication.ui.theme.ChatApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChatApplicationTheme {
                Scaffold() {
                    val navHostController = rememberNavController()

                    NavGraph(navHostController,Modifier.padding(it))
                }
            }
        }
    }
}