package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Messages : Screen("messages")
    object Favorites : Screen("favorites")
    object Search : Screen("search")
    object Apps : Screen("apps")
    object Security : Screen("security")
    object Troubleshooting : Screen("troubleshooting")
    object About : Screen("about")
    object Onboarding : Screen("onboarding")
    object DatabaseInspector : Screen("database_inspector")
    object MessageDetail : Screen("message_detail/{messageId}") {
        fun createRoute(messageId: Long) = "message_detail/$messageId"
    }
}
