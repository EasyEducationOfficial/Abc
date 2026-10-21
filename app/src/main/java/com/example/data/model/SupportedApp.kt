package com.example.data.model

import android.content.Context
import androidx.compose.ui.graphics.Color

data class SupportedApp(
    val packageName: String,
    val displayName: String,
    val brandColorHex: Long,
    val shortLabel: String,
    val isInstalled: Boolean = false,
    val isMonitored: Boolean = true
) {
    val brandColor: Color get() = Color(brandColorHex)
}

object SupportedAppRegistry {
    val ALL_SUPPORTED_APPS = listOf(
        SupportedApp("com.whatsapp", "WhatsApp", 0xFF25D366, "WA"),
        SupportedApp("com.whatsapp.w4b", "WhatsApp Business", 0xFF128C7E, "WAB"),
        SupportedApp("com.facebook.orca", "Messenger", 0xFF0084FF, "FB"),
        SupportedApp("com.facebook.mlite", "Messenger Lite", 0xFF0084FF, "FBL"),
        SupportedApp("com.facebook.katana", "Facebook", 0xFF1877F2, "FBK"),
        SupportedApp("com.facebook.lite", "Facebook Lite", 0xFF1877F2, "FBL"),
        SupportedApp("com.google.android.gm", "Gmail", 0xFFEA4335, "GM"),
        SupportedApp("com.instagram.android", "Instagram", 0xFFE1306C, "IG"),
        SupportedApp("com.instagram.barcelona", "Threads", 0xFF101010, "TH"),
        SupportedApp("com.imo.android.imoim", "imo", 0xFF00A0EA, "IMO"),
        SupportedApp("com.imo.android.imoimbeta", "imo Beta", 0xFF00A0EA, "IMOB"),
        SupportedApp("com.imo.android.imolite", "imo Lite", 0xFF00A0EA, "IMOL"),
        SupportedApp("org.telegram.messenger", "Telegram", 0xFF229ED9, "TG"),
        SupportedApp("org.telegram.messenger.web", "Telegram Web", 0xFF229ED9, "TGW"),
        SupportedApp("org.thunderdog.challegram", "Telegram X", 0xFF229ED9, "TGX"),
        SupportedApp("org.telegram.plus", "Telegram Plus", 0xFF229ED9, "TGP"),
        SupportedApp("com.snapchat.android", "Snapchat", 0xFFFFFC00, "SC"),
        SupportedApp("com.discord", "Discord", 0xFF5865F2, "DC"),
        SupportedApp("org.thoughtcrime.securesms", "Signal", 0xFF3A76F0, "SG"),
        SupportedApp("com.viber.voip", "Viber", 0xFF7360F2, "VB"),
        SupportedApp("com.google.android.apps.messaging", "Google Messages", 0xFF1A73E8, "GMSG"),
        SupportedApp("jp.naver.line.android", "LINE", 0xFF06C755, "LN"),
        SupportedApp("jp.naver.line.android.lite", "LINE Lite", 0xFF06C755, "LNL"),
        SupportedApp("com.skype.raider", "Skype", 0xFF00AFF0, "SK"),
        SupportedApp("com.twitter.android", "X / Twitter", 0xFF1DA1F2, "X"),
        SupportedApp("com.tencent.mm", "WeChat", 0xFF07C160, "WC"),
        SupportedApp("com.Slack", "Slack", 0xFF4A154B, "SL"),
        SupportedApp("com.kakao.talk", "KakaoTalk", 0xFFFFE812, "KT"),
        SupportedApp("ch.threema.app", "Threema", 0xFF202020, "TH"),
        SupportedApp("com.example", "MsgVault Test Demo", 0xFF3B82F6, "MV")
    )

    fun getAppByPackage(packageName: String): SupportedApp? {
        return ALL_SUPPORTED_APPS.find { it.packageName == packageName }
    }

    fun getDisplayName(packageName: String, context: Context? = null, fallback: String = "Messaging App"): String {
        ALL_SUPPORTED_APPS.find { it.packageName == packageName }?.let { return it.displayName }
        if (context != null) {
            try {
                val pm = context.packageManager
                val ai = pm.getApplicationInfo(packageName, 0)
                return pm.getApplicationLabel(ai).toString()
            } catch (ignored: Exception) {}
        }
        return fallback
    }

    fun getBrandColor(packageName: String): Color {
        val known = ALL_SUPPORTED_APPS.find { it.packageName == packageName }
        if (known != null) return Color(known.brandColorHex)
        val palette = listOf(
            0xFF3B82F6, 0xFF10B981, 0xFF8B5CF6, 0xFFF59E0B,
            0xFFEF4444, 0xFFEC4899, 0xFF06B6D4, 0xFF14B8A6
        )
        val index = (packageName.hashCode() and 0x7FFFFFFF) % palette.size
        return Color(palette[index])
    }

    fun getShortLabel(packageName: String): String {
        val known = ALL_SUPPORTED_APPS.find { it.packageName == packageName }
        if (known != null) return known.shortLabel
        return packageName.split(".").lastOrNull()?.take(3)?.uppercase() ?: "APP"
    }

    fun getDetectedApps(context: Context, monitoredPackages: Set<String>): List<SupportedApp> {
        val pm = context.packageManager
        return ALL_SUPPORTED_APPS.map { app ->
            val isInstalled = try {
                pm.getPackageInfo(app.packageName, 0)
                true
            } catch (e: Exception) {
                app.packageName == context.packageName
            }
            app.copy(
                isInstalled = isInstalled,
                isMonitored = monitoredPackages.contains(app.packageName)
            )
        }
    }
}
