# Implementation Plan

## Phased Roadmap

---

## Phase 1: Foundation & Notification Capture
> Core plumbing — capture notifications, store them, show them in UI.

- [ ] **1.1 Project restructure**
  - Rename template fragments: Home → Dashboard, Gallery → Summaries, Slideshow → Settings
  - Update navigation graph, menu, and strings
  - Add new nav items: Notification Log, About
  - Update app theme colors (teal/dark theme for "Jist" brand)

- [ ] **1.2 Add Room database**
  - Add Room + KSP dependencies to `libs.versions.toml` and `build.gradle.kts`
  - Create entities: `NotificationEntity`, `SummaryEntity`, `AppRuleEntity`, `LlmConfigEntity`
  - Create DAOs: `NotificationDao`, `SummaryDao`, `AppRuleDao`, `LlmConfigDao`
  - Create `JistDatabase` (Room database class)

- [ ] **1.3 Add DataStore for preferences**
  - Add DataStore dependency
  - Create `JistPreferences` data class (global settings: default mode, batch window, theme)
  - Create `PreferencesRepository` with DataStore backing

- [ ] **1.4 Implement NotificationListenerService**
  - Create `JistNotificationListenerService` extending `NotificationListenerService`
  - Register in `AndroidManifest.xml` with `BIND_NOTIFICATION_LISTENER_SERVICE` permission
  - Implement `onNotificationPosted()`:
    - Extract: packageName, title, text, key, timestamp
    - Filter: skip own notifications, skip system/ongoing notifications
    - Derive `conversationKey` from packageName + title
    - Insert into Room DB via `NotificationRepository`
  - Add permission grant flow in UI (redirect to notification access settings)

- [ ] **1.5 Notification Log UI**
  - Create `NotificationLogFragment` + `NotificationLogViewModel`
  - RecyclerView showing captured notifications, grouped by app
  - Search/filter by app, contact, date range
  - Swipe to delete / bulk clear

- [ ] **1.6 Dashboard UI**
  - Stats: total notifications captured today, summaries generated, active apps
  - Quick-access cards: "Configure Apps", "View Summaries", notification listener status
  - Warning banner if notification access not granted

---

## Phase 2: LLM Integration & Summarization Engine
> The brain — connect to AI models and generate summaries.

- [ ] **2.1 LLM Client layer**
  - Add OkHttp dependency
  - Create `LlmClient` interface:
    ```kotlin
    interface LlmClient {
        suspend fun complete(messages: List<ChatMessage>, config: LlmConfig): LlmResponse
    }
    ```
  - Implement `OpenAiCompatibleClient`:
    - POST to `{baseUrl}/v1/chat/completions`
    - Works for OpenAI, OpenRouter, and any compatible API
  - Implement `GeminiClient`:
    - POST to `{baseUrl}/v1beta/models/{model}:generateContent`
    - Handle Gemini-specific safety settings
  - Implement `ClaudeClient`:
    - POST to `{baseUrl}/v1/messages`
    - `x-api-key` + `anthropic-version` headers
  - Create `LlmClientFactory`
  - Error handling: rate limits, auth errors, network, timeout
  - Retry with exponential backoff

- [ ] **2.2 Prompt system**
  - Create `PromptBuilder` class
  - Default system prompt for notification summarization
  - User message: numbered list of notifications with timestamps
  - Support custom prompt templates per app
  - Prompt variables: `{app}`, `{contact}`, `{count}`, `{timeRange}`

- [ ] **2.3 Summary Engine**
  - Create `SummaryEngine`:
    1. Fetch unsummarized notifications for a conversationKey
    2. Check minimum message threshold
    3. Build prompt via `PromptBuilder`
    4. Call LLM via `LlmClient`
    5. Store summary in DB
    6. Mark notifications as summarized
  - `summarizeAll()`: batch summarize all pending conversations
  - Error handling with retry capability

- [ ] **2.4 Auto-summarize with WorkManager**
  - Create `SummaryWorker` (CoroutineWorker)
  - Periodic check for unsummarized notification batches
  - Respect per-app `batchWindowMinutes` and `minMessagesForSummary`
  - Configurable interval (default: 15 min)
  - Network-required constraint

- [ ] **2.5 Manual summarize flow**
  - When mode = MANUAL, post notification with "📋 Summarize" action button
  - `SummarizeActionReceiver` handles button tap
  - Triggers `SummaryEngine.summarize()` → replaces notification with summary

- [ ] **2.6 Summary notification posting**
  - Create `SummaryNotificationManager`
  - `NotificationCompat.BigTextStyle` for summaries
  - Group summaries by app
  - Notification channels: "Summaries", "Summarize Prompts", "Service"
  - Handle Android 13+ POST_NOTIFICATIONS permission

---

## Phase 3: Settings & Configuration UI
> Full user control — configure everything.

- [ ] **3.1 Model Configuration screen**
  - List configured LLM providers
  - Add/Edit/Delete model configurations
  - Form: name, provider dropdown, API key (masked), base URL (pre-filled), model ID, max tokens, temperature slider
  - "Test Connection" button
  - Set default model
  - Pre-filled defaults per provider:
    - OpenAI: `https://api.openai.com` / `gpt-4o-mini`
    - Gemini: `https://generativelanguage.googleapis.com` / `gemini-2.0-flash`
    - Claude: `https://api.anthropic.com` / `claude-3-5-haiku-20241022`
    - OpenRouter: `https://openrouter.ai/api` / user picks
    - Custom: user fills everything

- [ ] **3.2 App Configuration screen**
  - List installed apps (or apps that have sent notifications)
  - Toggle enable/disable per app
  - Per-app: mode (Auto/Manual/Disabled), batch window, min messages, custom prompt
  - Quick presets: "WhatsApp optimized", "Email optimized"
  - Popular app icon recognition

- [ ] **3.3 General Settings screen**
  - Global defaults: mode, batch window, min messages
  - Notification settings: style, sound, vibration
  - Data management: clear notifications, clear summaries, export
  - Privacy: auto-delete raw notifications after summarizing
  - Theme: follow system / light / dark

- [ ] **3.4 Prompt Customization screen**
  - Edit default system prompt
  - Edit per-app prompts
  - Preview with sample data
  - Reset to default

---

## Phase 4: Enhanced Summarization
> Smarter summaries and better UX.

- [ ] **4.1 Conversation-aware grouping**
  - WhatsApp: group by group name vs individual
  - Gmail: group by thread subject
  - Telegram: group by chat
  - Slack: group by channel
  - Smart key extraction using notification extras (MessagingStyle, etc.)

- [ ] **4.2 Smart batching**
  - Time-based: every N minutes
  - Count-based: after N messages
  - Activity-based: when message flow stops
  - Priority-based: immediate for urgent notifications
  - DND-aware: batch during DND, summarize when it ends

- [ ] **4.3 Summary quality improvements**
  - Include sender names in group chats
  - Detect action items / questions directed at user
  - Detect urgency level
  - Language detection → summarize in preferred language

- [ ] **4.4 Summary history & search**
  - Summaries list with filtering by app, date, contact
  - Full-text search
  - Tap to see original notifications
  - Re-summarize, share, copy

---

## Phase 5: Advanced Features
> Power-user features.

- [ ] **5.1 Quick Reply from Summary**
  - "Reply" action on summary notification
  - LLM-drafted reply suggestions
  - Send via RemoteInput back to original app

- [ ] **5.2 Daily/Weekly Digest**
  - Scheduled digest across all apps
  - Configurable schedule
  - Expandable per-app sections

- [ ] **5.3 Priority & Focus Mode**
  - AI priority classification (urgent / important / FYI / spam)
  - Only notify for urgent/important
  - Focus mode: suppress during focus, deliver digest after

- [ ] **5.4 Widgets & Quick Settings**
  - Home screen widget: recent summaries
  - Quick Settings tile: toggle on/off
  - App shortcuts

- [ ] **5.5 Cost tracking**
  - Tokens used per model per day
  - Estimated cost calculator
  - Usage graphs
  - Budget alerts

- [ ] **5.6 Backup & Sync**
  - Export/import settings as JSON
  - Auto-backup to device storage

- [ ] **5.7 Accessibility & Localization**
  - TalkBack support
  - RTL layout support
  - Localization-ready strings

---

## Phase 6: Polish & Open Source Readiness
> Ship it.

- [ ] **6.1 Onboarding flow**
  - First-launch wizard: welcome → notification access → POST_NOTIFICATIONS → API key → app selection → mode
  - Skip option

- [ ] **6.2 Error handling & resilience**
  - Graceful API failure messages
  - Offline queue for pending summarizations
  - Battery optimization handling
  - Service restart recovery

- [ ] **6.3 Security & Privacy**
  - Encrypt API keys (AndroidKeyStore)
  - Auto-delete options (raw notifications, summaries)
  - No analytics/tracking
  - Privacy policy in-app

- [ ] **6.4 Open source setup**
  - LICENSE, README.md, CONTRIBUTING.md
  - GitHub Issue templates
  - GitHub Actions CI
  - F-Droid metadata

- [ ] **6.5 Testing**
  - Unit tests: SummaryEngine, PromptBuilder, LlmClient, RuleEngine
  - Integration tests: Room DAOs
  - UI tests: onboarding, settings, summarization flows
  - ProGuard rules, baseline profiles, LeakCanary

---

## MVP = Phase 1 + Phase 2

A shippable v0.1 that captures notifications, summarizes them with any LLM, and posts summary notifications. Phase 3 makes it configurable. Phase 4-6 iterate based on feedback.
