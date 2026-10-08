package com.guardianangel

import com.guardianangel.core.CheckInAction
import com.guardianangel.core.CheckInRolls
import com.guardianangel.core.ClipInfo
import com.guardianangel.core.ClipKind
import com.guardianangel.core.Clips
import com.guardianangel.core.LockGuard
import com.guardianangel.core.Rules
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
        assertNull(Clips.films(StepKind.WATCH, films))
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
        assertTrue(Clips.inSession(s, clips = 1, roll = 0.0))
        assertFalse(Clips.inSession(s, clips = 0, roll = 0.0))
        // Every session (round 78), as long as there's something to watch.
        assertTrue(Clips.inSession(s, clips = 1, roll = 0.99))
        assertFalse(Clips.inSession(s.copy(watchInSessions = false), clips = 1, roll = 0.0))
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
        assertFalse(LockGuard.loosens(strict, strict.copy(session = SessionSettings(watchInSessions = false))))
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
