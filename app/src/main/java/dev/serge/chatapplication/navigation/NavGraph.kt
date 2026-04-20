package dev.serge.chatapplication.navigation

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.serge.chatapplication.screen.BrutalAuthScreen
import dev.serge.chatapplication.screen.ChatHomeScreen
import dev.serge.chatapplication.screen.HomeScreen

@Composable
fun NavGraph(
    navHostController: NavHostController,
    sharedPreferences: SharedPreferences,
    modifier: Modifier
) {
    val isUserLoggedIn = sharedPreferences.getBoolean("isUserLoggedIn", false)

    val startDestination = if (isUserLoggedIn) NavRoute.Home.path else NavRoute.AuthScreen.path

    NavHost(navHostController, startDestination) {
        addHomeScreen(navHostController, this)
        addChatHomeScreen(navHostController, this)
        addAuthScreen(navHostController, this)
    }
}

fun addHomeScreen(navHostController: NavHostController, navGraphBuilder: NavGraphBuilder) {
    navGraphBuilder.composable(NavRoute.Home.path) {
        HomeScreen(
            navigateToChatScreen = {chatId, userName ->
                navHostController.navigate("${NavRoute.ChatHome.path}/$chatId/$userName")
            },
            navigateToAuth = {
                navHostController.navigate(NavRoute.AuthScreen.path)
            }
        )
    }
}

fun addChatHomeScreen(navHostController: NavHostController, navGraphBuilder: NavGraphBuilder) {
    navGraphBuilder.composable("${NavRoute.ChatHome.path}/{chatId}/{userName}") {
        val chatId = it.arguments?.getString("chatId") ?: ""
        val userName = it.arguments?.getString("userName") ?: ""

        ChatHomeScreen(
            chatId = chatId,
            userName = userName,
            back = {navHostController.popBackStack()}
        )
    }
}

fun addAuthScreen(navHostController: NavHostController, navGraphBuilder: NavGraphBuilder) {
    navGraphBuilder.composable(NavRoute.AuthScreen.path) {
        BrutalAuthScreen(
            navigateToChat = {
                navHostController.navigate(NavRoute.Home.path)
            }
        )
    }
}