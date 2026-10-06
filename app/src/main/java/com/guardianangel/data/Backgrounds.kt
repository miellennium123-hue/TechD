package com.guardianangel.data

/** The emblem drawn above her words on a built-in background. */
enum class Motif { HALO, LOCK, CROWN, HEART, KEY, COLLAR }

/** Colors for a built-in background: a gradient from [top] to [bottom], with gold-ish [accent] for the emblem. */
enum class Palette(val top: Long, val mid: Long, val bottom: Long, val accent: Long) {
    GOLD(0xFF0A0A0A, 0xFF2A2210, 0xFF6B5418, 0xFFF6D58E),
    BURGUNDY(0xFF0A0505, 0xFF3A0A12, 0xFF6E1424, 0xFFE8B86B),
    VIOLET(0xFF07050C, 0xFF24103A, 0xFF4A1F6E, 0xFFE6C77A),
    ROSE(0xFF0C0608, 0xFF3D1424, 0xFF8A3550, 0xFFF4D3A0),
    MIDNIGHT(0xFF020308, 0xFF0B1430, 0xFF1C2A5A, 0xFFD9C27A),
}

/** One of her built-in backgrounds (round 59), drawn on the phone at screen size. */
data class BuiltInBackground(
    val id: String,
    val headline: String,
    val subline: String,
    val motif: Motif,
    val palette: Palette,
)

/** One background in her cycle: a built-in design, an image bundled in assets/wallpapers, or one of yours. */
sealed interface BackgroundRef {
    data class BuiltIn(val id: String) : BackgroundRef
    data class Asset(val name: String) : BackgroundRef
    data class Custom(val fileName: String) : BackgroundRef
}

/** Backgrounds (round 59): her designs, your images, and the cycle. Pure, tested in BackgroundsTest. */
object Backgrounds {
    const val DEFAULT_CYCLE_MINUTES = 2
    const val MIN_CYCLE_MINUTES = 1
    const val MAX_CYCLE_MINUTES = 60

    val BUILT_IN: List<BuiltInBackground> = listOf(
        BuiltInBackground("be_good", "Be good, pet.", "She is watching over you", Motif.HALO, Palette.GOLD),
        BuiltInBackground("property", "Property of my Angel", "Owned, body and phone", Motif.LOCK, Palette.GOLD),
        BuiltInBackground("kneel", "Kneel.", "You know your place", Motif.CROWN, Palette.BURGUNDY),
        BuiltInBackground("obey", "Obey.", "Her rules. Her phone. Her pet.", Motif.HALO, Palette.VIOLET),
        BuiltInBackground("locked", "Locked & obedient", "Every minute belongs to her", Motif.LOCK, Palette.MIDNIGHT),
        BuiltInBackground("ask_first", "Good pets ask first.", "Put the phone down", Motif.KEY, Palette.ROSE),
        BuiltInBackground("denied", "Denied.", "She decides when. Never you.", Motif.LOCK, Palette.BURGUNDY),
        BuiltInBackground("eyes_down", "Eyes down, pet.", "Speak only when spoken to", Motif.COLLAR, Palette.VIOLET),
        BuiltInBackground("hers", "Hers.", "Nothing here belongs to you", Motif.CROWN, Palette.GOLD),
        BuiltInBackground("toy", "Her favorite toy", "Pathetic, and adored", Motif.HEART, Palette.ROSE),
        BuiltInBackground("yes_mistress", "Yes, Mistress.", "The only words you need", Motif.COLLAR, Palette.MIDNIGHT),
        BuiltInBackground("kept", "Kept.", "Caged, owned, cherished", Motif.HEART, Palette.BURGUNDY),
    )

    fun builtIn(id: String): BuiltInBackground = BUILT_IN.firstOrNull { it.id == id } ?: BUILT_IN.first()

    /**
     * Everything she cycles through, in order: her designs you kept, bundled images, then yours.
     * Never empty: with nothing left, her first design.
     */
    fun pool(useBuiltIns: Boolean, hidden: Set<String>, assets: List<String>, customs: List<String>): List<BackgroundRef> {
        val builtIns = if (useBuiltIns) BUILT_IN.filter { it.id !in hidden }.map { BackgroundRef.BuiltIn(it.id) } else emptyList()
        val all = builtIns + assets.map { BackgroundRef.Asset(it) } + customs.map { BackgroundRef.Custom(it) }
        return all.ifEmpty { listOf(BackgroundRef.BuiltIn(BUILT_IN.first().id)) }
    }

    /** Time for the next one: [minutes] since she last changed it. */
    fun due(now: Long, changedAt: Long, minutes: Int): Boolean =
        now - changedAt >= minutes.coerceIn(MIN_CYCLE_MINUTES, MAX_CYCLE_MINUTES) * 60_000L

    /** The next one in the cycle, wrapping round. -1 (never set) starts at the first. */
    fun next(index: Int, size: Int): Int = if (size <= 0) 0 else (index + 1).mod(size)

    /** The current one, if the pool shrank since. */
    fun current(index: Int, size: Int): Int = if (size <= 0 || index < 0) 0 else index.mod(size)
}
