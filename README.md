# Pi4J - Minimalist AI Agent Framework for Java

> A lightweight, composable AI Agent framework built on Java 8.
> Inspired by [pi-mono](https://github.com/badlogic/pi-mono)'s minimalist philosophy — toolkit, not framework.

English | [中文](README_CN.md)

[![Java](https://img.shields.io/badge/Java-8%2B-orange)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](LICENSE)
[![Version](https://img.shields.io/badge/version-1.0.8--SNAPSHOT-green)]()

## Features

- **Agent Loop** — Built-in execution loop with multi-turn tool calling, streaming, and abort support
- **13 LLM Providers** — Anthropic, OpenAI (Completions & Responses API), Google Gemini/Vertex, Groq, Mistral, Ollama, xAI, OpenRouter, Bailian, and custom OpenAI-compatible endpoints
- **Composable Tool System** — Declarative tool definitions with JSON Schema validation, 3 dispatch modes, and middleware pipeline
- **Event-Driven** — Fine-grained event stream with replay support for late subscribers
- **Session Management** — JSONL-based session persistence with multi-tenant isolation
- **Built-in Tools** — 7 developer tools out of the box (file I/O, bash, grep, find, ls)
- **Zero Magic** — No heavy dependencies, no reflection, no annotation processing. Just Gson + OkHttp + SLF4J
- **Java 8 Compatible** — Embeddable in any Java application, including legacy systems

## Quick Start

### Installation

Add Pi4J to your project via Gradle:

```kotlin
repositories {
    mavenLocal()
}

dependencies {
    implementation("com.pi4j:pi4j-agent:1.0.8-SNAPSHOT")
    implementation("com.pi4j:pi4j-tools:1.0.8-SNAPSHOT") // optional: built-in tools
}
```

### 3-Line Agent

```java
Agent agent = new Agent(AgentOptions.builder()
    .systemPrompt("You are a helpful assistant")
    .model(model)
    .getApiKey(provider -> System.getenv("ANTHROPIC_API_KEY"))
    .build());

agent.prompt("Hello!").join();

// Get the response
AgentState state = agent.getState();
```

### Custom Tools

Define tools with the declarative `ToolSpec` builder:

```java
AgentTool weatherTool = ToolSpec.builder("get_weather")
    .description("Get weather information for a city")
    .stringParam("city", true, "City name")
    .integerParam("days", false, "Forecast days (default: 1)")
    .handler((id, args, abort, onUpdate) -> {
        String city = args.getString("city");
        int days = args.getInt("days", 1);
        String weather = fetchWeather(city, days);
        return AgentToolResult.text(weather);
    })
    .build()
    .toAgentTool();
```

Or implement the `AgentTool` interface directly for full control:

```java
AgentTool timeTool = new AgentTool() {
    @Override public String getName() { return "get_time"; }
    @Override public String getDescription() { return "Get current time in a timezone"; }
    @Override public String getLabel() { return "Get Time"; }
    @Override public JsonObject getParameters() { /* JSON Schema */ }
    @Override public AgentToolResult execute(String id, Map<String, Object> params,
                                             AbortHandle abort, ToolUpdateCallback onUpdate) {
        String zone = String.valueOf(params.get("zone"));
        return AgentToolResult.text(ZonedDateTime.now(ZoneId.of(zone)).toString());
    }
};
```

### Built-in Tools

Mount developer tools with a single call:

```java
// Select specific tools
List<AgentTool> tools = BuiltinTools.select(Paths.get("."), "read", "write", "bash", "grep");

// Or mount all 7 tools
List<AgentTool> allTools = BuiltinTools.all(Paths.get("."));

Agent agent = new Agent(AgentOptions.builder()
    .systemPrompt("You are a coding assistant")
    .model(model)
    .tools(tools)
    .build());
```

Available built-in tools:

| Tool | Description |
|------|-------------|
| `read` | Read file contents (with line offset/limit) |
| `write` | Create or overwrite files |
| `edit` | Edit specific line ranges |
| `bash` | Execute shell commands (with timeout & abort) |
| `grep` | Regex search across files |
| `find` | Find files by glob pattern |
| `ls` | List directory contents |

### Event Listening

Subscribe to fine-grained execution events:

```java
Runnable unsubscribe = agent.subscribe(event -> {
    if (event instanceof MessageUpdateEvent) {
        MessageUpdateEvent update = (MessageUpdateEvent) event;
        if (update.getAssistantMessageEvent() instanceof TextDeltaEvent) {
            // Stream text to console
            System.out.print(((TextDeltaEvent) update.getAssistantMessageEvent()).getDelta());
        }
    }
    if (event instanceof ToolExecutionStartEvent) {
        System.out.println("Executing: " + ((ToolExecutionStartEvent) event).getToolName());
    }
});

agent.prompt("Analyze this codebase").join();
unsubscribe.run(); // Stop listening
```

Event hierarchy:

```
AgentEvent
├── AgentStartEvent / AgentEndEvent
├── TurnStartEvent / TurnEndEvent
├── MessageStartEvent / MessageUpdateEvent / MessageEndEvent
└── ToolExecutionStartEvent / ToolExecutionUpdateEvent / ToolExecutionEndEvent
```

### Session Management

Persist conversations across restarts:

```java
SessionManager sessionManager = new SessionManager(Paths.get("sessions"));

// Create and populate a session
Session session = sessionManager.create("user-123");
session.appendMessage(new LlmAgentMessage(new UserMessage(
    Collections.singletonList(new TextContent("Hello"))
)));

// Load existing session
Session loaded = sessionManager.load("user-123");

// List all sessions
for (SessionInfo info : sessionManager.list()) {
    System.out.println(info.getSessionId());
}
```

## Architecture

```
pi4j/
├── pi4j-ai          # Unified LLM API layer
│   ├── types/        #   Core types: Message, ContentBlock, Tool, Model
│   ├── stream/       #   EventStream with replay support
│   ├── provider/     #   13 LLM provider adapters
│   └── util/         #   Validation, JSON utilities
│
├── pi4j-agent        # Agent runtime core
│   ├── agent/        #   Agent class, execution loop, options
│   ├── tool/         #   Tool interface, ToolSpec, dispatcher, middleware
│   ├── session/      #   JSONL session persistence
│   └── event/        #   Agent event hierarchy
│
├── pi4j-tools        # Built-in tool implementations
│   └── (7 tools)     #   read, write, edit, bash, grep, find, ls
│
└── pi4j-examples     # Usage examples
    └── (5 examples)  #   Basic, CustomTool, Event, BuiltinTools, Session
```

### Design Principles

1. **Minimal Core** — Only three pillars: Agent Loop + LLM Interface + Tool System
2. **Toolkit, Not Framework** — Provides building blocks, doesn't impose workflows
3. **Composability First** — All tools are pluggable, replaceable, extensible
4. **No Magic** — No implicit behavior, everything is explicit and predictable
5. **Multi-Tenant** — Multiple independent Agent sessions can run in parallel

### Agent Execution Loop

```
prompt("message")
  │
  ▼
┌──────────────────────────────────────────┐
│  while (hasToolCalls || pendingMessages)  │
│    ├─ Convert messages → LLM format      │
│    ├─ Call LLM API (streaming SSE)       │
│    ├─ If tool calls → execute tools      │
│    └─ Check steering queue (interrupt)   │
│                                          │
│  Check follow-up queue                   │
│    └─ If messages → continue loop        │
└──────────────────────────────────────────┘
  │
  ▼
AgentEndEvent
```

- **Steering queue**: Interrupt the current loop mid-execution
- **Follow-up queue**: Append messages after the current loop completes

### Supported LLM Providers

| Provider | API Type | Models |
|----------|----------|--------|
| Anthropic | `anthropic-messages` | Claude 3.5 Sonnet, Claude Opus, etc. |
| OpenAI | `openai-completions` | GPT-4o, GPT-4 Turbo, etc. |
| OpenAI | `openai-responses` | GPT-4o (Responses API) |
| Google | `google-gemini` | Gemini Pro, Gemini Ultra |
| Google | `google-vertex` | Vertex AI models |
| Groq | `groq` | LLaMA, Mixtral |
| Mistral | `mistral` | Mistral Large, etc. |
| xAI | `xai` | Grok |
| OpenRouter | `openrouter` | Aggregated models |
| Ollama | `ollama` | Local open-source models |
| Bailian | `bailian` | Alibaba Cloud models |
| Custom | `custom-openai` | Any OpenAI-compatible endpoint |

### Tool System

The tool system provides 3 dispatch modes:

- **Serial** — Execute tools one at a time, in order
- **Parallel** — Execute all tools concurrently
- **Custom** — User-defined dispatch logic

Middleware pipeline for cross-cutting concerns:

```java
Agent agent = new Agent(AgentOptions.builder()
    .toolMiddlewares(Arrays.asList(
        new TimeoutMiddleware(30_000),
        new RetryMiddleware(3),
        new LoggingMiddleware()
    ))
    // ...
    .build());
```

### Java 8 Design Patterns

Since the framework targets Java 8, several modern patterns are adapted:

| Modern Java / TypeScript | Pi4J Java 8 Approach |
|--------------------------|---------------------|
| Union types `A \| B \| C` | Abstract class + subclasses + Visitor |
| `async/await` | `CompletableFuture` + callbacks |
| `AsyncIterator` | Custom `EventStream<T, R>` + `Consumer` |
| Sealed interfaces | Abstract class + package-private constructors |
| Records | Immutable POJOs (final fields + getters) |

## Configuration

### AgentOptions

| Option | Type | Description |
|--------|------|-------------|
| `systemPrompt` | `String` | System prompt (required) |
| `model` | `Model` | LLM model configuration (required) |
| `tools` | `List<AgentTool>` | Available tools |
| `temperature` | `Double` | Sampling temperature (0-1) |
| `maxTokens` | `Integer` | Max output tokens |
| `thinkingLevel` | `String` | Thinking mode: "off", "basic", "deep" |
| `thinkingBudget` | `Integer` | Thinking token budget |
| `responseFormat` | `JsonObject` | Response format (JSON mode, etc.) |
| `toolChoice` | `String` | Tool selection strategy |
| `steeringMode` | `String` | Steering queue mode: "all" / "one-at-a-time" |
| `followUpMode` | `String` | Follow-up queue mode: "all" / "one-at-a-time" |
| `getApiKey` | `ApiKeyResolver` | API key resolver function |
| `toolDispatcher` | `ToolDispatcher` | Custom tool dispatcher |
| `toolMiddlewares` | `List<ToolMiddleware>` | Tool middleware chain |
| `convertToLlm` | `MessageConverter` | Custom message converter |
| `transformContext` | `ContextTransformer` | Context transformer |

## Dependencies

Pi4J keeps dependencies minimal:

| Dependency | Version | Purpose |
|-----------|---------|---------|
| Gson | 2.11.0 | JSON serialization |
| OkHttp | 4.12.0 | HTTP client + SSE |
| SLF4J | 1.7.25 | Logging facade |

## Building

```bash
# Build all modules
./gradlew build

# Run tests
./gradlew test

# Publish to Maven Local
./gradlew publishToMavenLocal
```

## Examples

See [`pi4j-examples`](pi4j-examples/) for complete working examples:

| Example | Description |
|---------|-------------|
| [BasicAgentExample](pi4j-examples/src/main/java/com/pi4j/examples/BasicAgentExample.java) | Minimal agent setup and prompt |
| [CustomToolExample](pi4j-examples/src/main/java/com/pi4j/examples/CustomToolExample.java) | Define and use custom tools |
| [BuiltinToolsExample](pi4j-examples/src/main/java/com/pi4j/examples/BuiltinToolsExample.java) | Mount built-in developer tools |
| [EventListenerExample](pi4j-examples/src/main/java/com/pi4j/examples/EventListenerExample.java) | Subscribe to execution events |
| [SessionManagerExample](pi4j-examples/src/main/java/com/pi4j/examples/SessionManagerExample.java) | Session persistence and loading |

## Documentation

- [English Documentation](docs/en/)
  - [Getting Started](docs/en/getting-started.md)
  - [Architecture & Design](docs/en/architecture.md)
  - [Tool System](docs/en/tool-system.md)
  - [LLM Providers](docs/en/providers.md)
- [中文文档](docs/zh/)
  - [快速开始](docs/zh/getting-started.md)
  - [架构与设计](docs/zh/architecture.md)
  - [工具系统](docs/zh/tool-system.md)
  - [LLM 提供商](docs/zh/providers.md)

## License

[Apache License 2.0](LICENSE)
