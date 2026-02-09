# Jist — AI Notification Summarizer for Android

## Overview

**Jist** is an open-source, fully configurable AI-powered notification summarizer for Android. It intercepts notifications from apps like WhatsApp, Telegram, Gmail, Slack, etc., batches them per contact/group/thread, and uses LLM APIs (Gemini, OpenAI, Claude, OpenRouter, or any OpenAI-compatible endpoint) to generate concise summaries.

Users can choose between **auto-summarize** mode (hands-free) or **manual mode** (a "Summarize" action button appears on notifications).

---

## Key Features

- 🔔 **Notification Capture** — Listens to all app notifications via `NotificationListenerService`
- 🤖 **Multi-Model LLM Support** — OpenAI, Gemini, Claude, OpenRouter, and any OpenAI-compatible API
- ⚙️ **Fully Configurable** — Per-app rules, custom prompts, batch windows, model selection
- 📋 **Auto & Manual Modes** — Auto-summarize in the background, or tap a "Summarize" button
- 📱 **Per-App Rules** — Different settings for WhatsApp, Telegram, Gmail, etc.
- 🔑 **Bring Your Own Key** — Users provide their own API keys, no backend needed
- 🔒 **Privacy First** — All processing on-device, only notification text sent to chosen LLM
- 🌐 **Open Source** — Apache 2.0 / GPL v3 licensed

---

## Documentation

| Document | Description |
|---|---|
| [Architecture](./ARCHITECTURE.md) | System architecture, components, and data flow |
| [Implementation Plan](./IMPLEMENTATION_PLAN.md) | Phased roadmap with detailed tasks |
| [Database Schema](./DATABASE_SCHEMA.md) | Room database tables and relationships |
| [LLM Integration](./LLM_INTEGRATION.md) | How multi-model LLM support works |
| [File Structure](./FILE_STRUCTURE.md) | Target project file/folder organization |
| [Feature Ideas](./FEATURE_IDEAS.md) | Future features and community suggestions |

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin |
| UI | XML + ViewBinding, Material 3 |
| Navigation | AndroidX Navigation |
| Database | Room |
| Preferences | DataStore |
| Networking | OkHttp |
| Background work | WorkManager |
| Security | AndroidKeyStore + EncryptedSharedPreferences |
| Serialization | kotlinx.serialization |
| Min SDK | 29 (Android 10) |
