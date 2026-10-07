package com.guardianangel.core

import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.SessionSettings
import kotlin.random.Random

/** What a clip shows. [tag] is part of its file name. */
enum class ClipKind(val label: String, val tag: String) {
    RUIN("Ruin", "ruin"),
    EDGE("Edge", "edge"),
    CBT("CBT", "cbt"),
}

/** One of her videos: its file name, when she filmed it, and what it shows. */
data class ClipInfo(val name: String, val at: Long, val kind: ClipKind)

/**
 * Her videos (round 77): what she films during sessions (ruins always, edges and CBT if you let her),
 * where they're kept (private storage, apart from proof photos), and when she makes you watch one.
 * Pure Kotlin, tested in ClipsTest. The files themselves are in core/Files (SessionClips).
 */
object Clips {
    const val DIR = "clips"

    /** She keeps this many. Older clips are deleted. */
    const val KEEP = 40

    /** At a check-in that makes you watch, you have this long to open it. */
    const val WATCH_DUE_SECONDS = 60

    /** How often she makes you watch: per session, per check-in, and per time her lock screen opens. */
    const val SESSION_CHANCE = 0.5
    const val CHECK_IN_CHANCE = 0.25
    const val LOCK_SCREEN_CHANCE = 1.0 / 3

    fun name(kind: ClipKind, at: Long): String = "clip_${at}_${kind.tag}.mp4"

    /** Reads a clip's file name back, or null if it isn't one of hers. */
    fun parse(name: String): ClipInfo? {
        val parts = name.removeSuffix(".mp4").split('_')
        if (!name.endsWith(".mp4") || parts.size != 3 || parts[0] != "clip") return null
        val at = parts[1].toLongOrNull() ?: return null
        val kind = ClipKind.entries.firstOrNull { it.tag == parts[2] } ?: return null
        return ClipInfo(name, at, kind)
    }

    /** A ruin clip from before round 77 (kept with the proof photos), as the name it moves to. */
    fun fromLegacy(name: String): String? {
        val match = Regex("""proof_(\d+)_ruin\.mp4""").matchEntire(name) ?: return null
        return name(ClipKind.RUIN, match.groupValues[1].toLong())
    }

    /** Newest first. */
    fun sorted(clips: List<ClipInfo>): List<ClipInfo> = clips.sortedByDescending { it.at }

    /** The clips past the newest [KEEP], to delete. */
    fun overflow(clips: List<ClipInfo>): List<ClipInfo> = sorted(clips).drop(KEEP)

    /**
     * What she films during a session step, or null. Ruins always; edges and CBT with "She films
     * edges and CBT" on.
     */
    fun films(kind: StepKind, settings: SessionSettings): ClipKind? = when (kind) {
        StepKind.RUIN -> ClipKind.RUIN
        StepKind.EDGE -> ClipKind.EDGE.takeIf { settings.filmTasks }
        StepKind.CBT -> ClipKind.CBT.takeIf { settings.filmTasks }
        else -> null
    }

    /** One clip for her to play: any of them, a little more often a ruin. */
    fun pick(clips: List<ClipInfo>, random: Random): ClipInfo? {
        if (clips.isEmpty()) return null
        val ruins = clips.filter { it.kind == ClipKind.RUIN }
        return if (ruins.isNotEmpty() && random.nextDouble() < 0.5) ruins[random.nextInt(ruins.size)] else clips[random.nextInt(clips.size)]
    }

    /** A session gets a "watch yourself" break: the setting is on, you have clips, and [roll] (0 until 1) says so. */
    fun inSession(settings: SessionSettings, clips: Int, roll: Double): Boolean =
        settings.watchInSessions && clips > 0 && roll < SESSION_CHANCE

    /** Her bedtime or caught screen opens with a clip: she's on, the setting is on, you have clips, and [roll] says so. */
    fun onLockScreen(config: GuardianConfig, clips: Int, roll: Double): Boolean =
        config.enabled && config.session.watchOnLockScreens && clips > 0 && roll < LOCK_SCREEN_CHANCE

    /** A check-in can make you watch: the setting is on and you have clips. Rules.checkInAction decides. */
    fun atCheckIns(config: GuardianConfig, clips: Int): Boolean = config.session.watchAtCheckIns && clips > 0
}
