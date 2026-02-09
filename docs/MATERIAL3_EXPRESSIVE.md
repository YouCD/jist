# Material 3 Expressive Design System

This document details how Jist implements Material 3 Expressive (https://m3.material.io/) for a modern, visually rich UI.

---

## What is Material 3 Expressive?

Material 3 Expressive is a variant of Material 3 with:
- **Larger, bolder typography scales** for stronger visual hierarchy
- **Vibrant color tokens** (primary, secondary, tertiary) for more visual richness
- **Generous spacing and corner radius** for a premium feel
- **Dynamic color (Material You)** on Android 12+ — colors extracted from user's wallpaper
- **Improved contrast and readability** for accessibility

---

## Typography Scale

Jist uses the full M3 Expressive typography suite:

| Style | Size | Weight | Use Case |
|---|---|---|---|
| `displayLarge` | 57sp | 400 | Page titles, hero sections (Dashboard stats) |
| `displayMedium` | 45sp | 400 | Prominent headings (card titles) |
| `displaySmall` | 36sp | 400 | Major section headers |
| `headlineLarge` | 32sp | 400 | Screen section titles |
| `headlineMedium` | 28sp | 400 | Subsection titles (Settings groups) |
| `headlineSmall` | 24sp | 400 | Card headers |
| `titleLarge` | 22sp | 500 | Dialog titles, important labels |
| `titleMedium` | 16sp | 500 | Secondary headings |
| `titleSmall` | 14sp | 500 | Small titles |
| `bodyLarge` | 16sp | 400 | Main body text, list items |
| `bodyMedium` | 14sp | 400 | Secondary body text |
| `bodySmall` | 12sp | 400 | Helper text, captions |
| `labelLarge` | 14sp | 500 | Button text, labels |
| `labelMedium` | 12sp | 500 | Small labels |
| `labelSmall` | 11sp | 500 | Smallest labels |

**Never hardcode font sizes** — always use `MaterialTheme.typography.*`

---

## Color System

### Core Colors

Jist's Expressive color palette:

| Color | Usage |
|---|---|
| **Primary** | Main brand color (vibrant teal). Buttons, FABs, high emphasis. |
| **Secondary** | Accent color for supporting elements. Toggles, secondary buttons. |
| **Tertiary** | Tertiary accent for special highlights. Status indicators, badges. |
| **Error** | Red for errors, destructive actions. |
| **Neutral** | Background surfaces, dividers. |
| **Neutral Variant** | Borders, outlines, secondary surfaces. |

### Dynamic Color (Material You)

On **Android 12+**, colors are extracted from the user's wallpaper via `dynamicColorScheme()`:
```kotlin
val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    dynamicColorScheme(LocalContext.current)
} else {
    customColorScheme() // Jist Expressive fallback
}
```

**Always use `MaterialTheme.colorScheme.*`** — never hardcode `#HexColor`:
```kotlin
// ✅ CORRECT
Button(
    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
) { }

// ❌ WRONG
Button(
    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BCD4))
) { }
```

---

## Components

### Buttons

Use Material 3 button styles for consistency:

```kotlin
// Primary button — highest emphasis
Button(
    onClick = { }
) { Text("Summarize") }

// Filled button — normal emphasis
FilledButton(
    onClick = { }
) { Text("Save") }

// Outlined button — medium emphasis
OutlinedButton(
    onClick = { }
) { Text("Cancel") }

// Text button — lowest emphasis
TextButton(
    onClick = { }
) { Text("Learn More") }
```

### Cards

Use `ElevatedCard` or `OutlinedCard` (not plain `Card`):

```kotlin
// Elevated card — raised appearance
ElevatedCard(
    modifier = Modifier.fillMaxWidth()
) {
    Text("Notification Stats", style = MaterialTheme.typography.headlineMedium)
    Text("42 notifications today", style = MaterialTheme.typography.bodyLarge)
}

// Outlined card — flat appearance with border
OutlinedCard {
    Text("Summary", style = MaterialTheme.typography.titleMedium)
}
```

### Lists & Items

Use `ListItem` composable from Material 3:

```kotlin
ListItem(
    headlineContent = { Text("Project Team", style = MaterialTheme.typography.bodyLarge) },
    supportingContent = { Text("5 messages", style = MaterialTheme.typography.bodyMedium) },
    leadingContent = { /* Icon */ },
    trailingContent = { /* Icon */ },
    colors = ListItemDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surface
    )
)
```

### Top App Bar

Use `TopAppBar` with Material 3 styling:

```kotlin
TopAppBar(
    title = { Text("Notifications", style = MaterialTheme.typography.headlineSmall) },
    navigationIcon = { IconButton(onClick = {}) { Icon(...) } },
    actions = { /* Action buttons */ },
    colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.primary
    )
)
```

### Dialogs & Sheets

Use Material 3 `AlertDialog`, `ModalBottomSheet`:

```kotlin
AlertDialog(
    onDismissRequest = { },
    title = { Text("Confirm Delete?", style = MaterialTheme.typography.headlineSmall) },
    text = { Text("This cannot be undone.", style = MaterialTheme.typography.bodyMedium) },
    confirmButton = {
        Button(onClick = { }) { Text("Delete") }
    },
    dismissButton = {
        TextButton(onClick = { }) { Text("Cancel") }
    }
)
```

---

## Spacing & Padding

Use Material 3 recommended spacing:

| Size | Usage |
|---|---|
| 4dp | Tiny gaps between tight elements |
| 8dp | Small padding inside small components |
| 12dp | Medium padding around content |
| 16dp | Standard padding for sections |
| 24dp | Large padding between major sections |
| 32dp | Extra large padding for screen edges |

```kotlin
// ✅ CORRECT
Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
) { }

// ❌ WRONG: Arbitrary spacing
Column(
    modifier = Modifier.padding(15.dp),
    verticalArrangement = Arrangement.spacedBy(7.dp)
) { }
```

---

## Corner Radius (Shapes)

Material 3 defines shape scales:

| Radius | Usage |
|---|---|
| 0dp (square) | Minimal rounding — not used in M3 Expressive |
| 8dp | Small components (chips, small buttons) |
| 12dp | Medium components (cards, buttons) |
| 16dp | Large components (dialogs, sheets) |
| 28dp | Extra large components (FABs, rounded buttons) |

```kotlin
// ✅ CORRECT — Use MaterialTheme.shapes
Card(
    shape = MaterialTheme.shapes.medium, // 12dp
    modifier = Modifier.fillMaxWidth()
) { }

// ❌ WRONG — Hardcoded radius
Card(
    shape = RoundedCornerShape(8.dp),
    modifier = Modifier.fillMaxWidth()
) { }
```

---

## Elevation

Material 3 uses shadow elevation (not blur). Don't hardcode elevation — use Material 3 defaults:

```kotlin
// ✅ CORRECT
ElevatedCard(
    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
) { }

// ❌ WRONG: Custom shadows
Card(
    modifier = Modifier.shadow(elevation = 10.dp)
) { }
```

---

## Dark Mode

Always test in both light and dark themes:

```kotlin
@Composable
fun MyScreen() {
    val isDark = isSystemInDarkTheme()
    
    val containerColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }
}

// OR just use MaterialTheme — it handles dark mode automatically!
Box(
    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
) { }
```

---

## Accessibility

Material 3 components handle a11y by default, but verify:

- **Minimum touch target**: 48x48dp
- **Color contrast**: WCAG AA minimum (Material 3 defaults meet this)
- **Text scale**: Users can increase text size in system settings — respect it
- **Content descriptions**: All icons need `contentDescription`

```kotlin
// ✅ CORRECT
IconButton(
    onClick = { },
    modifier = Modifier.size(48.dp)
) {
    Icon(
        imageVector = Icons.Default.Menu,
        contentDescription = "Open navigation menu"
    )
}

// ❌ WRONG: Too small, missing description
Icon(
    imageVector = Icons.Default.Menu,
    modifier = Modifier.size(24.dp)
)
```

---

## Best Practices

1. **Use Material 3 composables exclusively** — never mix M2 and M3
2. **Never hardcode colors** — always use `MaterialTheme.colorScheme.*`
3. **Never hardcode typography** — always use `MaterialTheme.typography.*`
4. **Never hardcode spacing** — use standard Material 3 spacing (8, 12, 16, 24, 32dp)
5. **Test in both light and dark modes** — Material 3 handles it automatically
6. **Respect accessibility** — all components must be keyboard navigable and have descriptions
7. **Use dynamic color on Android 12+** — Material You wallpaper extraction is automatic with `dynamicColorScheme()`
8. **Group related content** with Cards and Surface composables
9. **Use elevation** for visual hierarchy (ElevatedCard for emphasis, OutlinedCard for flat)
10. **Keep layouts responsive** — use `fillMaxWidth()`, `weight()`, and `Arrangement` for flexibility

---

## Reference Links

- **Material 3 Spec**: https://m3.material.io/
- **Material 3 Components**: https://developer.android.com/develop/ui/compose/material3
- **Material You (Dynamic Color)**: https://developer.android.com/about/versions/12/features#material_you
- **Compose Documentation**: https://developer.android.com/jetpack/compose/documentation
