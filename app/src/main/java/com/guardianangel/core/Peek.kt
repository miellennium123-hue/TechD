package com.guardianangel.core

import com.guardianangel.data.AppLists
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.PeekRecord

/** What she saw you doing when she peeked. Each kind has its own lines. */
enum class PeekKind(val line: Line) {
    HOME(Line.PEEK_HOME),
    SOCIAL(Line.PEEK_SOCIAL),
    VIDEO(Line.PEEK_VIDEO),
    GAME(Line.PEEK_GAME),
    CHAT(Line.PEEK_CHAT),
    BROWSER(Line.PEEK_BROWSER),
    OTHER(Line.PEEK_OTHER),
}

/**
 * She peeks (round 60): about every 5 minutes she captures your screen into her private gallery and
 * comments on what you were doing. Pure Kotlin, tested in PeekTest. The capture itself is in her
 * watch (Android 11 or later). Screenshots never leave the phone.
 */
object Peek {
    const val EVERY_MINUTES = 5

    /** She keeps this many peeks. Older ones (and their screenshots) are deleted. */
    const val KEEP = 100

    /** Android's app categories (ApplicationInfo.CATEGORY_*), copied so this stays pure. */
    const val CATEGORY_GAME = 0
    const val CATEGORY_AUDIO = 1
    const val CATEGORY_VIDEO = 2
    const val CATEGORY_SOCIAL = 4

    /** Messaging apps Android doesn't tag (it has no messaging category). */
    val CHAT_APPS: Set<String> = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.thoughtcrime.securesms",
        "com.facebook.orca",
        "com.discord",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "jp.naver.line.android",
        "com.viber.voip",
        "com.kakao.talk",
        "com.tencent.mm",
    )

    val VIDEO_APPS: Set<String> = setOf(
        "com.google.android.youtube",
        "com.netflix.mediaclient",
        "com.amazon.avod.thirdpartyclient",
        "com.disney.disneyplus",
        "tv.twitch.android.app",
        "com.hbo.hbonow",
        "com.wbd.stream",
    )

    /** Due when she's on, She peeks is on, and her last peek was [EVERY_MINUTES] ago. */
    fun due(config: GuardianConfig, lastPeekAt: Long, now: Long): Boolean =
        config.enabled && config.peek.on && now - lastPeekAt >= EVERY_MINUTES * 60_000L

    /**
     * Whether she may look right now. Never with the screen off or locked, never at her own screens,
     * and never while the keyboard is up (so she doesn't catch you typing a password). Never at the
     * phone, system screens or your Always-allowed apps, so banking apps can be kept from her by
     * putting them there.
     */
    fun mayLook(
        pkg: String?,
        config: GuardianConfig,
        ownApp: Boolean,
        screenOn: Boolean,
        locked: Boolean,
        keyboardUp: Boolean,
        launcher: Boolean,
        protectedPackages: Set<String>,
    ): Boolean {
        if (pkg == null || ownApp || !screenOn || locked || keyboardUp) return false
        if (launcher) return true
        return pkg !in config.alwaysAllowed && pkg !in AppLists.NEVER_BLOCK && pkg !in protectedPackages
    }

    /** What you were doing. [category] is the app's Android category, or -1 when it has none. */
    fun kind(pkg: String, category: Int, launcher: Boolean, browser: Boolean): PeekKind = when {
        launcher -> PeekKind.HOME
        pkg in CHAT_APPS -> PeekKind.CHAT
        pkg in VIDEO_APPS || category == CATEGORY_VIDEO -> PeekKind.VIDEO
        pkg in AppLists.SOCIAL_MEDIA || category == CATEGORY_SOCIAL -> PeekKind.SOCIAL
        category == CATEGORY_GAME -> PeekKind.GAME
        browser -> PeekKind.BROWSER
        else -> PeekKind.OTHER
    }

    /** Adds a peek, newest last. Returns the list she keeps and the ones whose screenshots go. */
    fun add(peeks: List<PeekRecord>, record: PeekRecord): Pair<List<PeekRecord>, List<PeekRecord>> {
        val all = peeks + record
        val drop = (all.size - KEEP).coerceAtLeast(0)
        return all.drop(drop) to all.take(drop)
    }
}
