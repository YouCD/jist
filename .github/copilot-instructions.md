# Copilot Instructions for Jist

This document outlines the key patterns, conventions, and workflows for contributing to Jist. Familiarize yourself with the architecture and build process before making changes.

---

## Build & Test Commands

```bash
./gradlew clean build -x test   # Quick build (skip tests)
./gradlew build                 # Build + tests
./gradlew assembleDebug         # Debug APK only
./gradlew lint                  # Run linter
./gradlew test                  # Unit tests
./gradlew connectedAndroidTest  # Instrumented tests (device required)
```

---

## Project Structure & Architecture

### High-Level Layers

Jist follows a **layered architecture**:

1. **UI Layer** (Jetpack Compose + ViewModels)
   - Located in `app/src/main/java/dev/rcht/jist/ui/`
   - Each feature has its own subdirectory: `dashboard/`, `summaries/`, `settings/`, `notifications/`, `about/`
   - Pure Compose UI — Material 3 Expressive with dynamic color (Material You on Android 12+)
   - Larger, bolder type scales for modern visual hierarchy
   - Navigation via Compose Navigation (`NavHost` + `NavController`)
   - Theme defined in `ui/theme/` (Color.kt, Theme.kt, Type.kt)

2. **Domain Layer** (Business Logic)
   - `SummaryEngine`, `RuleEngine`, `PromptBuilder`
   - Location: `app/src/main/java/dev/rcht/jist/engine/`

3. **Data Layer** (Database & APIs)
   - Room database: `data/db/JistDatabase.kt`
   - Entities in `data/db/entity/`, DAOs in `data/db/dao/`
   - Repositories in `data/repository/`
   - Preferences: `data/preferences/PreferencesRepository.kt` (DataStore-backed)

4. **Service Layer** (Background & Platform Integration)
   - `JistNotificationListenerService` — captures notifications
   - `SummaryWorker` — WorkManager periodic task
   - Location: `app/src/main/java/dev/rcht/jist/service/`

5. **LLM Layer** (Multi-Model LLM Support)
   - Unified client interface: `llm/LlmClient.kt`
   - Provider implementations: `llm/clients/` (OpenAI, Gemini, Claude, OpenRouter)
   - Factory pattern: `llm/LlmClientFactory.kt`

### Current Project State

**Phase 1 (Foundation)** ✅ Complete — DB, NotificationListenerService, basic UI scaffolding
**Phase 1.5 (Compose Migration)** 🔄 — Migrating from XML/ViewBinding to Jetpack Compose
**Phase 2 (LLM Integration)** ⏳ Next

Detailed phase breakdown: see `docs/IMPLEMENTATION_PLAN.md`

---

## Jetpack Compose Conventions

### Screen Pattern
Each screen is a composable function, not a Fragment. Screens live under `ui/{feature}/`:

```kotlin
// ui/dashboard/DashboardScreen.kt
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DashboardContent(
        uiState = uiState,
        onAction = viewModel::onAction
    )
}

// Stateless composable for previews and testing
@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    onAction: (DashboardAction) -> Unit
) {
    // Material 3 composables here
}
```

### ViewModel Pattern
ViewModels expose **StateFlow** (not LiveData). Use sealed classes for UI state:

```kotlin
class DashboardViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
}

data class DashboardUiState(
    val notificationCount: Int = 0,
    val summaryCount: Int = 0,
    val isListenerActive: Boolean = false
)
```

### Navigation
Use Compose Navigation with a sealed class for routes:

```kotlin
sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Summaries : Screen("summaries")
    object Settings : Screen("settings")
    object NotificationLog : Screen("notification_log")
    object About : Screen("about")
}
```

`MainActivity` hosts a single `setContent { JistApp() }` composable containing the `NavHost`.

### Theming
- **Material 3 Expressive** design system with dynamic color (Material You on Android 12+)
- Larger, bolder type scales for modern visual hierarchy
- Vibrant color tokens for interactive elements
- Fallback to custom Jist Expressive theme on older devices
- Theme composables in `ui/theme/Theme.kt`
- Always use `MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*`, `MaterialTheme.shapes.*`
- Never hardcode colors or text sizes
- Support light/dark with `isSystemInDarkTheme()`

**Material 3 Expressive specifics:**
- Use `displayLarge` / `displayMedium` / `displaySmall` for headlines (generous spacing, bolder weights)
- Use `headlineLarge` / `headlineMedium` for section titles
- Use `bodyLarge` / `bodyMedium` for body text (expressive sizes)
- Use `labelLarge` / `labelMedium` for buttons and labels
- Shapes: `roundedShape` with generous corner radius for modern look
- Color tokens: leverage `primary`, `secondary`, `tertiary` for visual richness

### Lists
Use `LazyColumn` / `LazyRow` instead of RecyclerView:

```kotlin
LazyColumn {
    items(notifications, key = { it.id }) { notification ->
        NotificationItem(notification)
    }
}
```

### State Hoisting
Always hoist state up. Composables should be stateless where possible:
- Screen composables collect state from ViewModel
- Inner composables receive state + callbacks as parameters
- This enables `@Preview` on inner composables

---

## Database & Repositories

### Room Setup
- **Database class**: `JistDatabase.kt` — singleton, initialized in `JistApplication`
- **Version**: 1 (no migrations yet)
- **Entities**: 4 tables (Notifications, Summaries, AppRules, LlmConfigs)
- **DAOs**: One DAO per entity, all suspend functions

### Data Flow
Always: Composable → ViewModel → Repository → DAO → Room DB

```kotlin
// ✅ CORRECT
class MyViewModel : ViewModel() {
    private val repo = // from JistApplication
    val items = repo.getItemsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

// ❌ WRONG: Don't access DAO from composables
```

### Getting Repositories
Access via `JistApplication` singleton:
```kotlin
val app = context.applicationContext as JistApplication
val notificationRepo = app.notificationRepository
```

### Preferences (Global Settings)
- DataStore-backed in `data/preferences/PreferencesRepository.kt`
- Use `preferencesRepository.preferencesFlow` — Flow-based, reactive
- Collect in ViewModel with `collectAsStateWithLifecycle()` in composables

---

## Notification Listener Service

### How It Works
1. User grants "Notification Access" in Android settings
2. `JistNotificationListenerService` receives `onNotificationPosted()` callbacks
3. Extracts: packageName, title, content, timestamp
4. Derives `conversationKey` = `packageName:title` for grouping
5. Inserts into DB asynchronously via coroutines

### Filtering
Ignores: own package, foreground services, system packages (`com.android.*`, `android.*`, `com.google.android.gms`), notifications without title/content.

### Adding New App Support
No code changes needed — the service processes all apps. Per-app configuration is done via the Settings UI.

---

## Kotlin & Code Style

### Language
- **Kotlin 2.0.21**, Java 11 target
- Use **coroutines** everywhere for async (`launch`, `async`, suspend)
- Prefer **StateFlow** over LiveData in ViewModels
- Use **data classes** for state objects and entities

### Naming Conventions
| Type | Pattern | Example |
|---|---|---|
| Entity | `*Entity` | `NotificationEntity` |
| DAO | `*Dao` | `NotificationDao` |
| Repository | `*Repository` | `NotificationRepository` |
| ViewModel | `*ViewModel` | `DashboardViewModel` |
| Screen composable | `*Screen` | `DashboardScreen` |
| UI state | `*UiState` | `DashboardUiState` |
| Service | `*Service` / `*Worker` | `JistNotificationListenerService` |

---

## Key Patterns

### Manual Dependency Injection
- `JistApplication` initializes and holds all repositories
- ViewModels get repos from application context
- No Hilt — keeps the project lean

### Conversation Grouping
Notifications grouped by `conversationKey = packageName:title`:
- WhatsApp group → unique key per group name
- Gmail → unique key per thread subject
- Used for batching before summarization

### Notification Channels
```kotlin
const val CHANNEL_SUMMARIES = "jist_summaries"
const val CHANNEL_SUMMARIZE_PROMPT = "jist_summarize_prompt"
const val CHANNEL_SERVICE = "jist_service"
```

---

## Lint

`ProtectedPermissions` lint check is disabled in `app/build.gradle.kts` — `BIND_NOTIFICATION_LISTENER_SERVICE` is a legitimate use, not a false positive.

---

## Important Notes

### API Key Security
- Encrypt with AndroidKeyStore + EncryptedSharedPreferences
- Never log or include in crash reports

### Permissions
- `BIND_NOTIFICATION_LISTENER_SERVICE` — capture notifications
- `POST_NOTIFICATIONS` — post summaries (Android 13+)

### Material 3 Only
- Use Material 3 composables exclusively (`androidx.compose.material3.*`)
- Never import `material` (M2) — only `material3`
- Use `MaterialTheme.colorScheme`, `MaterialTheme.typography`

---

## References

- **Architecture**: `docs/ARCHITECTURE.md`
- **Database schema**: `docs/DATABASE_SCHEMA.md`
- **LLM integration**: `docs/LLM_INTEGRATION.md`
- **Implementation plan**: `docs/IMPLEMENTATION_PLAN.md`
- **Material 3 Expressive Design**: `docs/MATERIAL3_EXPRESSIVE.md` ← UI guidelines, typography, colors, components
- **Feature ideas**: `docs/FEATURE_IDEAS.md`
