# File Structure

## Target Project Layout

```
app/src/main/
├── AndroidManifest.xml
├── java/dev/rcht/jist/
│   ├── JistApplication.kt                  # Application class, DB + DI init
│   ├── MainActivity.kt                     # Single activity, hosts Compose NavHost
│   │
│   ├── data/
│   │   ├── db/
│   │   │   ├── JistDatabase.kt             # Room database definition
│   │   │   ├── entity/
│   │   │   │   ├── NotificationEntity.kt   # Captured notification
│   │   │   │   ├── SummaryEntity.kt        # Generated summary
│   │   │   │   ├── AppRuleEntity.kt        # Per-app configuration
│   │   │   │   └── LlmConfigEntity.kt      # LLM provider configuration
│   │   │   └── dao/
│   │   │       ├── NotificationDao.kt      # Notification CRUD
│   │   │       ├── SummaryDao.kt           # Summary CRUD
│   │   │       ├── AppRuleDao.kt           # App rules CRUD
│   │   │       └── LlmConfigDao.kt         # LLM config CRUD
│   │   ├── preferences/
│   │   │   ├── JistPreferences.kt          # Preferences data class
│   │   │   └── PreferencesRepository.kt    # DataStore-backed prefs
│   │   └── repository/
│   │       ├── NotificationRepository.kt   # Notification data access
│   │       ├── SummaryRepository.kt        # Summary data access
│   │       └── LlmConfigRepository.kt      # LLM config data access
│   │
│   ├── service/
│   │   ├── JistNotificationListenerService.kt  # Captures notifications
│   │   ├── SummaryWorker.kt                    # WorkManager periodic worker
│   │   └── SummarizeActionReceiver.kt          # "Summarize" button handler
│   │
│   ├── llm/
│   │   ├── LlmClient.kt                   # Client interface
│   │   ├── LlmClientFactory.kt            # Factory for creating clients
│   │   ├── LlmConfig.kt                   # Config data class
│   │   ├── LlmResponse.kt                 # Response data class
│   │   ├── ChatMessage.kt                  # Message data class
│   │   ├── clients/
│   │   │   ├── OpenAiCompatibleClient.kt   # OpenAI / OpenRouter / Custom
│   │   │   ├── GeminiClient.kt             # Google Gemini
│   │   │   └── ClaudeClient.kt             # Anthropic Claude
│   │   └── PromptBuilder.kt               # Prompt template engine
│   │
│   ├── engine/
│   │   ├── SummaryEngine.kt               # Core summarization orchestrator
│   │   └── RuleEngine.kt                  # Per-app rule evaluation
│   │
│   ├── notification/
│   │   └── SummaryNotificationManager.kt  # Posts summary notifications
│   │
│   ├── ui/
│   │   ├── JistApp.kt                     # Root composable with NavHost
│   │   ├── navigation/
│   │   │   ├── Screen.kt                  # Sealed class of routes
│   │   │   └── JistNavHost.kt             # NavHost setup
│   │   ├── theme/
│   │   │   ├── Color.kt                   # Material 3 Expressive color tokens
│   │   │   ├── Theme.kt                   # JistTheme with dynamic color (Material You)
│   │   │   └── Type.kt                    # Material 3 Expressive typography scale
│   │   ├── components/                     # Reusable composables
│   │   │   ├── JistTopBar.kt              # Shared top app bar
│   │   │   ├── JistDrawer.kt              # Navigation drawer
│   │   │   ├── EmptyState.kt              # Empty list placeholder
│   │   │   └── LoadingIndicator.kt        # Loading spinner
│   │   ├── dashboard/
│   │   │   ├── DashboardScreen.kt         # Home screen with stats
│   │   │   └── DashboardViewModel.kt
│   │   ├── summaries/
│   │   │   ├── SummariesScreen.kt         # Summary list
│   │   │   ├── SummariesViewModel.kt
│   │   │   └── SummaryDetailScreen.kt     # Summary detail + original msgs
│   │   ├── notifications/
│   │   │   ├── NotificationLogScreen.kt   # Raw notification log
│   │   │   └── NotificationLogViewModel.kt
│   │   ├── settings/
│   │   │   ├── SettingsScreen.kt          # General settings
│   │   │   ├── ModelConfigScreen.kt       # LLM provider list
│   │   │   ├── ModelEditScreen.kt         # Add/edit LLM provider
│   │   │   ├── AppConfigScreen.kt         # Per-app rules list
│   │   │   ├── AppRuleEditScreen.kt       # Edit single app rule
│   │   │   ├── PromptConfigScreen.kt      # Prompt customization
│   │   │   └── GeneralSettingsScreen.kt   # Theme, data, privacy
│   │   ├── onboarding/
│   │   │   └── OnboardingScreen.kt        # First-launch wizard
│   │   └── about/
│   │       └── AboutScreen.kt             # Version, links, licenses
│   │
│   └── util/
│       ├── CryptoUtil.kt                  # API key encryption helpers
│       ├── NotificationParser.kt          # Extract data from StatusBarNotification
│       └── Extensions.kt                  # Kotlin extension functions
│
├── res/
│   ├── values/
│   │   ├── colors.xml                     # Fallback colors (Compose theme is primary)
│   │   ├── strings.xml                    # All user-facing strings
│   │   └── themes.xml                     # Base app theme (minimal, Compose handles UI)
│   ├── values-night/
│   │   └── themes.xml
│   ├── drawable/
│   │   └── ...                            # App icon assets
│   ├── mipmap-*/                          # Launcher icons
│   └── xml/
│       ├── backup_rules.xml
│       └── data_extraction_rules.xml
```

---

## Notes

- **Jetpack Compose** — all UI is built with Compose. No XML layouts for screens (only minimal XML for app theme and manifest).
- **Material 3 Expressive** — larger, bolder type scales and vibrant color tokens for modern visual hierarchy. Dynamic color on Android 12+ (Material You).
- **No Hilt initially** — manual dependency injection via `JistApplication` to keep the project lean. Hilt can be added later if complexity warrants it.
- **Single Activity** — `MainActivity` uses `setContent { JistTheme { JistApp() } }`. All screens are composable functions, not Fragments.
- **Compose Navigation** — routes defined in `Screen.kt` sealed class, navigation handled by `NavHost` in `JistNavHost.kt`.
- **Material 3 Expressive + Material You** — dynamic color extraction from wallpaper on Android 12+, custom Jist Expressive theme as fallback.
- **Reusable components** — shared UI elements live in `ui/components/` and are used across screens.
- **Each feature** gets its own package under `ui/` with `*Screen.kt` + `*ViewModel.kt`.
