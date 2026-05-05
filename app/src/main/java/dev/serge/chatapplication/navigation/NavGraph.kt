package dev.serge.chatapplication.navigation

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import dev.serge.chatapplication.screen.BrutalAuthScreen
import dev.serge.chatapplication.screen.ChatHomeScreen
import dev.serge.chatapplication.screen.GroupChatScreen
import dev.serge.chatapplication.screen.GroupRTCScreen
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
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    LaunchedEffect(currentUserId) {
        if (currentUserId.isEmpty()) return@LaunchedEffect

        val db = FirebaseDatabase.getInstance().reference
        db.child("incoming_calls").child(currentUserId)
            .addValueEventListener(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    val data = snapshot.value as? Map<*,*> ?: return
                    val callerId = data["callerId"] as? String ?: return
                    val chatId = data["chatId"] as? String ?: return
                    val callerName = data["callerName"] as? String ?: "Unknown"

                    navHostController.navigate("${NavRoute.WebRTC.path}/$chatId/$callerId/$callerName/false")

                    snapshot.ref.removeValue()
                }

                override fun onCancelled(error: DatabaseError) {}
            })

        db.child("incoming_group_calls").child(currentUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val data = snapshot.value as? Map<*, *> ?: return
                    val groupId = data["groupId"] as? String ?: return

                    navHostController.navigate("${NavRoute.GroupRTC.path}/$groupId/false")

                    snapshot.ref.removeValue()
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    NavHost(navHostController, startDestination) {
        addHomeScreen(navHostController, this)
        addChatHomeScreen(navHostController, this)
        addAuthScreen(navHostController, this)
        addGroupChatScreen(navHostController, this)
        addWebRTCScreen(navHostController, this)
        addGroupRTCScreen(navHostController, this)
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
                navHostController.navigate("${NavRoute.WebRTC.path}/$chatId/$otherUserId/$userName/true")
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
            back = {navHostController.popBackStack()},
            navigateToCall = {gId ->
                navHostController.navigate("${NavRoute.GroupRTC.path}/$gId/true")}
        )
    }
}

fun addWebRTCScreen(navHostController: NavHostController, navGraphBuilder: NavGraphBuilder) {
    navGraphBuilder.composable("${NavRoute.WebRTC.path}/{chatId}/{otherUserId}/{userName}/{isCaller}") {
        val chatId = it.arguments?.getString("chatId") ?: ""
        val otherUserid = it.arguments?.getString("otherUserId") ?: ""
        val userName = it.arguments?.getString("userName") ?: ""
        val isCaller = it.arguments?.getString("isCaller")?.toBoolean() ?: true

        WebRTCCallScreen(
            chatId = chatId,
            otherUserId = otherUserid,
            otherUserName = userName,
            isCaller = isCaller,
            onCallEnded = { navHostController.navigate(NavRoute.Home.path){
                popUpTo(NavRoute.Home.path){
                    inclusive = false
                }
            } }
        )
    }
}

fun addGroupRTCScreen(navHostController: NavHostController, navGraphBuilder: NavGraphBuilder) {
    navGraphBuilder.composable("${ NavRoute.GroupRTC.path }/{groupId}/{isCaller}") {
        val roomId = it.arguments?.getString("groupId") ?: ""
        val isCaller = it.arguments?.getString("isCaller")?.toBoolean() ?: true
        GroupRTCScreen(
            roomId = roomId,
            isCaller = isCaller,
            onCallEnded = {navHostController.navigate(NavRoute.Home.path) {
                popUpTo(NavRoute.Home.path) {
                    inclusive = false
                }
            } }
        )
    }
}