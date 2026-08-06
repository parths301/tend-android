package com.tend.app.data

/**
 * The fixed set of colors and the badge letters a habit can be tagged with.
 *
 * A curated palette rather than a free color picker, on purpose — it keeps
 * every habit card legible against the app's flat, warm background instead
 * of risking a color a user picks that reads poorly there.
 */
object HabitPalette {
    val COLORS = listOf(
        0xFFD96F4E, 0xFF7D74C9, 0xFF2F9C82, 0xFFC9931F,
        0xFF4A90D9, 0xFFD94A7A, 0xFF5E9E5E, 0xFF8C6E4E,
    )

    /** A deterministic starting point — same name, same color — until the user picks one. */
    fun suggestedColor(name: String): Long = COLORS[(name.hashCode() and 0x7FFFFFFF) % COLORS.size]

    fun suggestedGlyph(name: String): String =
        name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("").ifEmpty { "HB" }.take(2)
}
