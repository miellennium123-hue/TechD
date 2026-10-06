package com.guardianangel

import com.guardianangel.core.Line
import com.guardianangel.core.Voice
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.LineSet
import com.guardianangel.data.Mood
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LinesTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private fun builtIns(line: Line) = LineSet(Voice.builtIn(line, Mood.SWEET), Voice.builtIn(line, Mood.STRICT))

    @Test
    fun noEditsUsesBuiltIns() {
        assertEquals(Voice.builtIn(Line.PRAISE, Mood.STRICT), Voice.lines(Line.PRAISE, Mood.STRICT, emptyMap()))
        assertEquals(Voice.builtIn(Line.PRAISE, Mood.SWEET), Voice.lines(Line.PRAISE, Mood.SWITCHING, emptyMap()))
    }

    @Test
    fun editedLinesReplaceHers() {
        val overrides = mapOf(Line.PRAISE.name to LineSet(sweet = listOf("Mine."), strict = listOf("Also mine.")))
        assertEquals(listOf("Mine."), Voice.lines(Line.PRAISE, Mood.SWEET, overrides))
        assertEquals("Also mine.", Voice.pick(Line.PRAISE, Mood.STRICT, Random(1), overrides))
        // Other situations are untouched.
        assertEquals(Voice.builtIn(Line.DENY, Mood.SWEET), Voice.lines(Line.DENY, Mood.SWEET, overrides))
    }

    @Test
    fun emptyMoodFallsBackToBuiltIns() {
        val overrides = mapOf(Line.DENY.name to LineSet(sweet = emptyList(), strict = listOf("  ", "")))
        assertTrue(Voice.shown(Line.DENY, Mood.SWEET, overrides).isEmpty())
        assertEquals(Voice.builtIn(Line.DENY, Mood.SWEET), Voice.lines(Line.DENY, Mood.SWEET, overrides))
        assertEquals(Voice.builtIn(Line.DENY, Mood.STRICT), Voice.lines(Line.DENY, Mood.STRICT, overrides))
    }

    @Test
    fun everyLineAlwaysHasSomethingToSay() {
        val emptied = Line.entries.associate { it.name to LineSet() }
        Line.entries.forEach { line ->
            assertTrue("$line sweet", Voice.pick(line, Mood.SWEET, Random(0), emptied).isNotBlank())
            assertTrue("$line strict", Voice.pick(line, Mood.STRICT, Random(0), emptied).isNotBlank())
        }
    }

    @Test
    fun firstEditCopiesBothMoods() {
        val after = Voice.edit(emptyMap(), Line.GRANT, Mood.SWEET) { it + "  Go on.  " }
        val set = after.getValue(Line.GRANT.name)
        assertEquals(Voice.builtIn(Line.GRANT, Mood.SWEET) + "Go on.", set.sweet)
        assertEquals(Voice.builtIn(Line.GRANT, Mood.STRICT), set.strict)
        assertTrue(Voice.isEdited(Line.GRANT, after))
        assertFalse(Voice.isEdited(Line.DENY, after))
    }

    @Test
    fun editDropsBlankLines() {
        val after = Voice.edit(emptyMap(), Line.TYPO, Mood.STRICT) { listOf("Again.", "   ", "") }
        assertEquals(listOf("Again."), after.getValue(Line.TYPO.name).strict)
    }

    @Test
    fun deletingAllLinesKeepsTheEdit() {
        val after = Voice.edit(emptyMap(), Line.MOVED, Mood.SWEET) { emptyList() }
        assertTrue(Voice.isEdited(Line.MOVED, after))
        assertTrue(Voice.shown(Line.MOVED, Mood.SWEET, after).isEmpty())
        assertEquals(Voice.builtIn(Line.MOVED, Mood.SWEET), Voice.lines(Line.MOVED, Mood.SWEET, after))
    }

    @Test
    fun editingBackToHersStopsStoringIt() {
        val added = Voice.edit(emptyMap(), Line.SUMMON, Mood.STRICT) { it + "Now." }
        val removed = Voice.edit(added, Line.SUMMON, Mood.STRICT) { it.dropLast(1) }
        assertFalse(Voice.isEdited(Line.SUMMON, removed))
        assertTrue(removed.isEmpty())
    }

    @Test
    fun resetOneSituation() {
        val overrides = Voice.edit(Voice.edit(emptyMap(), Line.PRAISE, Mood.SWEET) { listOf("A") }, Line.DENY, Mood.SWEET) { listOf("B") }
        val after = Voice.reset(overrides, Line.PRAISE)
        assertFalse(Voice.isEdited(Line.PRAISE, after))
        assertTrue(Voice.isEdited(Line.DENY, after))
        assertEquals(builtIns(Line.PRAISE).sweet, Voice.lines(Line.PRAISE, Mood.SWEET, after))
    }

    @Test
    fun unknownSituationIsIgnored() {
        // e.g. the removed WAIT line, or a situation from a newer version.
        val overrides = mapOf("WAIT" to LineSet(listOf("x"), listOf("y")))
        Line.entries.forEach { assertEquals(Voice.builtIn(it, Mood.SWEET), Voice.lines(it, Mood.SWEET, overrides)) }
    }

    @Test
    fun savedConfigRoundTrips() {
        val config = GuardianConfig(lineOverrides = Voice.edit(emptyMap(), Line.GREETING, Mood.SWEET) { listOf("Hi, pet.") })
        val loaded = json.decodeFromString(GuardianConfig.serializer(), json.encodeToString(GuardianConfig.serializer(), config))
        assertEquals(config.lineOverrides, loaded.lineOverrides)
    }

    @Test
    fun olderSavedConfigLoadsWithNoEdits() {
        val loaded = json.decodeFromString(GuardianConfig.serializer(), """{"enabled":true,"mood":"STRICT"}""")
        assertTrue(loaded.enabled)
        assertTrue(loaded.lineOverrides.isEmpty())
    }
}
