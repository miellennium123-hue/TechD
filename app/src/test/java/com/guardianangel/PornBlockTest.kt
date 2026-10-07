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

/** Porn block (round 73): what she watches, when she scans, what counts, and her lock. */
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
        minute: Int = noon,
    ) = PornBlock.mayScan(pkg, config, state, now, minute, ownApp, screenOn, screenLocked, watched, protectedPackages)

    private val defaults = PornBlockSettings(on = true)
    private fun watches(pkg: String, category: Int = -1, browser: Boolean = false, settings: PornBlockSettings = defaults) =
        PornBlock.watches(pkg, category, browser, settings)

    @Test
    fun watchesBrowsersAndSocialApps() {
        assertTrue(watches("com.android.chrome", browser = true))
        assertTrue(watches("com.twitter.android"))
        assertTrue(watches("com.reddit.frontpage"))
        assertTrue(watches("com.tumblr"))
        // Anything Android tags as social.
        assertTrue(watches("com.example.social", category = 4))
        assertFalse(watches("com.example.notes"))
        assertFalse(watches("com.example.game", category = 0))
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
        repeat(2) { count = PornBlock.nextBlind(count, blind = true, browser = true, config = on) }
        assertFalse(PornBlock.hiding(count, scanSeconds = 5))
        count = PornBlock.nextBlind(count, blind = true, browser = true, config = on)
        assertTrue(PornBlock.hiding(count, scanSeconds = 5))
        // About 15 seconds at any check rate, and never a single black frame.
        assertEquals(5, PornBlock.blindScansNeeded(3))
        assertEquals(3, PornBlock.blindScansNeeded(5))
        assertEquals(2, PornBlock.blindScansNeeded(10))
        assertEquals(2, PornBlock.blindScansNeeded(60))
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

    // ---- Round 74: your app list, check rate, hours and adult apps -----------------------------

    @Test
    fun youEditWhichAppsSheChecks() {
        // Add a game, take Reddit off.
        var p = PornBlock.setWatched(defaults, "com.example.game", byDefault = false, on = true)
        p = PornBlock.setWatched(p, "com.reddit.frontpage", byDefault = true, on = false)
        assertTrue(watches("com.example.game", category = 0, settings = p))
        assertFalse(watches("com.reddit.frontpage", settings = p))
        assertTrue(watches("com.twitter.android", settings = p))
        // Ticking back only stores what differs from her list.
        p = PornBlock.setWatched(p, "com.example.game", byDefault = false, on = false)
        p = PornBlock.setWatched(p, "com.reddit.frontpage", byDefault = true, on = true)
        assertEquals(defaults, p)
        // Browsers can be taken off too.
        val noChrome = PornBlock.setWatched(defaults, "com.android.chrome", byDefault = true, on = false)
        assertFalse(watches("com.android.chrome", browser = true, settings = noChrome))
    }

    @Test
    fun checkRateSteps() {
        assertEquals(5, PornBlock.scanSeconds(defaults))
        assertEquals(10, PornBlock.stepScan(5, up = true))
        assertEquals(3, PornBlock.stepScan(5, up = false))
        assertEquals(3, PornBlock.stepScan(3, up = false))
        assertEquals(60, PornBlock.stepScan(60, up = true))
        // A value off the list snaps to the nearest step.
        assertEquals(10, PornBlock.scanSeconds(defaults.copy(scanSeconds = 9)))
    }

    @Test
    fun onlyInsideYourHours() {
        val hours = on.copy(pornBlock = defaults.copy(hoursOn = true, startMinute = 9 * 60, endMinute = 22 * 60))
        assertTrue(PornBlock.active(on, 3 * 60))
        assertTrue(PornBlock.active(hours, noon))
        assertFalse(PornBlock.active(hours, 23 * 60))
        assertFalse(may(config = hours, minute = 23 * 60))
        assertTrue(may(config = hours, minute = noon))
        // Overnight hours wrap past midnight.
        val night = on.copy(pornBlock = defaults.copy(hoursOn = true, startMinute = 22 * 60, endMinute = 6 * 60))
        assertTrue(PornBlock.active(night, 23 * 60))
        assertFalse(PornBlock.active(night, noon))
        // A lock already running stays outside your hours.
        assertTrue(PornBlock.locked(hours, caught, now))
    }

    @Test
    fun openingAnAdultAppIsACatch() {
        val config = on.copy(pornBlock = defaults.copy(adultApps = setOf("com.adult")), alwaysAllowed = setOf("com.adult"))
        fun adult(
            pkg: String = "com.adult",
            c: GuardianConfig = config,
            state: GuardianState = GuardianState(),
            minute: Int = noon,
            protectedPackages: Set<String> = emptySet(),
        ) = PornBlock.adultApp(pkg, c, state, now, minute, ownApp = false, protectedPackages = protectedPackages)
        // Always-allowed doesn't protect it.
        assertTrue(adult())
        assertFalse(adult(pkg = "com.android.chrome"))
        assertFalse(adult(state = caught))
        assertFalse(adult(c = config.copy(enabled = false)))
        assertFalse(adult(c = config.copy(pornBlock = config.pornBlock.copy(on = false))))
        assertFalse(adult(protectedPackages = setOf("com.adult")))
        val hours = config.copy(pornBlock = config.pornBlock.copy(hoursOn = true, startMinute = 9 * 60, endMinute = 22 * 60))
        assertFalse(adult(c = hours, minute = 23 * 60))
        assertTrue(adult(c = hours, minute = noon))
        // During her lock it stays blocked, even though it's Always-allowed. Other Always-allowed apps open.
        val withChat = config.copy(alwaysAllowed = setOf("com.adult", "com.whatsapp"))
        val block = Rules.decide("com.adult", withChat, caught, now, noon)
        assertTrue(block is Decision.Block && block.kind == RestrictionKind.CAUGHT)
        assertEquals(Decision.Allow, Rules.decide("com.whatsapp", withChat, caught, now, noon))
        assertEquals(Decision.Allow, Rules.decide("com.adult", withChat, GuardianState(), now, noon))
    }

    @Test
    fun loosensWithRound74Settings() {
        val strict = on.copy(lockGuard = true, pornBlock = defaults.copy(adultApps = setOf("a"), watched = setOf("g"), unwatched = setOf("r")))
        fun after(p: PornBlockSettings) = strict.copy(pornBlock = p)
        val p = strict.pornBlock
        assertTrue(LockGuard.loosens(strict, after(p.copy(scanSeconds = 10))))
        assertFalse(LockGuard.loosens(strict, after(p.copy(scanSeconds = 3))))
        assertTrue(LockGuard.loosens(strict, after(p.copy(adultApps = emptySet()))))
        assertFalse(LockGuard.loosens(strict, after(p.copy(adultApps = setOf("a", "b")))))
        assertTrue(LockGuard.loosens(strict, after(p.copy(watched = emptySet()))))
        assertTrue(LockGuard.loosens(strict, after(p.copy(unwatched = setOf("r", "x")))))
        assertFalse(LockGuard.loosens(strict, after(p.copy(unwatched = emptySet(), watched = setOf("g", "h")))))
        assertTrue(LockGuard.loosens(strict, after(p.copy(hoursOn = true))))
        val hours = after(p.copy(hoursOn = true))
        assertFalse(LockGuard.loosens(hours, hours.copy(pornBlock = hours.pornBlock.copy(hoursOn = false))))
        assertTrue(LockGuard.loosens(hours, hours.copy(pornBlock = hours.pornBlock.copy(endMinute = 22 * 60))))
    }

    @Test
    fun olderCatchesLoadAsScreenCatches() {
        val json = Json { ignoreUnknownKeys = true }
        val record = json.decodeFromString(CatchRecord.serializer(), """{"at":1,"app":"Chrome","hiding":false,"line":"x"}""")
        assertFalse(record.adultApp)
    }
}
