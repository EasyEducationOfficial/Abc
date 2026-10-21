package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Messages : Screen("messages")
    object MediaVault : Screen("media_vault")
    object Favorites : Screen("favorites")
    object Search : Screen("search")
    object Apps : Screen("apps")
    object Security : Screen("security")
    object Troubleshooting : Screen("troubleshooting")
    object About : Screen("about")
    object PermissionWizard : Screen("permission_wizard")
    object MessageDetail : Screen("message_detail/{messageId}") {
        fun createRoute(messageId: Long) = "message_detail/$messageId"
    }
    object MediaViewer : Screen("media_viewer/{mediaId}") {
        fun createRoute(mediaId: Long) = "media_viewer/$mediaId"
    }
}
