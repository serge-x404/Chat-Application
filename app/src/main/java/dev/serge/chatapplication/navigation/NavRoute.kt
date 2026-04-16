package dev.serge.chatapplication.navigation

sealed class NavRoute(val path: String) {
    object Home: NavRoute("home")
    object ChatHome: NavRoute("chatHome")
}