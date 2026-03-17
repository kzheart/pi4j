# LLM Providers Guide

This guide covers the 13 LLM providers supported by Pi4J, their configuration, compatibility handling, and how to add custom providers.

## Overview

Pi4J's unified LLM API layer abstracts provider-specific protocols behind a single `ApiProvider` interface. All providers:

- Accept the same `Context` (system prompt + messages + tools)
- Return the same `AssistantMessageEventStream` (streaming events)
- Produce the same `AssistantMessage` type as the final result

This allows switching providers by changing the `Model` configuration alone, without modifying agent or tool code.

## Supported Providers

| Provider | API Type | Class | Base URL | Notes |
|----------|----------|-------|----------|-------|
| Anthropic | `anthropic-messages` | `AnthropicProvider` | `https://api.anthropic.com` | Native protocol; Claude models |
| OpenAI Completions | `openai-completions` | `OpenAICompletionsProvider` | `https://api.openai.com` | Chat Completions API; GPT models |
| OpenAI Responses | `openai-responses` | `OpenAIResponsesProvider` | `https://api.openai.com` | Responses API; GPT models |
| Google Gemini | `google-generative-ai` | `GoogleProvider` | `https://generativelanguage.googleapis.com` | Gemini models |
| Google Vertex AI | `google-vertex` | `GoogleVertexProvider` | `https://{region}-aiplatform.googleapis.com` | Vertex AI models |
| Groq | `openai-completions` | `GroqProvider` | `https://api.groq.com` | OpenAI-compatible; LLaMA, Mixtral |
| Mistral | `openai-completions` | `MistralProvider` | `https://api.mistral.ai` | OpenAI-compatible with quirks |
| xAI | `openai-completions` | `XAIProvider` | `https://api.x.ai` | OpenAI-compatible; Grok models |
| OpenRouter | `openai-completions` | `OpenRouterProvider` | `https://openrouter.ai/api` | Aggregated model routing |
| Ollama | `openai-completions` | `OllamaProvider` | `http://localhost:11434` | Local open-source models |
| Bailian | `openai-completions` | `BailianProvider` | `https://dashscope.aliyuncs.com` | Alibaba Cloud models |
| Custom OpenAI | `openai-completions` | `CustomOpenAIProvider` | User-defined | Any OpenAI-compatible endpoint |

All providers are registered at startup by calling `BuiltinProviderRegistry.registerBuiltins()`.

## Configuration

### Model Definition

The `Model` class carries all information needed to route a request to the correct provider:

```java
Model model = new Model(
    id,             // "claude-sonnet-4-20250514" — model identifier
    name,           // "Claude Sonnet 4" — display name
    api,            // "anthropic-messages" — API type (routes to provider)
    provider,       // "anthropic" — provider name (for sub-routing)
    baseUrl,        // "https://api.anthropic.com" — API endpoint
    reasoning,      // false — whether model supports thinking mode
    input,          // Arrays.asList("text", "image") — input modalities
    cost,           // ModelCost or null — pricing per million tokens
    contextWindow,  // 200000 — context window size in tokens
    maxTokens,      // 16384 — max output tokens
    headers         // Collections.emptyMap() — custom HTTP headers
);
```

| Field | Type | Description |
|-------|------|-------------|
| `id` | `String` | Model identifier sent to the API |
| `name` | `String` | Human-readable display name |
| `api` | `String` | API type — routes to `ApiProvider` via `ApiRegistry` |
| `provider` | `String` | Provider name — used for sub-routing and `ProviderCompat` |
| `baseUrl` | `String` | API endpoint URL |
| `reasoning` | `boolean` | Whether the model supports extended thinking |
| `input` | `List<String>` | Input modalities: `"text"`, `"image"` |
| `cost` | `ModelCost` | Pricing (input/output/cacheRead/cacheWrite per million tokens) |
| `contextWindow` | `int` | Maximum context size in tokens |
| `maxTokens` | `int` | Maximum output tokens |
| `headers` | `Map<String, String>` | Custom HTTP headers added to every request |

### API Key Resolution

The `ApiKeyResolver` function is called before each LLM request, enabling dynamic key resolution:

```java
Agent agent = new Agent(AgentOptions.builder()
    .getApiKey(provider -> {
        switch (provider) {
            case "anthropic": return System.getenv("ANTHROPIC_API_KEY");
            case "openai":    return System.getenv("OPENAI_API_KEY");
            case "google":    return System.getenv("GOOGLE_API_KEY");
            default:          return System.getenv("LLM_API_KEY");
        }
    })
    // ...
    .build());
```

This supports short-lived tokens (OAuth, rotating keys) since the resolver runs on every request, not just at agent creation time.

### Custom Headers

Add per-model HTTP headers via the `headers` field:

```java
Map<String, String> headers = new LinkedHashMap<>();
headers.put("X-Custom-Header", "value");
headers.put("Anthropic-Beta", "max-tokens-3-5-sonnet-2024-07-15");

Model model = new Model(
    "claude-sonnet-4-20250514", "Claude Sonnet 4",
    "anthropic-messages", "anthropic",
    "https://api.anthropic.com",
    false, Arrays.asList("text"), null, 200000, 16384,
    headers
);
```

## Provider Details

### Anthropic

Native Anthropic Messages API protocol.

```java
Model claude = new Model(
    "claude-sonnet-4-20250514",
    "Claude Sonnet 4",
    "anthropic-messages",
    "anthropic",
    "https://api.anthropic.com",
    false,
    Arrays.asList("text", "image"),
    new Model.ModelCost(3.0, 15.0, 0.3, 3.75),
    200000,
    16384,
    Collections.<String, String>emptyMap()
);
```

Environment variable: `ANTHROPIC_API_KEY`

Features: streaming, tool use, thinking mode, image input, cache control.

### OpenAI Completions

Standard OpenAI Chat Completions API.

```java
Model gpt4o = new Model(
    "gpt-4o",
    "GPT-4o",
    "openai-completions",
    "openai",
    "https://api.openai.com",
    false,
    Arrays.asList("text", "image"),
    new Model.ModelCost(2.5, 10.0, 0, 0),
    128000,
    16384,
    Collections.<String, String>emptyMap()
);
```

Environment variable: `OPENAI_API_KEY`

Features: streaming, tool use, JSON mode, developer role, reasoning effort.

### OpenAI Responses

OpenAI Responses API (newer API surface).

```java
Model gpt4oResponses = new Model(
    "gpt-4o",
    "GPT-4o (Responses)",
    "openai-responses",
    "openai",
    "https://api.openai.com",
    false,
    Arrays.asList("text", "image"),
    null,
    128000,
    16384,
    Collections.<String, String>emptyMap()
);
```

Environment variable: `OPENAI_API_KEY`

### Google Gemini

Google Generative AI API (direct access).

```java
Model gemini = new Model(
    "gemini-2.5-pro",
    "Gemini 2.5 Pro",
    "google-generative-ai",
    "google",
    "https://generativelanguage.googleapis.com",
    true,
    Arrays.asList("text", "image"),
    null,
    1048576,
    8192,
    Collections.<String, String>emptyMap()
);
```

Environment variable: `GOOGLE_API_KEY`

Features: streaming, tool use, thinking mode, large context window.

### Google Vertex AI

Google Cloud Vertex AI platform.

```java
Model vertexGemini = new Model(
    "gemini-2.5-pro",
    "Vertex Gemini 2.5 Pro",
    "google-vertex",
    "google-vertex",
    "https://us-central1-aiplatform.googleapis.com",
    true,
    Arrays.asList("text", "image"),
    null,
    1048576,
    8192,
    Collections.<String, String>emptyMap()
);
```

Environment variable: `GOOGLE_VERTEX_API_KEY` (or OAuth token via `ApiKeyResolver`)

### OpenAI-Compatible Providers

Groq, Mistral, xAI, OpenRouter, Ollama, and Bailian all use the `openai-completions` API type. They are differentiated by the `provider` field, and their quirks are handled by `ProviderCompat`.

All OpenAI-compatible providers follow the same `Model` constructor pattern. The key fields that differ are `id`, `provider`, and `baseUrl`:

| Provider | `provider` | `baseUrl` | Model ID Example | Env Variable |
|----------|-----------|-----------|-----------------|--------------|
| Groq | `"groq"` | `https://api.groq.com/openai` | `"llama-3.3-70b-versatile"` | `GROQ_API_KEY` |
| Mistral | `"mistral"` | `https://api.mistral.ai` | `"mistral-large-latest"` | `MISTRAL_API_KEY` |
| xAI | `"xai"` | `https://api.x.ai` | `"grok-3"` | `XAI_API_KEY` |
| OpenRouter | `"openrouter"` | `https://openrouter.ai/api` | `"anthropic/claude-sonnet-4-20250514"` | `OPENROUTER_API_KEY` |
| Ollama | `"ollama"` | `http://localhost:11434` | `"llama3.1"` | (none) |
| Bailian | `"bailian"` | `https://dashscope.aliyuncs.com/compatible-mode` | `"qwen-plus"` | `DASHSCOPE_API_KEY` |
| Custom | `"custom-openai"` | User-defined | User-defined | User-defined |

Example for Groq (same pattern applies to all):

```java
Model groq = new Model(
    "llama-3.3-70b-versatile", "LLaMA 3.3 70B",
    "openai-completions", "groq",
    "https://api.groq.com/openai",
    false, Arrays.asList("text"), null, 131072, 8192,
    Collections.<String, String>emptyMap()
);
```

Mistral has specific quirks handled by `ProviderCompat`: uses `max_tokens` instead of `max_completion_tokens`, requires tool result name field, requires 9-character tool call IDs. Ollama requires no API key -- set `getApiKey` to return an empty string.

## Adding a Custom Provider

Implement `ApiProvider` and register it with `ApiRegistry`:

```java
import com.pi4j.ai.provider.ApiProvider;
import com.pi4j.ai.provider.ApiRegistry;
import com.pi4j.ai.provider.StreamOptions;
import com.pi4j.ai.stream.AssistantMessageEventStream;
import com.pi4j.ai.types.Context;
import com.pi4j.ai.types.Model;

public class MyCustomProvider implements ApiProvider {

    @Override
    public String getApi() {
        return "my-custom-api";
    }

    @Override
    public AssistantMessageEventStream stream(
            Model model, Context context, StreamOptions options) {
        AssistantMessageEventStream eventStream = new AssistantMessageEventStream();

        // Start async HTTP call, parse responses, push events
        CompletableFuture.runAsync(() -> {
            try {
                // Build request from context
                // Parse SSE or polling response
                // Push events: eventStream.push(new TextDeltaEvent(...))
                // On completion:
                eventStream.push(new DoneEvent(StopReason.STOP, finalMessage));
                eventStream.end(finalMessage);
            } catch (Exception e) {
                eventStream.push(new ErrorEvent(StopReason.ERROR, errorMessage));
                eventStream.error(e);
            }
        });

        return eventStream;
    }
}

// Register at startup
ApiRegistry.register(new MyCustomProvider());

// Or register under an existing API type with a specific provider name
ApiRegistry.register("openai-completions", "my-vendor", new MyCustomProvider());
```

The `ApiRegistry` supports two registration methods:

| Method | Use Case |
|--------|----------|
| `register(provider)` | Register by `provider.getApi()` — used for unique API protocols |
| `register(api, providerName, provider)` | Register by `(api, provider)` pair — used when sharing an API type |

## Cross-Provider Message Conversion

When switching models mid-conversation (e.g., starting with Claude, then switching to GPT), the `MessageTransformer` normalizes the message history for the target model.

### Transformation Rules

```
MessageTransformer.transform(messages, targetModel)
```

| Message Type | Same Model | Cross-Model |
|-------------|-----------|-------------|
| `ThinkingContent` | Preserved with signature | Converted to `TextContent` |
| `TextContent` | Preserved with signature | Stripped of provider-specific metadata |
| `ToolCallContent` IDs | Unchanged | Normalized for target provider format |
| Error/Aborted `AssistantMessage` | Skipped | Skipped |
| Orphan tool calls (no result) | N/A | Synthetic error results inserted |

### Tool Call ID Normalization

Different providers have different requirements for tool call IDs:

| Provider | ID Format Requirement |
|----------|----------------------|
| Anthropic | `[a-zA-Z0-9_-]`, max 64 characters |
| Mistral | Exactly 9 characters |
| Others | No specific requirements |

The `MessageTransformer` automatically normalizes IDs when converting across providers.

### Usage

Message transformation is handled automatically by the agent's execution loop. For manual use:

```java
List<Message> normalized = MessageTransformer.transform(
    existingMessages,
    newTargetModel
);
```

## Error Handling

### OverflowDetector

The `OverflowDetector` identifies context window overflow errors across providers. Each provider returns different error messages, so the detector uses a set of regex patterns:

```java
boolean isOverflow = OverflowDetector.isContextOverflow(
    assistantMessage,
    model.getContextWindow()
);
```

Detected patterns include:
- `"prompt is too long"`
- `"exceeds the context window"`
- `"input token count.*exceeds the maximum"`
- And more provider-specific patterns

When overflow is detected, applications can truncate the conversation history and retry.

### Error Recovery

LLM API errors are captured as `AssistantMessage` with `StopReason.ERROR`:

```java
agent.subscribe(event -> {
    if (event instanceof MessageEndEvent) {
        AgentMessage msg = ((MessageEndEvent) event).getMessage();
        if (msg instanceof LlmAgentMessage) {
            AssistantMessage assistant =
                (AssistantMessage) ((LlmAgentMessage) msg).getMessage();
            if (assistant.getStopReason() == StopReason.ERROR) {
                System.err.println("LLM error: " + assistant.getErrorMessage());
            }
        }
    }
});
```

## Streaming

All providers implement streaming via Server-Sent Events (SSE). The flow:

```
HTTP Response (SSE stream)
  |
  v
Provider parses SSE lines
  |
  v
Translates to AssistantMessageEvent subclasses
  |
  v
Pushes to AssistantMessageEventStream
  |
  v
EventStream notifies all subscribers
  |
  v
Agent wraps in MessageUpdateEvent
  |
  v
AgentEventListener receives it
```

### Event Types During Streaming

A typical streaming session produces events in this order:

```
StartEvent
ThinkingStartEvent          (if thinking mode enabled)
ThinkingDeltaEvent (x N)
ThinkingEndEvent
TextStartEvent
TextDeltaEvent (x N)        (incremental text chunks)
TextEndEvent
ToolCallStartEvent          (if tool call)
ToolCallDeltaEvent (x N)    (incremental JSON arguments)
ToolCallEndEvent
DoneEvent                   (final AssistantMessage)
```

### Late-Subscriber Replay

The `EventStream` buffers all events. A subscriber that attaches after streaming has started will receive all previously emitted events before transitioning to live events. This is critical for UIs that render mid-stream:

```java
// Streaming may have already started
AssistantMessageEventStream stream = provider.stream(model, context, options);

// Subscribe later -- still receives all events from the beginning
stream.subscribe(event -> renderToUI(event));
```

See the [Architecture Guide](architecture.md#streaming-event-architecture) for implementation details of the replay mechanism.

For tool-related provider behavior, see the [Tool System Guide](tool-system.md). For getting started quickly, see the [Getting Started Guide](getting-started.md).
