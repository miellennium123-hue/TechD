package com.guardianangel.core

import com.guardianangel.data.ForcedClip
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.SessionSettings
import kotlin.random.Random

/** What a clip shows. [tag] and [ext] make its file name. [FACE] (round 79) is a photo, not a video. */
enum class ClipKind(val label: String, val tag: String, val ext: String = "mp4") {
    RUIN("Ruin", "ruin"),
    EDGE("Edge", "edge"),
    CBT("CBT", "cbt"),
    /** Round 79: her snapshot of your face the moment you tap "I'm at the edge". */
    FACE("Edge face", "face", "jpg"),
    ;

    val video: Boolean get() = ext == "mp4"
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

    /**
     * How often she makes you watch (round 78): every session, and about every other check-in and
     * time her lock screen opens (at random, so you never know which).
     */
    const val SESSION_CHANCE = 1.0
    const val CHECK_IN_CHANCE = 0.5
    const val LOCK_SCREEN_CHANCE = 0.5

    /** Round 92: after a full-screen clip ends, this long before she can play another. */
    const val COOLDOWN_MS = 5 * MINUTE
    /** Round 92: a full-screen clip stops holding the phone after this long, in case it never ends. */
    const val FORCED_MAX_MS = 10 * MINUTE

    /** Her 5 minute cooldown since the last full-screen clip is over. */
    fun cooldownOver(lastClipAt: Long, now: Long): Boolean = now - lastClipAt >= COOLDOWN_MS

    /** A full-screen clip is holding the phone right now. */
    fun forcing(forced: ForcedClip?, now: Long): Boolean = forced != null && now - forced.since < FORCED_MAX_MS

    /**
     * Round 92: while her clip plays, she sends you straight back to it from anywhere: the home screen
     * and every app, Always-allowed ones too. Never from her own screens, the phone, Settings or system
     * screens ([exempt]), so calls and Quit for now always work.
     */
    fun pullsBack(forced: ForcedClip?, now: Long, ownApp: Boolean, launcher: Boolean, exempt: Boolean): Boolean =
        forcing(forced, now) && !ownApp && (launcher || !exempt)

    fun name(kind: ClipKind, at: Long): String = "clip_${at}_${kind.tag}.${kind.ext}"

    /** Reads a clip's file name back, or null if it isn't one of hers. */
    fun parse(name: String): ClipInfo? {
        val ext = name.substringAfterLast('.', "")
        val parts = name.substringBeforeLast('.').split('_')
        if (parts.size != 3 || parts[0] != "clip") return null
        val at = parts[1].toLongOrNull() ?: return null
        val kind = ClipKind.entries.firstOrNull { it.tag == parts[2] && it.ext == ext } ?: return null
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

    /** One clip for her to play: any video (never an edge photo), a little more often a ruin. */
    fun pick(all: List<ClipInfo>, random: Random): ClipInfo? {
        val clips = all.filter { it.kind.video }
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

    /** Round 79: her ruin reel, every ruin clip back to back, oldest first. */
    fun reel(clips: List<ClipInfo>): List<ClipInfo> = clips.filter { it.kind == ClipKind.RUIN }.sortedBy { it.at }

    /**
     * Round 79: her caption on a clip she just saved. [number]: which ruin this is, or which edge of the
     * session. [seconds]: how long that edge took. [reps]: CBT count.
     */
    fun caption(kind: ClipKind, number: Int, seconds: Int = 0, reps: Int = 0, edges: Int = 0): String = when (kind) {
        ClipKind.RUIN -> "Ruin #$number" + if (edges > 0) " · after $edges ${if (edges == 1) "edge" else "edges"}" else ""
        ClipKind.EDGE -> "Edge $number" + when {
            seconds <= 0 -> ""
            Session.edgeFast(seconds) -> " · ${seconds}s to the edge. Too quick"
            else -> " · ${seconds}s to the edge"
        }
        ClipKind.CBT -> "CBT · $reps ${if (reps == 1) "slap" else "slaps"}"
        ClipKind.FACE -> "Your face at edge $number"
    }

    /** Captions of clips that still exist. */
    fun keepCaptions(captions: Map<String, String>, names: Set<String>): Map<String, String> = captions.filterKeys { it in names }

    /** A check-in can make you watch: the setting is on and you have clips. Rules.checkInAction decides. */
    fun atCheckIns(config: GuardianConfig, clips: Int): Boolean = config.session.watchAtCheckIns && clips > 0
}
