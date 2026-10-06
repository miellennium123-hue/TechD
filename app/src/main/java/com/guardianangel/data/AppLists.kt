package com.guardianangel.data

object AppLists {
    /** Instagram, TikTok, X, Snapchat, Facebook, Reddit, YouTube. */
    val SOCIAL_MEDIA: Set<String> = setOf(
        "com.instagram.android",
        "com.instagram.lite",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.twitter.android",
        "com.twitter.android.lite",
        "com.snapchat.android",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.reddit.frontpage",
        "com.google.android.youtube",
    )

    /** Prefilled Always-allowed list. Banking apps differ per person, so they are picked in Settings. */
    val DEFAULT_ALLOWED: Set<String> = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.dialer",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.google.android.contacts",
        "com.samsung.android.app.contacts",
        "com.google.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.google.android.apps.maps",
    )

    /**
     * Never blocked, whatever the settings say: phone and emergency calls, system UI,
     * system Settings (so she can always be switched off) and permission dialogs.
     * The launcher, keyboard, default dialer and this app are added at runtime.
     */
    val NEVER_BLOCK: Set<String> = setOf(
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.emergency",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.dialer",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
    )
}
