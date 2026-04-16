package dev.serge.chatapplication.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.serge.chatapplication.screen.ChatHomeScreen
import dev.serge.chatapplication.screen.HomeScreen

@Composable
fun NavGraph(
    navHostController: NavHostController,
    modifier: Modifier
) {
    NavHost(navHostController, NavRoute.Home.path) {
        addHomeScreen(navHostController, this)
        addChatHomeScreen(navHostController, this)
    }
}

fun addHomeScreen(navHostController: NavHostController, navGraphBuilder: NavGraphBuilder) {
    navGraphBuilder.composable(NavRoute.Home.path) {
        HomeScreen(
            navigateToChatScreen = {
                navHostController.navigate(NavRoute.ChatHome.path)
            }
        )
    }
}

fun addChatHomeScreen(navHostController: NavHostController, navGraphBuilder: NavGraphBuilder) {
    navGraphBuilder.composable(NavRoute.ChatHome.path) {
        ChatHomeScreen(
            back = {navHostController.popBackStack()}
        )
    }
}