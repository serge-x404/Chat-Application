package dev.serge.chatapplication

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.google.firebase.Firebase
import com.google.firebase.initialize
import dev.serge.chatapplication.navigation.NavGraph
import dev.serge.chatapplication.ui.theme.ChatApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Firebase.initialize(this)
        setContent {
            ChatApplicationTheme {
                Scaffold() {
                    val navHostController = rememberNavController()
                    val sharedPreferences = applicationContext.getSharedPreferences("chatApp", MODE_PRIVATE)

                    NavGraph(navHostController,sharedPreferences,Modifier.padding(it))
                }
            }
        }
    }
}