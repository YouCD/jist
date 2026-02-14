package dev.rcht.jist.ui.theme

import androidx.compose.ui.graphics.Color

// Jist Brand Colors - Consistent Cyan/Purple theme across all screens
// Primary: Vibrant Cyan (modern, tech-forward) - matches Dashboard
val JistCyan = Color(0xFF00E5FF)
val JistPurple = Color(0xFFD500F9)

// App Background Colors
val AppBackground = Color(0xFF101C22)
val JistBackgroundStart = Color(0xFF0D1B2A)
val JistBackgroundEnd = Color(0xFF101C22)

// Glassmorphism Colors
val GlassSurface = Color(0xFF1E1E1E).copy(alpha = 0.6f)
val GlassBorder = Color(0xFFFFFFFF).copy(alpha = 0.1f)

// Light Theme Colors (using JistCyan as primary)
val md_theme_light_primary = JistCyan
val md_theme_light_onPrimary = Color(0xFF003744)
val md_theme_light_primaryContainer = Color(0xFFB3F5FF)
val md_theme_light_onPrimaryContainer = Color(0xFF001F28)

// Secondary: JistPurple (accent for secondary actions)
val md_theme_light_secondary = JistPurple
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFF3D9FF)
val md_theme_light_onSecondaryContainer = Color(0xFF3C0059)

// Tertiary: Cyan variant (accent for highlights)
val md_theme_light_tertiary = Color(0xFF00B8D4)
val md_theme_light_onTertiary = Color(0xFF003640)
val md_theme_light_tertiaryContainer = Color(0xFFB3F5FF)
val md_theme_light_onTertiaryContainer = Color(0xFF001F28)

// Error: Standard Material 3 error red
val md_theme_light_error = Color(0xFFB3261E)
val md_theme_light_errorContainer = Color(0xFFF9DEDC)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_onErrorContainer = Color(0xFF410E0B)

// Neutral: Gray tones for surfaces and text
val md_theme_light_background = Color(0xFFFFFBFE)
val md_theme_light_onBackground = Color(0xFF1C1B1F)
val md_theme_light_surface = Color(0xFFFFFBFE)
val md_theme_light_onSurface = Color(0xFF1C1B1F)
val md_theme_light_surfaceVariant = Color(0xFFE7E0EC)
val md_theme_light_onSurfaceVariant = Color(0xFF49454E)
val md_theme_light_outline = Color(0xFF79747E)
val md_theme_light_outlineVariant = Color(0xFFCAC7D0)

// Scrim (for overlays and dialogs)
val md_theme_light_scrim = Color(0xFF000000)

// Dark Theme Colors - Using JistCyan and JistPurple for consistency
val md_theme_dark_primary = JistCyan
val md_theme_dark_onPrimary = Color(0xFF003744)
val md_theme_dark_primaryContainer = Color(0xFF004D5C)
val md_theme_dark_onPrimaryContainer = Color(0xFFB3F5FF)

val md_theme_dark_secondary = JistPurple
val md_theme_dark_onSecondary = Color(0xFF570080)
val md_theme_dark_secondaryContainer = Color(0xFF7A00B3)
val md_theme_dark_onSecondaryContainer = Color(0xFFF3D9FF)

val md_theme_dark_tertiary = Color(0xFF00B8D4)
val md_theme_dark_onTertiary = Color(0xFF003640)
val md_theme_dark_tertiaryContainer = Color(0xFF004D5C)
val md_theme_dark_onTertiaryContainer = Color(0xFFB3F5FF)

val md_theme_dark_error = Color(0xFFF2B8B5)
val md_theme_dark_errorContainer = Color(0xFF8C1D18)
val md_theme_dark_onError = Color(0xFF601410)
val md_theme_dark_onErrorContainer = Color(0xFFF9DEDC)

val md_theme_dark_background = AppBackground
val md_theme_dark_onBackground = Color(0xFFE6E1E6)
val md_theme_dark_surface = AppBackground
val md_theme_dark_onSurface = Color(0xFFE6E1E6)
val md_theme_dark_surfaceVariant = Color(0xFF2A3A42)
val md_theme_dark_onSurfaceVariant = Color(0xFFBFC8CC)
val md_theme_dark_outline = Color(0xFF6B7A82)
val md_theme_dark_outlineVariant = Color(0xFF3A4A52)

val md_theme_dark_scrim = Color(0xFF000000)
