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
import dev.serge.chatapplication.screen.GroupChatScreen
import dev.serge.chatapplication.screen.HomeScreen
import dev.serge.chatapplication.screen.WebRTCCallScreen

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
        addGroupChatScreen(navHostController, this)
        addWebRTCScreen(navHostController, this)
    }
}

fun addHomeScreen(navHostController: NavHostController, navGraphBuilder: NavGraphBuilder) {
    navGraphBuilder.composable(NavRoute.Home.path) {
        HomeScreen(
            navigateToChatScreen = {chatId, userName, userId, isGroup ->
                if (isGroup) {
                    navHostController.navigate("${NavRoute.GroupChat.path}/$chatId")
                }
                else {
                    navHostController.navigate("${NavRoute.ChatHome.path}/$chatId/$userName/$userId")
                }
            },
            navigateToAuth = {
                navHostController.navigate(NavRoute.AuthScreen.path) {
                    popUpTo(NavRoute.Home.path) {
                        inclusive = true
                    }
                }
            }
        )
    }
}

fun addChatHomeScreen(navHostController: NavHostController, navGraphBuilder: NavGraphBuilder) {
    navGraphBuilder.composable("${NavRoute.ChatHome.path}/{chatId}/{userName}/{userId}") {
        val chatId = it.arguments?.getString("chatId") ?: ""
        val userName = it.arguments?.getString("userName") ?: ""
        val userId = it.arguments?.getString("userId") ?: ""

        ChatHomeScreen(
            chatId = chatId,
            userName = userName,
            userId = userId,
            back = {navHostController.popBackStack()},
            navigateToCall = { chatId, otherUserId, userName->
                navHostController.navigate("${NavRoute.WebRTC.path}/$chatId/$otherUserId/$userName")
            }
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

fun addGroupChatScreen(navHostController: NavHostController, navGraphBuilder: NavGraphBuilder) {
    navGraphBuilder.composable("${NavRoute.GroupChat.path}/{groupId}") {
        val groupId = it.arguments?.getString("groupId") ?: ""
        GroupChatScreen(
            groupId = groupId,
            back = {navHostController.popBackStack()}
        )
    }
}

fun addWebRTCScreen(navHostController: NavHostController, navGraphBuilder: NavGraphBuilder) {
    navGraphBuilder.composable("${NavRoute.WebRTC.path}/{chatId}/{otherUserId}/{userName}") {
        val chatId = it.arguments?.getString("chatId") ?: ""
        val otherUserid = it.arguments?.getString("otherUserId") ?: ""
        val userName = it.arguments?.getString("userName") ?: ""

        WebRTCCallScreen(
            chatId = chatId,
            otherUserName = userName,
            otherUserId = otherUserid,
            onCallEnded = { navHostController.popBackStack() }
        )
    }
}