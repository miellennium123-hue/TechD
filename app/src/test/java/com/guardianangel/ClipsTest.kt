package com.guardianangel

import com.guardianangel.core.CheckInAction
import com.guardianangel.core.CheckInRolls
import com.guardianangel.core.ClipInfo
import com.guardianangel.core.ClipKind
import com.guardianangel.core.ClipTake
import com.guardianangel.core.Clips
import com.guardianangel.core.LockGuard
import com.guardianangel.core.Rules
import com.guardianangel.core.Step
import com.guardianangel.core.StepKind
import com.guardianangel.data.ClipSource
import com.guardianangel.data.ForcedClip
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.QuietHoursSettings
import com.guardianangel.data.SessionSettings
import com.guardianangel.data.WatchRequest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Her videos (round 77): names, what she films, keeping 40, and when she makes you watch. */
class ClipsTest {
    private val noon = 12 * 60
    private val on = GuardianConfig(enabled = true)

    @Test
    fun namesRoundTrip() {
        ClipKind.entries.forEach { kind ->
            val name = Clips.name(kind, 1234L)
            assertEquals(ClipInfo(name, 1234L, kind), Clips.parse(name))
        }
        assertNull(Clips.parse("proof_1_ruin.mp4"))
        assertNull(Clips.parse("clip_x_ruin.mp4"))
        assertNull(Clips.parse("clip_1_nope.mp4"))
        assertNull(Clips.parse("clip_1_ruin.jpg"))
    }

    @Test
    fun oldRuinClipsMoveOver() {
        assertEquals(Clips.name(ClipKind.RUIN, 1700000000000L), Clips.fromLegacy("proof_1700000000000_ruin.mp4"))
        assertNull(Clips.fromLegacy("proof_1700000000000.jpg"))
        assertNull(Clips.fromLegacy("peek_1.jpg"))
    }

    @Test
    fun keepsTheNewest() {
        val clips = (1..(Clips.KEEP + 3)).map { ClipInfo(Clips.name(ClipKind.EDGE, it.toLong()), it.toLong(), ClipKind.EDGE) }.shuffled(Random(1))
        val gone = Clips.overflow(clips)
        assertEquals(3, gone.size)
        assertEquals(setOf(1L, 2L, 3L), gone.map { it.at }.toSet())
        assertEquals(Clips.KEEP + 3L, Clips.sorted(clips).first().at)
    }

    @Test
    fun whatSheFilms() {
        val films = SessionSettings()
        val ruinsOnly = SessionSettings(filmTasks = false)
        assertEquals(ClipKind.RUIN, Clips.films(StepKind.RUIN, films))
        assertEquals(ClipKind.RUIN, Clips.films(StepKind.RUIN, ruinsOnly))
        assertEquals(ClipKind.EDGE, Clips.films(StepKind.EDGE, films))
        assertEquals(ClipKind.CBT, Clips.films(StepKind.CBT, films))
        assertNull(Clips.films(StepKind.EDGE, ruinsOnly))
        assertNull(Clips.films(StepKind.CBT, ruinsOnly))
        assertNull(Clips.films(StepKind.STROKE, films))
    }

    @Test
    fun aRuinIsOneTakeFromItsEdge() {
        // Round 95: edge, countdown and ruin are one ruin clip, started at the edge, even with edges not filmed.
        val ruinsOnly = SessionSettings(filmTasks = false)
        val steps = listOf(
            Step(StepKind.STROKE, 30, bpm = 100),
            Step(StepKind.EDGE, 180, bpm = 130),
            Step(StepKind.COUNTDOWN, 5, bpm = 160),
            Step(StepKind.RUIN, 20),
            Step(StepKind.COOL, 45),
        )
        assertNull(Clips.take(steps, 0, ruinsOnly))
        (1..3).forEach { assertEquals(ClipTake(1, ClipKind.RUIN), Clips.take(steps, it, ruinsOnly)) }
        assertNull(Clips.take(steps, 4, ruinsOnly))
        assertEquals(1, Clips.ruinStart(steps, 3))
        // A plain edge (no ruin after it) is its own edge clip, only with edges filmed.
        val edges = listOf(Step(StepKind.EDGE, 180), Step(StepKind.EDGE_HOLD, 20), Step(StepKind.CBT, 20))
        assertEquals(ClipTake(0, ClipKind.EDGE), Clips.take(edges, 0, SessionSettings()))
        assertNull(Clips.take(edges, 0, ruinsOnly))
        assertNull(Clips.take(edges, 1, SessionSettings()))
        assertEquals(ClipTake(2, ClipKind.CBT), Clips.take(edges, 2, SessionSettings()))
        assertNull(Clips.ruinStart(edges, 0))
        // A countdown that isn't a ruin's isn't filmed.
        val countdown = listOf(Step(StepKind.COUNTDOWN, 5), Step(StepKind.STROKE, 20))
        assertNull(Clips.take(countdown, 0, SessionSettings()))
    }

    @Test
    fun clipsKeepTenSecondsBeforeTheMoment() {
        val stopped = 1_000_000L
        // Edge: 60 seconds filmed, stopped 8 seconds after the tap: keep the last 18.
        assertEquals(42_000L, Clips.trimStartMs(60_000, stopped, stopped - Clips.EDGE_AFTER_MS))
        // Ruin: stopped 19 seconds after hands off, so keep 29.
        assertEquals(31_000L, Clips.trimStartMs(60_000, stopped, stopped - Clips.RUIN_AFTER_MS))
        // Too short to cut, or no mark: keep it all.
        assertEquals(0L, Clips.trimStartMs(15_000, stopped, stopped - Clips.EDGE_AFTER_MS))
        assertEquals(0L, Clips.trimStartMs(18_500, stopped, stopped - Clips.EDGE_AFTER_MS))
        assertEquals(0L, Clips.trimStartMs(60_000, stopped, 0))
        assertEquals(0L, Clips.trimStartMs(0, stopped, stopped - 5_000))
    }

    @Test
    fun anEdgeIsFilmedUntilEightSecondsAfterYourTap() {
        val steps = listOf(
            Step(StepKind.STROKE, 30, bpm = 100),
            Step(StepKind.EDGE, 180, bpm = 130),
            Step(StepKind.EDGE_HOLD, 20),
            Step(StepKind.STROKE, 30, bpm = 100),
        )
        val s = SessionSettings()
        assertNull(Clips.nextTake(null, steps, 0, s, 1_000, 1))
        // The edge starts a take, with no mark yet and no stop time.
        val edge = Clips.nextTake(null, steps, 1, s, 10_000, 1)!!
        assertEquals(ClipKind.EDGE, edge.kind)
        assertEquals(1, edge.id)
        assertEquals(0L, edge.markAt)
        assertEquals(0L, edge.stopAt)
        assertEquals(1, edge.number)
        // Your tap 34 seconds in: marked, filming 8 more seconds, her caption has your time.
        val tapped = Clips.nextTake(edge, steps, 2, s, 44_000, 1)!!
        assertEquals(1, tapped.id)
        assertEquals(44_000L, tapped.markAt)
        assertEquals(44_000L + Clips.EDGE_AFTER_MS, tapped.stopAt)
        assertEquals(Clips.caption(ClipKind.EDGE, 1, 34), tapped.caption)
        // The next command before those 8 seconds are up keeps it; after, it's done.
        assertEquals(tapped, Clips.nextTake(tapped, steps, 3, s, 48_000, 1))
        assertNull(Clips.nextTake(tapped, steps, 3, s, 53_000, 1))
        // Edges not filmed: nothing.
        assertNull(Clips.nextTake(null, steps, 1, SessionSettings(filmTasks = false), 10_000, 1))
    }

    @Test
    fun aRuinKeepsTenSecondsBeforeHandsOffAndTheWholeRuin() {
        val steps = listOf(
            Step(StepKind.STROKE, 30, bpm = 100),
            Step(StepKind.EDGE, 180, bpm = 130),
            Step(StepKind.COUNTDOWN, 5, bpm = 160),
            Step(StepKind.RUIN, 20),
            Step(StepKind.COOL, 45),
        )
        val s = SessionSettings(filmTasks = false)
        val start = Clips.nextTake(null, steps, 1, s, 10_000, 3)!!
        assertEquals(ClipKind.RUIN, start.kind)
        assertEquals(3, start.number)
        // Leaving the edge doesn't mark a ruin: the countdown keeps the same take going.
        val counting = Clips.nextTake(start, steps, 2, s, 40_000, 3)!!
        assertEquals(start, counting)
        // Hands off: marked now, filming the whole ruin.
        val ruin = Clips.nextTake(counting, steps, 3, s, 45_000, 3)!!
        assertEquals(1, ruin.id)
        assertEquals(45_000L, ruin.markAt)
        assertEquals(45_000L + Clips.RUIN_AFTER_MS, ruin.stopAt)
        assertTrue(Clips.RUIN_AFTER_MS in 10_000L..20_000L)
        // She cuts it to start 10 seconds before hands off: the end of your edge and her countdown.
        val stoppedAt = ruin.stopAt
        val filmed = stoppedAt - start.startedAt
        assertEquals(filmed - Clips.RUIN_AFTER_MS - Clips.BEFORE_MS, Clips.trimStartMs(filmed, stoppedAt, ruin.markAt))
        assertNull(Clips.nextTake(ruin, steps, 4, s, 65_000, 3))
    }

    @Test
    fun aStraightRuinIsOneTakeFromItsStrokes() {
        // Round 99: rush then ruin (quickshot, owed ruin): one ruin take from the rush, marked at hands off.
        val steps = listOf(
            Step(StepKind.FASTER, 10, bpm = 180),
            Step(StepKind.RUSH, 120, bpm = 170, bpmTo = 200),
            Step(StepKind.RUIN, 20),
        )
        val s = SessionSettings(filmTasks = false)
        assertNull(Clips.take(steps, 0, s))
        assertEquals(ClipTake(1, ClipKind.RUIN), Clips.take(steps, 1, s))
        assertEquals(ClipTake(1, ClipKind.RUIN), Clips.take(steps, 2, s))
        val rush = Clips.nextTake(null, steps, 1, s, 10_000, 1)!!
        assertEquals(0L, rush.markAt)
        val ruin = Clips.nextTake(rush, steps, 2, s, 50_000, 1)!!
        assertEquals(1, ruin.id)
        assertEquals(50_000L, ruin.markAt)
        assertEquals(50_000L + Clips.RUIN_AFTER_MS, ruin.stopAt)
        // A rush with no ruin after it isn't filmed as one.
        assertNull(Clips.ruinStart(listOf(Step(StepKind.RUSH, 120), Step(StepKind.COOL, 45)), 0))
    }

    @Test
    fun herReleaseIsFilmedFromTheCountdown() {
        // Round 104: countdown then finish is one release take, always filmed, marked at her command.
        val steps = listOf(
            Step(StepKind.STROKE, 30, bpm = 120),
            Step(StepKind.COUNTDOWN, 10),
            Step(StepKind.FINISH, 300),
            Step(StepKind.COOL, 45),
        )
        val s = SessionSettings(filmTasks = false)
        assertEquals(ClipTake(1, ClipKind.RELEASE), Clips.take(steps, 1, s))
        assertEquals(ClipTake(1, ClipKind.RELEASE), Clips.take(steps, 2, s))
        val counting = Clips.nextTake(null, steps, 1, s, 10_000, 1, releaseNumber = 4)!!
        assertEquals(ClipKind.RELEASE, counting.kind)
        assertEquals("Release #4", counting.caption)
        assertEquals(0L, counting.markAt)
        val command = Clips.nextTake(counting, steps, 2, s, 20_000, 1, releaseNumber = 4)!!
        assertEquals(20_000L, command.markAt)
        assertEquals(20_000L + Clips.RELEASE_AFTER_MS, command.stopAt)
        // A countdown before anything else isn't a release.
        assertNull(Clips.releaseStart(listOf(Step(StepKind.COUNTDOWN, 5), Step(StepKind.EDGE, 120)), 0))
        assertEquals("release", ClipKind.RELEASE.tag)
        assertTrue(ClipKind.RELEASE.video)
    }

    @Test
    fun cbtIsFilmedWhole() {
        val steps = listOf(Step(StepKind.CBT, 20, bpm = 20, reps = 5), Step(StepKind.STROKE, 30, bpm = 100))
        val cbt = Clips.nextTake(null, steps, 0, SessionSettings(), 1_000, 1)!!
        assertEquals(ClipKind.CBT, cbt.kind)
        assertEquals(0L, cbt.markAt)
        assertEquals(Clips.caption(ClipKind.CBT, 0, reps = 5), cbt.caption)
        assertNull(Clips.nextTake(cbt, steps, 1, SessionSettings(), 21_000, 1))
    }

    @Test
    fun picksOneOfYours() {
        assertNull(Clips.pick(emptyList(), Random(1)))
        val clips = listOf(ClipInfo("a", 1, ClipKind.EDGE), ClipInfo("b", 2, ClipKind.RUIN))
        repeat(20) { assertTrue(Clips.pick(clips, Random(it)) in clips) }
    }

    @Test
    fun whenSheMakesYouWatch() {
        val s = SessionSettings()
        assertTrue(Clips.onLockScreen(on, clips = 1, roll = 0.0))
        assertTrue(Clips.onLockScreen(on, clips = 1, roll = 0.4))
        assertFalse(Clips.onLockScreen(on, clips = 1, roll = 0.6))
        assertFalse(Clips.onLockScreen(on.copy(enabled = false), clips = 1, roll = 0.0))
        assertFalse(Clips.onLockScreen(on.copy(session = s.copy(watchOnLockScreens = false)), clips = 1, roll = 0.0))
    }

    @Test
    fun checkInsCanSendAClip() {
        val sure = CheckInRolls(task = 0.99, summons = 0.99, proof = 0.99, watch = 0.0)
        val quietOff = on.copy(quietHours = QuietHoursSettings(on = false))
        assertEquals(CheckInAction.WATCH, Rules.checkInAction(quietOff, GuardianState(), noon, true, sure, clips = 2))
        // No clips, the setting off, one already waiting, or no notifications: no clip.
        assertEquals(CheckInAction.PLAIN, Rules.checkInAction(quietOff, GuardianState(), noon, true, sure, clips = 0))
        val off = quietOff.copy(session = SessionSettings(watchAtCheckIns = false))
        assertEquals(CheckInAction.PLAIN, Rules.checkInAction(off, GuardianState(), noon, true, sure, clips = 2))
        val waiting = GuardianState(watch = WatchRequest("clip_1_ruin.mp4", 0, 60_000))
        assertEquals(CheckInAction.PLAIN, Rules.checkInAction(quietOff, waiting, noon, true, sure, clips = 2))
        assertEquals(CheckInAction.PLAIN, Rules.checkInAction(quietOff, GuardianState(), noon, false, sure, clips = 2))
        // Never in quiet time, and never a minute before it starts.
        val quiet = on.copy(quietHours = QuietHoursSettings(on = true, startMinute = noon + 1, endMinute = noon + 60))
        assertEquals(CheckInAction.PLAIN, Rules.checkInAction(quiet, GuardianState(), noon, true, sure, clips = 2))
        assertEquals(CheckInAction.QUIET, Rules.checkInAction(quiet, GuardianState(), noon + 5, true, sure, clips = 2))
        // About every other check-in, and it comes before a task or summons.
        val busy = quietOff.copy(tasksOn = true, showsUpOn = true)
        val all = CheckInRolls(task = 0.0, summons = 0.0, proof = 0.0, watch = 0.4)
        assertEquals(CheckInAction.WATCH, Rules.checkInAction(busy, GuardianState(), noon, true, all, clips = 2))
        assertEquals(CheckInAction.TASK, Rules.checkInAction(busy, GuardianState(), noon, true, all.copy(watch = 0.6), clips = 2))
        // The old default rolls never send one, so earlier check-ins stay as they were.
        val old = CheckInRolls(task = 0.99, summons = 0.99, proof = 0.99)
        assertEquals(CheckInAction.PLAIN, Rules.checkInAction(quietOff, GuardianState(), noon, true, old, clips = 2))
    }

    @Test
    fun lockGuardOnSwitchingWatchingOff() {
        val strict = on.copy(lockGuard = true)
        assertTrue(LockGuard.loosens(strict, strict.copy(session = SessionSettings(watchAtCheckIns = false))))
        assertTrue(LockGuard.loosens(strict, strict.copy(session = SessionSettings(watchOnLockScreens = false))))
        assertFalse(LockGuard.loosens(strict, strict.copy(session = SessionSettings(filmTasks = false))))
    }

    @Test
    fun olderSavesLoad() {
        val json = Json { ignoreUnknownKeys = true }
        // Old session settings had camera and motion checks; they're ignored now.
        val config = json.decodeFromString(GuardianConfig.serializer(), """{"session":{"on":true,"camera":false,"motionChecks":true,"motionSensitivity":"LOW"}}""")
        assertTrue(config.session.on)
        assertTrue(config.session.filmTasks)
        assertTrue(config.session.watchAtCheckIns)
        val state = json.decodeFromString(GuardianState.serializer(), """{"sessions":[{"at":1,"minutes":10,"ending":"RUINED","caged":false,"caught":2,"skipped":0,"outcome":"RUINED"}]}""")
        assertEquals(1, state.sessions.size)
        assertNull(state.watch)
    }

    // ---- Round 79 ---------------------------------------------------------------------------

    @Test
    fun edgeFacesArePhotosSheNeverPlays() {
        val face = Clips.name(ClipKind.FACE, 5L)
        assertTrue(face.endsWith(".jpg"))
        assertEquals(ClipKind.FACE, Clips.parse(face)?.kind)
        assertFalse(ClipKind.FACE.video)
        assertNull(Clips.parse("clip_5_face.mp4"))
        val onlyFace = listOf(ClipInfo(face, 5, ClipKind.FACE))
        assertNull(Clips.pick(onlyFace, Random(1)))
        val mixed = onlyFace + ClipInfo("b", 6, ClipKind.EDGE)
        repeat(20) { assertEquals(ClipKind.EDGE, Clips.pick(mixed, Random(it))?.kind) }
    }

    @Test
    fun herCaptions() {
        assertEquals("Ruin #12 · after 6 edges", Clips.caption(ClipKind.RUIN, 12, edges = 6))
        assertEquals("Ruin #1", Clips.caption(ClipKind.RUIN, 1))
        assertEquals("Edge 3 · 25s to the edge. Too quick", Clips.caption(ClipKind.EDGE, 3, seconds = 25))
        assertEquals("Edge 3 · 90s to the edge", Clips.caption(ClipKind.EDGE, 3, seconds = 90))
        assertEquals("CBT · 8 slaps", Clips.caption(ClipKind.CBT, 0, reps = 8))
        assertEquals("Your face at edge 2", Clips.caption(ClipKind.FACE, 2))
        val kept = Clips.keepCaptions(mapOf("a" to "x", "gone" to "y"), setOf("a", "b"))
        assertEquals(mapOf("a" to "x"), kept)
    }

    @Test
    fun ruinReelOldestFirst() {
        val clips = listOf(
            ClipInfo("r2", 20, ClipKind.RUIN),
            ClipInfo("e", 15, ClipKind.EDGE),
            ClipInfo("r1", 10, ClipKind.RUIN),
        )
        assertEquals(listOf("r1", "r2"), Clips.reel(clips).map { it.name })
    }

    // ---- Round 92 ---------------------------------------------------------------------------

    @Test
    fun fiveMinutesBetweenHerClips() {
        val ended = 1_000_000L
        assertFalse(Clips.cooldownOver(ended, ended + 4 * 60_000))
        assertTrue(Clips.cooldownOver(ended, ended + 5 * 60_000))
        assertTrue(Clips.cooldownOver(0, 10 * 60_000))
        // Check-ins wait out her cooldown, and never send one while another is playing.
        val quietOff = on.copy(quietHours = QuietHoursSettings(on = false))
        val sure = CheckInRolls(task = 0.99, summons = 0.99, proof = 0.99, watch = 0.0)
        val recent = GuardianState(lastClipAt = ended)
        assertEquals(CheckInAction.PLAIN, Rules.checkInAction(quietOff, recent, noon, true, sure, clips = 2, now = ended + 60_000))
        assertEquals(CheckInAction.WATCH, Rules.checkInAction(quietOff, recent, noon, true, sure, clips = 2, now = ended + 6 * 60_000))
        val playing = GuardianState(forcedClip = ForcedClip("clip_1_ruin.mp4", ClipSource.LOCK_SCREEN, ended))
        assertEquals(CheckInAction.PLAIN, Rules.checkInAction(quietOff, playing, noon, true, sure, clips = 2, now = ended + 6 * 60_000))
    }

    @Test
    fun herClipHoldsThePhoneUntilItEnds() {
        val since = 1_000_000L
        val forced = ForcedClip("clip_1_ruin.mp4", ClipSource.CHECK_IN, since)
        assertTrue(Clips.forcing(forced, since + 60_000))
        assertFalse(Clips.forcing(null, since))
        // A clip that never ends stops holding the phone after 10 minutes.
        assertFalse(Clips.forcing(forced, since + Clips.FORCED_MAX_MS))
        // Back to it from the home screen and any app, Always-allowed ones too.
        assertTrue(Clips.pullsBack(forced, since, ownApp = false, launcher = true, exempt = true))
        assertTrue(Clips.pullsBack(forced, since, ownApp = false, launcher = false, exempt = false))
        // Never from her own screens, the phone, Settings or system screens.
        assertFalse(Clips.pullsBack(forced, since, ownApp = true, launcher = false, exempt = false))
        assertFalse(Clips.pullsBack(forced, since, ownApp = false, launcher = false, exempt = true))
        assertFalse(Clips.pullsBack(null, since, ownApp = false, launcher = true, exempt = false))
    }
}
