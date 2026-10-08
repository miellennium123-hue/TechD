package com.guardianangel

import com.guardianangel.data.BackgroundRef
import com.guardianangel.data.Backgrounds
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundsTest {
    @Test
    fun herDesignsAreDistinctAndWorded() {
        val ids = Backgrounds.BUILT_IN.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(Backgrounds.BUILT_IN.size >= 10)
        Backgrounds.BUILT_IN.forEach {
            assertTrue(it.id, it.headline.isNotBlank() && it.subline.isNotBlank())
        }
    }

    @Test
    fun poolIsHersThenBundledThenYours() {
        val pool = Backgrounds.pool(true, setOf("kneel"), listOf("a.png"), listOf("bg_1.jpg"))
        assertEquals(Backgrounds.BUILT_IN.size - 1 + 2, pool.size)
        assertFalse(BackgroundRef.BuiltIn("kneel") in pool)
        assertEquals(BackgroundRef.Asset("a.png"), pool[pool.size - 2])
        assertEquals(BackgroundRef.Custom("bg_1.jpg"), pool.last())
    }

    @Test
    fun onlyYoursWhenHersAreOff() {
        assertEquals(listOf(BackgroundRef.Custom("x.jpg")), Backgrounds.pool(false, emptySet(), emptyList(), listOf("x.jpg")))
    }

    @Test
    fun neverEmpty() {
        val pool = Backgrounds.pool(false, emptySet(), emptyList(), emptyList())
        assertEquals(listOf(BackgroundRef.BuiltIn(Backgrounds.BUILT_IN.first().id)), pool)
        val allHidden = Backgrounds.pool(true, Backgrounds.BUILT_IN.map { it.id }.toSet(), emptyList(), emptyList())
        assertEquals(1, allHidden.size)
    }

    @Test
    fun cyclesInOrderAndWraps() {
        assertEquals(0, Backgrounds.next(-1, 5))
        assertEquals(1, Backgrounds.next(0, 5))
        assertEquals(0, Backgrounds.next(4, 5))
        // The pool shrank: still a valid index.
        assertEquals(1, Backgrounds.current(7, 3))
        assertEquals(0, Backgrounds.current(-1, 3))
        assertEquals(0, Backgrounds.next(3, 0))
    }

    @Test
    fun dueEveryFewMinutes() {
        val t = 10_000_000L
        assertTrue(Backgrounds.due(t, t - 2 * 60_000, 2))
        assertFalse(Backgrounds.due(t, t - 60_000, 2))
        // Out-of-range settings are clamped.
        assertTrue(Backgrounds.due(t, t - 60_000, 0))
        assertTrue(Backgrounds.due(t, 0, 500))
    }

    @Test
    fun olderSavedDataLoads() {
        val json = Json { ignoreUnknownKeys = true }
        val w = json.decodeFromString(GuardianConfig.serializer(), "{\"wallpaper\":{\"on\":true}}").wallpaper
        assertTrue(w.on && w.cycle && w.builtIns)
        assertEquals(Backgrounds.DEFAULT_CYCLE_MINUTES, w.cycleMinutes)
        assertTrue(w.hiddenBuiltIns.isEmpty())
        val st = json.decodeFromString(GuardianState.serializer(), "{\"wallpaperId\":5}")
        assertEquals(-1, st.wallpaperIndex)
    }

    @Test
    fun sheLeavesTheWallpaperAloneWhileYouUseHer() {
        // Round 99: a new wallpaper reloads open screens on Android 12+, so never while her screens are in front.
        assertTrue(Backgrounds.waits(ownApp = true, quitting = false))
        assertTrue(Backgrounds.waits(ownApp = false, quitting = true))
        assertFalse(Backgrounds.waits(ownApp = false, quitting = false))
    }
}
