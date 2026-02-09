# Feature Ideas

## Planned Features (Phase 4-6)

### Smart Summarization
- **Conversation-aware grouping** — Group WhatsApp by group/contact, Gmail by thread, Telegram by chat, Slack by channel
- **Smart batching** — Time-based, count-based, activity-based (summarize when flow stops), DND-aware
- **Urgency detection** — AI classifies notifications as urgent/important/FYI/spam
- **Action item extraction** — Highlight questions and tasks directed at the user
- **Multi-language support** — Detect language, summarize in user's preferred language

### Productivity
- **Quick Reply** — Draft reply suggestions using LLM, send via RemoteInput back to original app
- **Daily/Weekly Digest** — Scheduled summary across all apps ("Here's what happened today")
- **Focus Mode** — Suppress all summaries during focus periods, deliver digest after
- **Calendar integration** — Detect meeting/event mentions, offer to create calendar events
- **Snippet extraction** — Auto-extract phone numbers, addresses, links, dates

### Widgets & OS Integration
- **Home screen widget** — Recent summaries at a glance
- **Quick Settings tile** — Toggle Jist on/off
- **App shortcuts** — "Summarize Now", "Open Summaries"
- **Wear OS companion** — View summaries on smartwatch

### Analytics & Cost Management
- **Token usage tracking** — Per model, per day
- **Cost calculator** — Estimated spend based on known model pricing
- **Usage graphs** — Notifications/day, summaries/day, tokens/day
- **Budget alerts** — Warn when approaching user-set spending limit
- **Model comparison** — Send same batch to multiple models, compare quality vs cost

### Data & Privacy
- **Export/Import settings** — Share configurations as JSON between devices
- **Auto-backup** — Settings, rules, and prompts backed up to device storage
- **Auto-delete** — Configurable retention for raw notifications and summaries
- **Encrypted storage** — API keys encrypted via AndroidKeyStore

---

## Future / Community Ideas

| Feature | Description | Complexity |
|---|---|---|
| **Local LLM support** | Run Ollama or llama.cpp on-device — fully offline, no API key needed | High |
| **Notification filtering rules** | Regex or keyword-based rules to auto-ignore certain notifications | Medium |
| **Smart categories** | Auto-categorize: Work, Personal, Shopping, Finance, Social | Medium |
| **Cross-app threading** | Link related notifications across apps (GitHub PR + Slack discussion) | High |
| **Tasker/Automate integration** | Expose intents for automation apps to trigger summarization | Low |
| **Media attachment handling** | Detect and note image/video/document attachments in summaries | Medium |
| **Notification timeline** | Visual chronological view of all notifications and summaries | Medium |
| **AI model benchmarking** | Compare multiple models on same batch — quality, speed, cost | Medium |
| **Custom actions** | User-defined actions on summaries (mark as TODO, forward to email) | Medium |
| **Per-contact rules** | VIP contacts always notify immediately, others batch | Medium |
| **Summary templates** | Different output formats: bullet points, paragraph, TL;DR + details | Low |
| **Notification sound customization** | Different sounds for different urgency levels | Low |
| **Statistics dashboard** | Deep analytics: busiest apps, chattiest contacts, peak hours | Medium |
| **Conversation insights** | AI-generated insights: "This group is 60% planning, 30% questions" | Medium |
| **Shared summaries** | Send summary as formatted message to another app/contact | Low |

---

## Contributing Ideas

Have a feature idea? Open a GitHub Issue with:
- **Title**: `[Feature] Brief description`
- **Description**: What the feature does and why it's useful
- **Use case**: A real-world scenario where this helps
- **Complexity estimate**: Low / Medium / High

Community voting (👍 reactions) helps prioritize what gets built next.
