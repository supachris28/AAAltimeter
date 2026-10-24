package com.supachris28.aaaltimeter.chart

/**
 * The app's colours, in one place so the look can be changed without touching drawing code.
 *
 * A restrained scheme: warm ivory on deep charcoal at night, near-black on warm paper by day,
 * with champagne gold as the single accent for the elevation line. Climbing and descending
 * use copper and steel blue, which stay distinguishable with colour-blindness and always come
 * with an arrow and a word. The phone theme mirrors these in res/values(-night)/colors.xml.
 */
data class ChartPalette(
    val surface: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val grid: Int,
    val accent: Int,
    val climbing: Int,
    val descending: Int,
) {
    companion object {
        val LIGHT = ChartPalette(
            surface = 0xFFFAF8F4.toInt(),
            textPrimary = 0xFF1E1F24.toInt(),
            textSecondary = 0xFF6B6860.toInt(),
            grid = 0xFFE6E1D8.toInt(),
            accent = 0xFF8F6B2E.toInt(),
            climbing = 0xFFB5562A.toInt(),
            descending = 0xFF2F64A3.toInt(),
        )
        val DARK = ChartPalette(
            surface = 0xFF15171C.toInt(),
            textPrimary = 0xFFEDEAE3.toInt(),
            textSecondary = 0xFF9B978E.toInt(),
            grid = 0xFF2A2C33.toInt(),
            accent = 0xFFD4B06A.toInt(),
            climbing = 0xFFCC7A48.toInt(),
            descending = 0xFF5B8BD4.toInt(),
        )

        fun of(dark: Boolean) = if (dark) DARK else LIGHT
    }
}
