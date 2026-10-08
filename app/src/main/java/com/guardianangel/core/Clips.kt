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
    /** Round 79: her snapshot of your face at your edge tap. Round 95: she stopped taking them; old ones still show. */
    FACE("Edge face", "face", "jpg"),
    ;

    val video: Boolean get() = ext == "mp4"
}

/** One of her videos: its file name, when she filmed it, and what it shows. */
data class ClipInfo(val name: String, val at: Long, val kind: ClipKind)

/** Round 95: one recording in a session. [id] is the command it started on; it can run across several. */
data class ClipTake(val id: Int, val kind: ClipKind)

/**
 * One take as the camera films it (round 95, moved here in round 98 so it's tested). It runs across
 * commands while [id] stays the same. [startedAt]: when it began. [markAt]: the moment that matters
 * (your edge tap, or hands off in a ruin); the saved clip starts [Clips.BEFORE_MS] before it, 0 keeps it
 * all. [stopAt]: when it stops on its own (0: when the session moves on). [number] and [caption]: hers.
 */
data class ClipRequest(
    val id: Int,
    val kind: ClipKind,
    val startedAt: Long = 0,
    val markAt: Long = 0,
    val stopAt: Long = 0,
    val number: Int = 0,
    val caption: String = "",
)

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
     * How often she makes you watch (round 78): about every other check-in and time her lock screen
     * opens (at random, so you never know which). Round 95: never inside sessions.
     */
    const val CHECK_IN_CHANCE = 0.5
    const val LOCK_SCREEN_CHANCE = 0.5

    /** Round 92: after a full-screen clip ends, this long before she can play another. */
    const val COOLDOWN_MS = 5 * MINUTE
    /** Round 92: a full-screen clip stops holding the phone after this long, in case it never ends. */
    const val FORCED_MAX_MS = 10 * MINUTE

    /**
     * Round 98: every edge and ruin clip keeps this long before the moment that matters. An edge clip
     * runs [EDGE_AFTER_MS] past your tap; a ruin clip runs [RUIN_AFTER_MS] past hands off (the whole ruin).
     * CBT is filmed whole.
     */
    const val BEFORE_MS = 10_000L
    const val EDGE_AFTER_MS = 8_000L
    const val RUIN_AFTER_MS = (Session.RUIN_CLIP_SECONDS - 1) * 1_000L
    /** Round 95: she only cuts a clip when there's at least this much to cut. */
    const val MIN_TRIM_MS = 1_000L

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

    /**
     * Round 95: what she's filming at command [index]. A ruin is one take from its edge, through her
     * countdown, to the ruin (always filmed). Otherwise [films] decides, one take per command.
     */
    fun take(steps: List<Step>, index: Int, settings: SessionSettings): ClipTake? {
        ruinStart(steps, index)?.let { return ClipTake(it, ClipKind.RUIN) }
        val kind = steps.getOrNull(index)?.let { films(it.kind, settings) } ?: return null
        return ClipTake(index, kind)
    }

    /** Round 95: if [index] is part of a ruin (its edge, countdown or the ruin itself), the edge's index. */
    fun ruinStart(steps: List<Step>, index: Int): Int? {
        fun kind(i: Int) = steps.getOrNull(i)?.kind
        val start = when (kind(index)) {
            StepKind.EDGE -> index
            StepKind.COUNTDOWN -> index - 1
            StepKind.RUIN -> index - 2
            else -> return null
        }
        return start.takeIf {
            kind(it) == StepKind.EDGE && kind(it + 1) == StepKind.COUNTDOWN && kind(it + 2) == StepKind.RUIN
        }
    }

    /**
     * Round 98: what she's filming once the session reaches command [index] at [now], given the take
     * so far ([prev]). Leaving an edge marks your tap and keeps filming [EDGE_AFTER_MS]. A ruin's take
     * starts at its edge (so there's enough before) and marks hands off when the ruin starts, filming the
     * whole ruin. A new take replaces one still running. [ruinNumber]: which ruin this will be.
     */
    fun nextTake(prev: ClipRequest?, steps: List<Step>, index: Int, settings: SessionSettings, now: Long, ruinNumber: Int): ClipRequest? {
        val step = steps.getOrNull(index) ?: return null
        val marked = prev?.let { p ->
            if (p.kind == ClipKind.EDGE && p.markAt == 0L && p.id == index - 1 && steps.getOrNull(index - 1)?.kind == StepKind.EDGE) {
                p.copy(
                    markAt = now,
                    stopAt = now + EDGE_AFTER_MS,
                    caption = caption(ClipKind.EDGE, p.number, ((now - p.startedAt) / 1_000).toInt()),
                )
            } else {
                p
            }
        }
        val wanted = take(steps, index, settings)
        val handsOff = step.kind == StepKind.RUIN
        return when {
            wanted != null && marked != null && marked.id == wanted.id ->
                if (handsOff) marked.copy(markAt = now, stopAt = now + RUIN_AFTER_MS) else marked
            wanted != null -> {
                val edgesSoFar = steps.take(index + 1).count { it.kind == StepKind.EDGE }
                val number = when (wanted.kind) {
                    ClipKind.RUIN -> ruinNumber
                    ClipKind.EDGE -> edgesSoFar
                    else -> 0
                }
                ClipRequest(
                    id = wanted.id,
                    kind = wanted.kind,
                    startedAt = now,
                    markAt = if (handsOff) now else 0,
                    stopAt = if (handsOff) now + RUIN_AFTER_MS else 0,
                    number = number,
                    caption = when (wanted.kind) {
                        ClipKind.RUIN -> caption(ClipKind.RUIN, number, edges = edgesSoFar)
                        ClipKind.CBT -> caption(ClipKind.CBT, 0, reps = step.reps)
                        else -> caption(wanted.kind, number)
                    },
                )
            }
            // An edge's last seconds after your tap.
            marked != null && marked.markAt > 0 && marked.stopAt > now -> marked
            else -> null
        }
    }

    /**
     * Round 95: where to cut the start of a clip so it begins [BEFORE_MS] before its mark ([markAt]).
     * [durationMs] is the whole recording, which ended at [stoppedAt]. 0 keeps it all.
     */
    fun trimStartMs(durationMs: Long, stoppedAt: Long, markAt: Long): Long {
        if (markAt <= 0 || durationMs <= 0) return 0
        val keep = (stoppedAt - markAt).coerceAtLeast(0) + BEFORE_MS
        return (durationMs - keep).coerceAtLeast(0).takeIf { it >= MIN_TRIM_MS } ?: 0
    }

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
