# Architecture

## High-Level Overview

```
┌─────────────────────────────────────────────────┐
│                    UI Layer                      │
│  (Jetpack Compose + Material 3 Expressive)       │
│  Dynamic Color · Larger Type Scales · Vibrant    │
│  DashboardScreen · SummariesScreen ·             │
│  NotificationLogScreen · SettingsScreen ·        │
│  ModelConfigScreen · AppConfigScreen · About      │
├─────────────────────────────────────────────────┤
│                  Domain Layer                    │
│  SummaryEngine · RuleEngine · PromptBuilder      │
├─────────────────────────────────────────────────┤
│                  Data Layer                      │
│  Room DB · DataStore · LlmClient ·               │
│  NotificationListenerService                     │
├─────────────────────────────────────────────────┤
│              Android Platform                    │
│  NotificationListenerService · WorkManager ·     │
│  Foreground Service · POST_NOTIFICATIONS         │
└─────────────────────────────────────────────────┘
```

---

## Key Components

### Service Layer

| Component | Role |
|---|---|
| `JistNotificationListenerService` | Captures all notifications via Android's `NotificationListenerService` API. Extracts app, title, content, and conversation key. Inserts into Room DB. |
| `SummaryWorker` | WorkManager-based periodic worker. Checks for unsummarized notification batches and triggers `SummaryEngine`. |
| `SummarizeActionReceiver` | BroadcastReceiver handling the "Summarize" action button tap from manual-mode notifications. |

### Engine Layer

| Component | Role |
|---|---|
| `SummaryEngine` | Core orchestrator. Fetches unsummarized notifications, checks rules, builds prompts, calls LLM, stores summaries. |
| `RuleEngine` | Evaluates per-app rules (auto/manual/disabled, batch window, min messages) to decide when and how to summarize. |
| `PromptBuilder` | Constructs system + user prompts using templates with variable substitution (`{app}`, `{contact}`, `{count}`, `{timeRange}`). |

### LLM Layer

| Component | Role |
|---|---|
| `LlmClient` | Interface for all LLM interactions: `suspend fun complete(messages, config): LlmResponse` |
| `OpenAiCompatibleClient` | Handles OpenAI, OpenRouter, and any OpenAI-compatible endpoint. |
| `GeminiClient` | Google Gemini API with its unique request/response format. |
| `ClaudeClient` | Anthropic Claude API with `x-api-key` auth and Messages API format. |
| `LlmClientFactory` | Creates the correct client implementation based on provider enum. |

### Data Layer

| Component | Role |
|---|---|
| `JistDatabase` | Room database with all entities and DAOs. |
| `NotificationRepository` | CRUD for captured notifications. |
| `SummaryRepository` | CRUD for generated summaries. |
| `LlmConfigRepository` | CRUD for LLM provider configurations. |
| `PreferencesRepository` | DataStore-backed global preferences. |

### Notification Layer

| Component | Role |
|---|---|
| `SummaryNotificationManager` | Posts summary notifications, "Summarize" prompt notifications, and manages notification channels. |

---

## Data Flow

### Auto-Summarize Flow

```
Notification arrives
    │
    ▼
JistNotificationListenerService.onNotificationPosted()
    │
    ├── Extract: packageName, title, text, timestamp
    ├── Derive conversationKey (packageName:title)
    ├── Check: is this app enabled? (RuleEngine)
    │
    ▼
NotificationRepository.insert(notification)
    │
    ▼
SummaryWorker (periodic, every ~15 min)
    │
    ├── Query unsummarized notifications grouped by conversationKey
    ├── For each group, check RuleEngine:
    │     ├── Mode = AUTO? → proceed
    │     ├── Mode = MANUAL? → post "Summarize" prompt notification
    │     └── Mode = DISABLED? → skip
    ├── Check batch window & min message threshold
    │
    ▼
SummaryEngine.summarize(conversationKey)
    │
    ├── Fetch notifications from DB
    ├── Build prompt (PromptBuilder)
    ├── Get active LLM config
    ├── Call LlmClient.complete()
    ├── Store summary in DB
    ├── Mark notifications as summarized
    │
    ▼
SummaryNotificationManager.postSummary()
    │
    └── Android notification with summary text
```

### Manual Summarize Flow

```
Notification batch accumulates
    │
    ▼
SummaryWorker detects batch (mode = MANUAL)
    │
    ▼
SummaryNotificationManager.postSummarizePrompt()
    │
    └── Notification: "12 messages from Project Team"
        └── Action button: "📋 Summarize"
                │
                ▼
        User taps "Summarize"
                │
                ▼
        SummarizeActionReceiver
                │
                ▼
        SummaryEngine.summarize(conversationKey)
                │
                ▼
        Replace notification with summary
```

---

## Threading Model

| Operation | Thread/Dispatcher |
|---|---|
| NotificationListenerService callbacks | Main thread → offload DB writes to `Dispatchers.IO` |
| SummaryWorker | WorkManager's coroutine dispatcher |
| LLM API calls | `Dispatchers.IO` with timeout |
| Room DB operations | `Dispatchers.IO` (Room handles internally) |
| UI updates | `Dispatchers.Main` via ViewModel + StateFlow, collected in Compose with `collectAsStateWithLifecycle()` |

---

## Security Model

- **API keys** are encrypted using AndroidKeyStore + EncryptedSharedPreferences (or encrypted Room fields)
- **No data leaves the device** except notification text sent to the user's chosen LLM API
- **No analytics or tracking** — fully local app
- **Notification access** is a sensitive permission — clearly explained in onboarding
- **Auto-delete options** for raw notifications and summaries after configurable retention periods
