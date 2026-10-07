package com.guardianangel

import com.guardianangel.core.Decision
import com.guardianangel.core.LockGuard
import com.guardianangel.core.PornBlock
import com.guardianangel.core.RestrictionKind
import com.guardianangel.core.Rules
import com.guardianangel.core.Tile
import com.guardianangel.data.CatchRecord
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.MarkSettings
import com.guardianangel.data.PornBlockSettings
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Porn block (round 71): what she watches, when she scans, what counts, and her lock. */
class PornBlockTest {
    private val now = 100 * 60_000L
    private val noon = 12 * 60
    private val on = GuardianConfig(enabled = true, pornBlock = PornBlockSettings(on = true))
    private val caught = GuardianState(caughtUntil = now + 60_000)

    private fun may(
        pkg: String? = "com.android.chrome",
        config: GuardianConfig = on,
        state: GuardianState = GuardianState(),
        ownApp: Boolean = false,
        screenOn: Boolean = true,
        screenLocked: Boolean = false,
        watched: Boolean = true,
        protectedPackages: Set<String> = emptySet(),
    ) = PornBlock.mayScan(pkg, config, state, now, ownApp, screenOn, screenLocked, watched, protectedPackages)

    @Test
    fun watchesBrowsersAndSocialApps() {
        assertTrue(PornBlock.watches("com.android.chrome", -1, browser = true))
        assertTrue(PornBlock.watches("com.twitter.android", -1, browser = false))
        assertTrue(PornBlock.watches("com.reddit.frontpage", -1, browser = false))
        assertTrue(PornBlock.watches("com.tumblr", -1, browser = false))
        // Anything Android tags as social.
        assertTrue(PornBlock.watches("com.example.social", 4, browser = false))
        assertFalse(PornBlock.watches("com.example.notes", -1, browser = false))
        assertFalse(PornBlock.watches("com.example.game", 0, browser = false))
    }

    @Test
    fun scansWatchedAppsWhileOn() {
        assertTrue(may())
        // Nothing is saved, so unlike her peeks the keyboard doesn't stop her.
        assertTrue(may(pkg = "com.reddit.frontpage"))
    }

    @Test
    fun neverScansWhenShouldnt() {
        assertFalse(may(pkg = null))
        assertFalse(may(watched = false))
        assertFalse(may(ownApp = true))
        assertFalse(may(screenOn = false))
        assertFalse(may(screenLocked = true))
        assertFalse(may(config = on.copy(enabled = false)))
        assertFalse(may(config = on.copy(pornBlock = PornBlockSettings(on = false))))
        assertFalse(may(pkg = "com.android.settings"))
        assertFalse(may(pkg = "com.browser", protectedPackages = setOf("com.browser")))
        assertFalse(may(config = on.copy(alwaysAllowed = setOf("com.android.chrome"))))
        // Already locked: nothing more to catch.
        assertFalse(may(state = caught))
    }

    @Test
    fun tilesCoverATallScreenInOverlappingSquares() {
        val tiles = PornBlock.tiles(640, 1422)
        assertEquals(3, tiles.size)
        assertEquals(Tile(0, 0, 640), tiles.first())
        assertEquals(Tile(0, 1422 - 640, 640), tiles.last())
        tiles.forEach { assertTrue(it.y + it.size <= 1422) }
        // Landscape goes sideways; a square is one tile.
        assertEquals(Tile(1422 - 640, 0, 640), PornBlock.tiles(1422, 640).last())
        assertEquals(listOf(Tile(0, 0, 500)), PornBlock.tiles(500, 500))
        assertTrue(PornBlock.tiles(0, 100).isEmpty())
    }

    @Test
    fun pornIsAScoreAtHerLine() {
        assertTrue(PornBlock.porn(PornBlock.THRESHOLD))
        assertTrue(PornBlock.porn(0.9f))
        assertFalse(PornBlock.porn(0.3f))
    }

    @Test
    fun aBlackMiddleIsABlindScreen() {
        val w = 10
        val h = 20
        val black = IntArray(w * h)
        assertTrue(PornBlock.blind(black, w, h))
        // Status bar icons at the top don't matter.
        val withBar = black.copyOf().also { for (i in 0 until w) it[i] = 255 }
        assertTrue(PornBlock.blind(withBar, w, h))
        // A real page, even a dark one, has something on it.
        val page = IntArray(w * h) { if (it % 7 == 0) 200 else 20 }
        assertFalse(PornBlock.blind(page, w, h))
        assertFalse(PornBlock.blind(IntArray(w * h) { 30 }, w, h))
    }

    @Test
    fun onlyABrowserBlindForAboutFifteenSecondsIsHiding() {
        var count = 0
        repeat(PornBlock.BLIND_SCANS - 1) { count = PornBlock.nextBlind(count, blind = true, browser = true, config = on) }
        assertFalse(PornBlock.hiding(count))
        count = PornBlock.nextBlind(count, blind = true, browser = true, config = on)
        assertTrue(PornBlock.hiding(count))
        // One normal screen starts it over.
        assertEquals(0, PornBlock.nextBlind(count, blind = false, browser = true, config = on))
        // Social apps never count, and neither does anything with Private tabs off.
        assertEquals(0, PornBlock.nextBlind(2, blind = true, browser = false, config = on))
        val off = on.copy(pornBlock = PornBlockSettings(on = true, privateTabs = false))
        assertEquals(0, PornBlock.nextBlind(2, blind = true, browser = true, config = off))
    }

    @Test
    fun aCatchLocksEverythingButAlwaysAllowed() {
        val config = on.copy(alwaysAllowed = setOf("com.whatsapp"))
        val block = Rules.decide("com.android.chrome", config, caught, now, noon)
        assertTrue(block is Decision.Block)
        block as Decision.Block
        assertEquals(RestrictionKind.CAUGHT, block.kind)
        assertFalse(block.askAllowed)
        assertFalse(block.canBuy)
        assertEquals(caught.caughtUntil, block.until)
        assertEquals(Decision.Allow, Rules.decide("com.whatsapp", config, caught, now, noon))
        assertEquals(Decision.Allow, Rules.decide("com.android.dialer", config, caught, now, noon))
        // Grants and bought time don't get through.
        val granted = caught.copy(grants = listOf(com.guardianangel.data.Grant("com.android.chrome", now + 60_000, bought = true)))
        assertTrue(Rules.decide("com.android.chrome", config, granted, now, noon) is Decision.Block)
        // Over, switched off, or Porn block off: free again.
        assertEquals(Decision.Allow, Rules.decide("com.android.chrome", config, caught, caught.caughtUntil, noon))
        assertEquals(Decision.Allow, Rules.decide("com.android.chrome", config.copy(enabled = false), caught, now, noon))
        assertEquals(Decision.Allow, Rules.decide("com.android.chrome", config.copy(pornBlock = PornBlockSettings()), caught, now, noon))
    }

    @Test
    fun herCaughtScreenCoversHomeAndBlockedApps() {
        val block = Rules.decide("com.android.chrome", on, caught, now, noon)
        assertTrue(Rules.caughtScreen(on, caught, now, block, launcher = false))
        assertTrue(Rules.caughtScreen(on, caught, now, Decision.Allow, launcher = true))
        assertFalse(Rules.caughtScreen(on, caught, now, Decision.Allow, launcher = false))
        assertFalse(Rules.caughtScreen(on, GuardianState(), now, Decision.Allow, launcher = true))
    }

    @Test
    fun herLockCountsForLockGuardAndHerTint() {
        assertTrue(LockGuard.locked(on, caught, now))
        assertFalse(LockGuard.locked(on, GuardianState(caughtUntil = now - 1), now))
        val marked = on.copy(mark = MarkSettings(on = true))
        assertTrue(Rules.markTinted(marked, caught, now, noon, ownApp = false))
    }

    @Test
    fun lockLengthAndRepeatCatches() {
        assertEquals(now + 60 * 60_000L, PornBlock.lockUntil(0, now, 60))
        // A catch during a lock adds on to it.
        assertEquals(now + 120 * 60_000L, PornBlock.lockUntil(now + 60 * 60_000L, now, 60))
        assertEquals(75, PornBlock.stepLock(60, up = true))
        assertEquals(60, PornBlock.stepLock(75, up = false))
        assertEquals(180, PornBlock.stepLock(120, up = true))
        assertEquals(120, PornBlock.stepLock(180, up = false))
        assertEquals(PornBlock.MIN_LOCK_MINUTES, PornBlock.stepLock(PornBlock.MIN_LOCK_MINUTES, up = false))
        assertEquals(PornBlock.MAX_LOCK_MINUTES, PornBlock.stepLock(PornBlock.MAX_LOCK_MINUTES, up = true))
    }

    @Test
    fun keepsTheNewestCatches() {
        var catches = emptyList<CatchRecord>()
        repeat(PornBlock.KEEP + 5) { catches = PornBlock.add(catches, CatchRecord(it.toLong(), "Chrome", false, "x")) }
        assertEquals(PornBlock.KEEP, catches.size)
        assertEquals((PornBlock.KEEP + 4).toLong(), catches.last().at)
    }

    @Test
    fun switchingAnyOfItOffOrAShorterLockLoosens() {
        val strict = on.copy(lockGuard = true)
        fun with(p: PornBlockSettings) = strict.copy(pornBlock = p)
        val base = PornBlockSettings(on = true)
        assertTrue(LockGuard.loosens(strict, with(base.copy(on = false))))
        assertTrue(LockGuard.loosens(strict, with(base.copy(lockScreen = false))))
        assertTrue(LockGuard.loosens(strict, with(base.copy(failure = false))))
        assertTrue(LockGuard.loosens(strict, with(base.copy(privateTabs = false))))
        assertTrue(LockGuard.loosens(strict, with(base.copy(lockMinutes = 45))))
        assertFalse(LockGuard.loosens(strict, with(base.copy(lockMinutes = 90))))
        assertFalse(LockGuard.loosens(GuardianConfig(enabled = true), strict))
    }

    @Test
    fun nothingChangesWhileLocked() {
        val strict = on.copy(lockGuard = true)
        assertTrue(LockGuard.frozen(strict, strict.copy(pornBlock = PornBlockSettings(on = true, lockMinutes = 90)), caught, now, noon) != null)
        assertTrue(LockGuard.frozen(strict, strict.copy(alwaysAllowed = strict.alwaysAllowed + "com.android.chrome"), caught, now, noon) != null)
        assertNull(LockGuard.frozen(strict, strict.copy(pornBlock = PornBlockSettings(on = true, lockMinutes = 90)), GuardianState(), now, noon))
    }

    @Test
    fun olderSavesLoadWithPornBlockOff() {
        val json = Json { ignoreUnknownKeys = true }
        val config = json.decodeFromString(GuardianConfig.serializer(), """{"enabled":true}""")
        assertEquals(PornBlockSettings(), config.pornBlock)
        assertFalse(config.pornBlock.on)
        val state = json.decodeFromString(GuardianState.serializer(), """{"merit":5}""")
        assertEquals(0L, state.caughtUntil)
        assertTrue(state.catches.isEmpty())
    }
}
