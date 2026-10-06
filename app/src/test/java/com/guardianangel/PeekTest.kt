package com.guardianangel

import com.guardianangel.core.LockGuard
import com.guardianangel.core.Peek
import com.guardianangel.core.PeekKind
import com.guardianangel.core.Voice
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.Mood
import com.guardianangel.data.PeekRecord
import com.guardianangel.data.PeekSettings
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** She peeks (round 60): when she may look, what she thinks you were doing, what she keeps. */
class PeekTest {
    private val on = GuardianConfig(enabled = true, peek = PeekSettings(on = true))
    private val minute = 60_000L

    private fun may(
        pkg: String? = "com.example.app",
        config: GuardianConfig = on,
        ownApp: Boolean = false,
        screenOn: Boolean = true,
        locked: Boolean = false,
        keyboardUp: Boolean = false,
        launcher: Boolean = false,
        protectedPackages: Set<String> = emptySet(),
    ) = Peek.mayLook(pkg, config, ownApp, screenOn, locked, keyboardUp, launcher, protectedPackages)

    @Test
    fun dueEveryFiveMinutesWhileOn() {
        val now = 100 * minute
        assertTrue(Peek.due(on, 0, now))
        assertTrue(Peek.due(on, now - 5 * minute, now))
        assertFalse(Peek.due(on, now - 4 * minute, now))
        assertFalse(Peek.due(on.copy(enabled = false), 0, now))
        assertFalse(Peek.due(on.copy(peek = PeekSettings(on = false)), 0, now))
    }

    @Test
    fun looksAtOrdinaryAppsAndTheHomeScreen() {
        assertTrue(may())
        assertTrue(may(pkg = "com.launcher", launcher = true, protectedPackages = setOf("com.launcher")))
    }

    @Test
    fun neverLooksWhenShouldnt() {
        assertFalse(may(pkg = null))
        assertFalse(may(ownApp = true))
        assertFalse(may(screenOn = false))
        assertFalse(may(locked = true))
        assertFalse(may(keyboardUp = true))
        assertFalse(may(pkg = "com.android.dialer"))
        assertFalse(may(pkg = "com.keyboard", protectedPackages = setOf("com.keyboard")))
        assertFalse(may(pkg = "com.mybank", config = on.copy(alwaysAllowed = setOf("com.mybank"))))
    }

    @Test
    fun kindFromAppAndCategory() {
        assertEquals(PeekKind.HOME, Peek.kind("com.launcher", -1, launcher = true, browser = false))
        assertEquals(PeekKind.SOCIAL, Peek.kind("com.instagram.android", -1, false, false))
        assertEquals(PeekKind.SOCIAL, Peek.kind("com.new.social", Peek.CATEGORY_SOCIAL, false, false))
        assertEquals(PeekKind.VIDEO, Peek.kind("com.google.android.youtube", Peek.CATEGORY_VIDEO, false, false))
        assertEquals(PeekKind.VIDEO, Peek.kind("com.some.player", Peek.CATEGORY_VIDEO, false, false))
        assertEquals(PeekKind.GAME, Peek.kind("com.some.game", Peek.CATEGORY_GAME, false, false))
        assertEquals(PeekKind.CHAT, Peek.kind("com.whatsapp", Peek.CATEGORY_SOCIAL, false, false))
        assertEquals(PeekKind.BROWSER, Peek.kind("com.android.chrome", -1, false, browser = true))
        assertEquals(PeekKind.OTHER, Peek.kind("com.notes", -1, false, false))
    }

    @Test
    fun everyKindHasLinesInBothMoods() {
        PeekKind.entries.forEach {
            assertTrue(it.name, Voice.builtIn(it.line, Mood.SWEET).isNotEmpty())
            assertTrue(it.name, Voice.builtIn(it.line, Mood.STRICT).isNotEmpty())
        }
    }

    @Test
    fun keepsTheNewestAndDropsTheOldest() {
        val full = (1..Peek.KEEP).map { PeekRecord(it.toLong(), "peek_$it.jpg", "App", "Hi") }
        val (kept, gone) = Peek.add(full, PeekRecord(999, "peek_new.jpg", "App", "Hi"))
        assertEquals(Peek.KEEP, kept.size)
        assertEquals("peek_new.jpg", kept.last().file)
        assertEquals(listOf("peek_1.jpg"), gone.map { it.file })
        val (few, none) = Peek.add(emptyList(), PeekRecord(1, "a.jpg", "App", "Hi"))
        assertEquals(1, few.size)
        assertTrue(none.isEmpty())
    }

    @Test
    fun switchingPeeksOffLoosens() {
        assertTrue(LockGuard.loosens(on, on.copy(peek = PeekSettings(on = false))))
        assertFalse(LockGuard.loosens(GuardianConfig(enabled = true), on))
    }

    @Test
    fun olderSavesLoadWithPeeksOff() {
        val json = Json { ignoreUnknownKeys = true }
        assertFalse(json.decodeFromString(GuardianConfig.serializer(), """{"enabled":true}""").peek.on)
    }
}
