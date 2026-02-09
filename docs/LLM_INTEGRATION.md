# LLM Integration

## Overview

Jist supports multiple LLM providers through a unified client interface. Users bring their own API keys and can configure multiple providers, switching between them freely.

---

## Supported Providers

| Provider | API Format | Auth Method | Base URL |
|---|---|---|---|
| **OpenAI** | OpenAI Chat Completions | `Authorization: Bearer {key}` | `https://api.openai.com` |
| **Google Gemini** | Gemini GenerateContent | `?key={key}` query param | `https://generativelanguage.googleapis.com` |
| **Anthropic Claude** | Claude Messages | `x-api-key: {key}` | `https://api.anthropic.com` |
| **OpenRouter** | OpenAI-compatible | `Authorization: Bearer {key}` | `https://openrouter.ai/api` |
| **Custom** | OpenAI-compatible | `Authorization: Bearer {key}` | User-provided |

---

## Client Interface

```kotlin
data class ChatMessage(
    val role: String,    // "system", "user", "assistant"
    val content: String
)

data class LlmResponse(
    val content: String,
    val model: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?
)

interface LlmClient {
    suspend fun complete(
        messages: List<ChatMessage>,
        config: LlmConfig
    ): Result<LlmResponse>
}
```

---

## Provider Implementations

### OpenAI-Compatible Client
Used for: **OpenAI**, **OpenRouter**, **Custom**, and any OpenAI-compatible endpoint.

**Request:**
```
POST {baseUrl}/v1/chat/completions
Authorization: Bearer {apiKey}
Content-Type: application/json

{
    "model": "gpt-4o-mini",
    "messages": [
        {"role": "system", "content": "..."},
        {"role": "user", "content": "..."}
    ],
    "max_tokens": 512,
    "temperature": 0.3
}
```

**Response:**
```json
{
    "choices": [
        {
            "message": {
                "role": "assistant",
                "content": "Here's your summary..."
            }
        }
    ],
    "usage": {
        "prompt_tokens": 150,
        "completion_tokens": 80,
        "total_tokens": 230
    }
}
```

### Gemini Client

**Request:**
```
POST {baseUrl}/v1beta/models/{modelId}:generateContent?key={apiKey}
Content-Type: application/json

{
    "contents": [
        {
            "role": "user",
            "parts": [{"text": "..."}]
        }
    ],
    "systemInstruction": {
        "parts": [{"text": "..."}]
    },
    "generationConfig": {
        "maxOutputTokens": 512,
        "temperature": 0.3
    }
}
```

**Response:**
```json
{
    "candidates": [
        {
            "content": {
                "parts": [{"text": "Here's your summary..."}],
                "role": "model"
            }
        }
    ],
    "usageMetadata": {
        "promptTokenCount": 150,
        "candidatesTokenCount": 80,
        "totalTokenCount": 230
    }
}
```

### Claude Client

**Request:**
```
POST {baseUrl}/v1/messages
x-api-key: {apiKey}
anthropic-version: 2023-06-01
Content-Type: application/json

{
    "model": "claude-3-5-haiku-20241022",
    "max_tokens": 512,
    "system": "You are a notification summarizer...",
    "messages": [
        {"role": "user", "content": "..."}
    ]
}
```

**Response:**
```json
{
    "content": [
        {
            "type": "text",
            "text": "Here's your summary..."
        }
    ],
    "usage": {
        "input_tokens": 150,
        "output_tokens": 80
    }
}
```

---

## OpenRouter Specifics

OpenRouter uses the OpenAI-compatible format but requires an additional header:

```
HTTP-Referer: https://github.com/user/jist
X-Title: Jist
```

OpenRouter provides access to 100+ models from various providers through a single API key and endpoint. Users select their desired model from OpenRouter's catalog.

---

## Prompt System

### Default System Prompt

```
You are Jist, a notification summarizer. You will receive a list of notifications 
from {app} for the conversation "{contact}". 

Produce a concise summary that:
- Captures the key points and important information
- Highlights any action items or questions directed at the user
- Notes any urgent or time-sensitive items
- Is brief but complete (aim for 2-4 sentences)

Do not include greetings or meta-commentary. Just provide the summary.
```

### User Message Format

```
{count} notifications from {app} — {contact} ({timeRange}):

1. [10:30 AM] Hey, are we still meeting at 3?
2. [10:32 AM] I have the presentation ready
3. [10:45 AM] Also, can you review the PR before the meeting?
4. [11:00 AM] John: I'll join too
5. [11:02 AM] John: Bringing the design mockups
```

### Prompt Variables

| Variable | Description | Example |
|---|---|---|
| `{app}` | App display name | WhatsApp |
| `{contact}` | Contact or group name | Project Team |
| `{count}` | Number of notifications | 5 |
| `{timeRange}` | Time span of messages | 10:30 AM – 11:02 AM |

### Custom Prompts

Users can override the system prompt per app. Useful for:
- **Email**: "Summarize this email thread, noting the subject, sender, and any required actions"
- **Slack**: "Summarize this Slack channel activity, focusing on decisions made and action items"
- **Social media**: "Briefly note what interactions occurred (likes, comments, follows)"

---

## Error Handling

| Error | Handling |
|---|---|
| **401 Unauthorized** | Show "Invalid API key" error, prompt to update in settings |
| **429 Rate Limited** | Retry with exponential backoff (1s, 2s, 4s, 8s, max 60s) |
| **500+ Server Error** | Retry up to 3 times, then queue for later |
| **Network Error** | Queue for later, retry when network available |
| **Timeout** (30s default) | Retry once with increased timeout, then queue |
| **Empty Response** | Treat as error, log and retry |
| **Invalid JSON** | Treat as error, log response body for debugging |

---

## Security

- API keys are encrypted at rest using `EncryptedSharedPreferences` backed by Android KeyStore
- Keys are never logged, never included in crash reports
- Keys are transmitted only to the user's configured endpoint over HTTPS
- Option to require biometric auth before viewing/editing API keys (Phase 5+)
