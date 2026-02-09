# File Structure

## Target Project Layout

```
app/src/main/
├── AndroidManifest.xml
├── java/dev/rcht/jist/
│   ├── JistApplication.kt                  # Application class, DB + DI init
│   ├── MainActivity.kt                     # Main activity with navigation drawer
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
│   │   ├── dashboard/
│   │   │   ├── DashboardFragment.kt       # Home screen with stats
│   │   │   └── DashboardViewModel.kt
│   │   ├── summaries/
│   │   │   ├── SummariesFragment.kt       # Summary list
│   │   │   ├── SummariesViewModel.kt
│   │   │   └── SummaryDetailFragment.kt   # Summary detail + original msgs
│   │   ├── notifications/
│   │   │   ├── NotificationLogFragment.kt # Raw notification log
│   │   │   └── NotificationLogViewModel.kt
│   │   ├── settings/
│   │   │   ├── SettingsFragment.kt        # General settings
│   │   │   ├── ModelConfigFragment.kt     # LLM provider list
│   │   │   ├── ModelEditFragment.kt       # Add/edit LLM provider
│   │   │   ├── AppConfigFragment.kt       # Per-app rules list
│   │   │   ├── AppRuleEditFragment.kt     # Edit single app rule
│   │   │   ├── PromptConfigFragment.kt    # Prompt customization
│   │   │   └── GeneralSettingsFragment.kt # Theme, data, privacy
│   │   ├── onboarding/
│   │   │   └── OnboardingActivity.kt      # First-launch wizard
│   │   └── about/
│   │       └── AboutFragment.kt           # Version, links, licenses
│   │
│   └── util/
│       ├── CryptoUtil.kt                  # API key encryption helpers
│       ├── NotificationParser.kt          # Extract data from StatusBarNotification
│       └── Extensions.kt                  # Kotlin extension functions
│
├── res/
│   ├── layout/
│   │   ├── activity_main.xml
│   │   ├── app_bar_main.xml
│   │   ├── content_main.xml
│   │   ├── nav_header_main.xml
│   │   ├── fragment_dashboard.xml
│   │   ├── fragment_summaries.xml
│   │   ├── fragment_summary_detail.xml
│   │   ├── fragment_notification_log.xml
│   │   ├── fragment_settings.xml
│   │   ├── fragment_model_config.xml
│   │   ├── fragment_model_edit.xml
│   │   ├── fragment_app_config.xml
│   │   ├── fragment_app_rule_edit.xml
│   │   ├── fragment_prompt_config.xml
│   │   ├── fragment_general_settings.xml
│   │   ├── fragment_about.xml
│   │   ├── activity_onboarding.xml
│   │   ├── item_notification.xml          # RecyclerView item
│   │   ├── item_summary.xml               # RecyclerView item
│   │   ├── item_app_rule.xml              # RecyclerView item
│   │   └── item_llm_config.xml            # RecyclerView item
│   ├── navigation/
│   │   └── mobile_navigation.xml
│   ├── menu/
│   │   ├── activity_main_drawer.xml
│   │   └── main.xml
│   ├── values/
│   │   ├── colors.xml
│   │   ├── strings.xml
│   │   ├── themes.xml
│   │   └── dimens.xml
│   ├── values-night/
│   │   └── themes.xml
│   ├── drawable/
│   │   └── ...                            # Icons, backgrounds
│   └── xml/
│       ├── backup_rules.xml
│       └── data_extraction_rules.xml
```

---

## Notes

- **No Hilt initially** — manual dependency injection via `JistApplication` to keep the project lean. Hilt can be added later if complexity warrants it.
- **ViewBinding** is already enabled — all UI uses generated binding classes, no `findViewById`.
- **Navigation Component** handles all fragment transitions via `mobile_navigation.xml`.
- **Each feature** gets its own package under `ui/` with Fragment + ViewModel pair.
