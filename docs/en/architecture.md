# Architecture & Design

This document describes the internal architecture of Pi4J, its module structure, execution model, and key design decisions.

## Overview

Pi4J is organized into four modules with a strict dependency graph:

```
pi4j-tools (optional) ──> pi4j-agent ──> pi4j-ai
                               ^
                          Your Application
                         (custom tools)
```

```
pi4j/
├── pi4j-ai/          # Unified LLM API layer
│   ├── types/         #   Core types: Message, ContentBlock, Tool, Model
│   ├── stream/        #   EventStream with replay support
│   ├── provider/      #   13 LLM provider adapters
│   └── util/          #   Validation, JSON utilities
│
├── pi4j-agent/        # Agent runtime core
│   ├── agent/         #   Agent class, execution loop, options
│   ├── tool/          #   Tool interface, ToolSpec, dispatcher, middleware
│   ├── session/       #   JSONL session persistence
│   └── event/         #   Agent event hierarchy
│
├── pi4j-tools/        # Built-in tool implementations (optional)
│   └── (7 tools)      #   read, write, edit, bash, grep, find, ls
│
└── pi4j-examples/     # Usage examples
    └── (5 examples)   #   Basic, CustomTool, Event, BuiltinTools, Session
```

The `pi4j-tools` module is an **optional** dependency. Applications only need `pi4j-agent` and register their own tools. Built-in tools are explicitly mounted, never auto-registered.

## Design Philosophy

Pi4J follows five core principles inherited from [pi-mono](https://github.com/badlogic/pi-mono):

### 1. Minimal Core

The framework has exactly three pillars: **Agent execution loop**, **unified LLM interface**, and **tool system**. Everything else is optional or user-provided. There is no dependency injection container, no plugin system, no configuration file parsing.

### 2. Toolkit, Not Framework

Pi4J provides composable building blocks without imposing a workflow. You construct an `Agent`, register tools, call `prompt()`, and handle events. The framework never takes control of your application lifecycle.

### 3. Composability First

All tools are pluggable, replaceable, and extensible. Built-in tools and custom tools share the same `AgentTool` interface. Middleware can intercept any tool execution. Providers can be swapped or extended at runtime.

### 4. No Magic

No reflection, no annotation processing, no classpath scanning, no implicit behavior. Every registration is explicit. Every conversion is visible. The only dependencies are Gson, OkHttp, and SLF4J.

### 5. Multi-Tenant

Multiple independent `Agent` instances can run in parallel. Each agent maintains its own message history, tool set, and execution state. The `SessionManager` provides per-tenant persistence with JSONL isolation.

## Module Structure

### pi4j-ai — Unified LLM API Layer

Responsible for abstracting LLM provider differences behind a single streaming interface.

| Package | Key Classes | Responsibility |
|---------|-------------|----------------|
| `types` | `Message`, `ContentBlock`, `Model`, `Tool`, `Usage`, `StopReason` | Core domain types shared across all modules |
| `stream` | `EventStream`, `AssistantMessageEventStream`, `AssistantMessageEvent` | Thread-safe event streaming with late-subscriber replay |
| `provider` | `ApiProvider`, `ApiRegistry`, `StreamOptions`, `ProviderCompat`, `MessageTransformer` | Provider abstraction, routing, and cross-provider compatibility |
| `util` | `ToolValidator`, `OverflowDetector`, `JsonUtil` | Tool parameter validation, context overflow detection |

### pi4j-agent — Agent Runtime Core

Contains the stateful agent engine that orchestrates LLM calls, tool execution, and event emission.

| Package | Key Classes | Responsibility |
|---------|-------------|----------------|
| `agent` | `Agent`, `AgentLoop`, `AgentOptions`, `AgentState`, `AgentMessage`, `LlmAgentMessage` | Agent lifecycle, execution loop, state management |
| `tool` | `AgentTool`, `ToolSpec`, `ToolExecutor`, `ToolMiddleware`, `ToolDispatcher`, `DefaultMiddlewares` | Tool definition, execution pipeline, middleware chain |
| `session` | `SessionManager`, `Session`, `SessionEntry`, `SessionInfo` | JSONL-based conversation persistence |
| `event` | `AgentEvent`, `AgentStartEvent`, `TurnEndEvent`, `MessageUpdateEvent`, etc. | Fine-grained execution event hierarchy |
| `func` | `ApiKeyResolver`, `ContextTransformer`, `MessageConverter` | Functional interfaces for agent customization |

### pi4j-tools — Built-in Tools (Optional)

Seven developer tools, each independently instantiable:

| Class | Tool Name | Description |
|-------|-----------|-------------|
| `ReadTool` | `read` | Read file contents with line offset/limit |
| `WriteTool` | `write` | Create or overwrite files |
| `EditTool` | `edit` | Find-and-replace editing |
| `BashTool` | `bash` | Execute shell commands with timeout and abort |
| `GrepTool` | `grep` | Regex search across files |
| `FindTool` | `find` | Find files by glob pattern |
| `LsTool` | `ls` | List directory contents |

Utility classes `Truncator` and `PathUtils` handle output truncation and path security.

## Agent Execution Loop

The agent runs a **dual-loop** architecture: an inner tool loop and an outer follow-up loop.

### Flow Diagram

```
prompt("message")
  |
  v
+=============================================+
|              OUTER LOOP (follow-up)         |
|                                             |
|  +---------------------------------------+  |
|  |        INNER LOOP (tool execution)    |  |
|  |                                       |  |
|  |  1. Inject pending messages           |  |
|  |  2. Transform context                 |  |
|  |  3. Convert to LLM format            |  |
|  |  4. Call LLM API (streaming SSE)      |  |
|  |  5. Receive AssistantMessage          |  |
|  |       |                               |  |
|  |       +-- Has tool calls?             |  |
|  |       |   YES: Execute tools          |  |
|  |       |         Check steering queue  |  |
|  |       |         If steered: skip rest |  |
|  |       |         Loop back to step 1   |  |
|  |       |   NO: Exit inner loop         |  |
|  +---------------------------------------+  |
|                                             |
|  Check follow-up queue                      |
|    Has messages? YES: continue outer loop   |
|    No messages?  Exit                       |
+=============================================+
  |
  v
AgentEndEvent
```

### Sequence Diagram

```
User        Agent       LLM Provider    Tool
 |            |              |            |
 |--prompt--->|              |            |
 |            |--stream()--->|            |
 |            |<--events-----|            |
 |            |<--DoneEvent--|            |
 |            |                           |
 |            |--[tool calls detected]--->|
 |            |              |            |--execute()
 |            |              |            |<--result
 |            |                           |
 |            |--[check steering queue]   |
 |            |                           |
 |            |--stream()--->|            |
 |            |<--DoneEvent--|            |
 |            |                           |
 |            |--[no tool calls]          |
 |            |--[check follow-up queue]  |
 |            |                           |
 |<-complete--|                           |
```

### Steering Queue

The steering queue allows **interrupting** the agent mid-execution. When a steering message is detected after a tool completes, all remaining tool calls in the current batch are **skipped** (with synthetic error results), and the steering message is injected into the context for the next LLM call.

```java
agent.steer(message);  // Enqueue an interrupt
```

Modes: `"all"` (drain entire queue) or `"one-at-a-time"` (process one message per check).

### Follow-up Queue

The follow-up queue allows **appending** messages after the current loop completes. This enables chaining tasks without nested `prompt()` calls:

```java
agent.followUp(message);  // Process after current loop ends
```

## Streaming Event Architecture

### EventStream Design

`EventStream<T, R>` is the core streaming primitive. It provides:

- **Thread-safe push/subscribe** — producers call `push(event)`, consumers call `subscribe(listener)`
- **Late-subscriber replay** — new subscribers receive all previously pushed events before live events
- **Dual consumption** — subscribe for real-time events, or await the final result via `result()` (`CompletableFuture<R>`)
- **Terminal signals** — `end(result)` for success, `error(cause)` for failure

```java
public class EventStream<T, R> {
    private final Queue<T> queue;                    // event buffer
    private final CopyOnWriteArrayList<Consumer<T>> listeners;
    private final CompletableFuture<R> finalResult;
    private volatile boolean done;

    public void push(T event);              // emit an event
    public void end(R result);              // signal completion
    public void error(Throwable cause);     // signal failure
    public Runnable subscribe(Consumer<T>); // subscribe (with replay)
    public CompletableFuture<R> result();   // await final result
}
```

The replay mechanism works by capturing a snapshot of the event queue under a lock when a new subscriber is added, then replaying all buffered events to the new listener before it starts receiving live events.

### Event Type Hierarchy

**LLM-level events** (`AssistantMessageEvent`):

```
AssistantMessageEvent
├── StartEvent              # Stream begins
├── TextStartEvent          # Text block starts
├── TextDeltaEvent          # Incremental text
├── TextEndEvent            # Text block ends
├── ThinkingStartEvent      # Thinking block starts
├── ThinkingDeltaEvent      # Incremental thinking
├── ThinkingEndEvent        # Thinking block ends
├── ToolCallStartEvent      # Tool call starts
├── ToolCallDeltaEvent      # Incremental arguments
├── ToolCallEndEvent        # Tool call complete
├── DoneEvent               # Stream completed successfully
└── ErrorEvent              # Stream failed
```

**Agent-level events** (`AgentEvent`):

```
AgentEvent
├── AgentStartEvent                # Agent loop begins
├── AgentEndEvent                  # Agent loop ends (carries all messages)
├── TurnStartEvent                 # One LLM call + tool execution begins
├── TurnEndEvent                   # Turn ends (carries assistant message + tool results)
├── MessageStartEvent              # Message committed to history
├── MessageUpdateEvent             # Streaming update (wraps AssistantMessageEvent)
├── MessageEndEvent                # Message finalized
├── ToolExecutionStartEvent        # Tool begins (carries tool name + args)
├── ToolExecutionUpdateEvent       # Tool progress update
└── ToolExecutionEndEvent          # Tool finished (carries result + error flag)
```

## Message & Content Block System

### Message Hierarchy

```
AgentMessage (abstract)           # Application-level message
└── LlmAgentMessage               # Wraps an LLM Message
    └── Message (abstract)         # LLM-level message
        ├── UserMessage            # User input (text + images)
        ├── AssistantMessage       # LLM response (text + thinking + tool calls)
        └── ToolResultMessage      # Tool execution result
```

`AgentMessage` is the superset. Custom message types can extend `AgentMessage` for application-specific events (e.g., system notifications). These are filtered or converted by the `MessageConverter` before being sent to the LLM.

### ContentBlock Hierarchy

```
ContentBlock (abstract)
├── TextContent            # Plain text
├── ImageContent           # Base64-encoded image with MIME type
├── ThinkingContent        # Chain-of-thought with optional signature
└── ToolCallContent        # Tool invocation (id, name, arguments)
```

### Cross-Provider Message Conversion

The `MessageTransformer` handles compatibility when switching models mid-conversation:

| Scenario | Transformation |
|----------|---------------|
| Thinking blocks (same model) | Preserved with signature |
| Thinking blocks (cross-model) | Converted to plain `TextContent` |
| Tool call IDs (Anthropic) | Sanitized to `[a-zA-Z0-9_-]`, max 64 chars |
| Tool call IDs (Mistral) | Truncated to exactly 9 characters |
| Orphan tool calls | Synthetic error results auto-inserted |
| Error/aborted messages | Skipped entirely |

## Provider System

### ApiProvider Interface

Every LLM provider implements this interface:

```java
public interface ApiProvider {
    String getApi();  // e.g., "anthropic-messages"
    AssistantMessageEventStream stream(Model model, Context context, StreamOptions options);
}
```

### ApiRegistry Routing

The `ApiRegistry` routes requests to providers using a two-level key: `(api, provider)`. This allows multiple providers to share the same API type:

```java
// Core providers registered by api type
ApiRegistry.register(new AnthropicProvider());       // "anthropic-messages"
ApiRegistry.register(new OpenAICompletionsProvider()); // "openai-completions"

// Compatible providers registered by (api, provider) pair
ApiRegistry.register("openai-completions", "groq", new GroqProvider());
ApiRegistry.register("openai-completions", "mistral", new MistralProvider());
```

When `ApiRegistry.stream(model, context, options)` is called, it first checks for a `(model.api, model.provider)` match, then falls back to `model.api` alone.

### ProviderCompat Layer

`ProviderCompat` detects per-provider quirks for OpenAI-compatible providers and returns an `OpenAiCompletionsCompat` configuration:

| Feature | Standard OpenAI | Mistral | xAI/Grok | DeepSeek | Ollama |
|---------|----------------|---------|----------|----------|--------|
| `max_tokens` field | `max_completion_tokens` | `max_tokens` | `max_completion_tokens` | `max_completion_tokens` | `max_completion_tokens` |
| Developer role | Yes | No | No | No | No |
| Reasoning effort | Yes | No | No | No | No |
| Tool ID format | Standard | 9-char truncated | Standard | Standard | Standard |
| Response format | Full | Full | JSON object only | JSON object only | None |

### SSE Streaming

All providers parse Server-Sent Events (SSE) from the HTTP response and translate them into `AssistantMessageEvent` instances pushed to an `AssistantMessageEventStream`. The stream is created before the HTTP call begins, so subscribers can attach immediately.

## Java 8 Design Patterns

Since Pi4J targets Java 8, several modern language features are adapted:

| Modern Java / TypeScript | Pi4J Java 8 Approach | Example |
|--------------------------|---------------------|---------|
| Union types `A \| B \| C` | Abstract class + subclasses + `instanceof` | `ContentBlock` -> `TextContent`, `ImageContent`, etc. |
| `async/await` | `CompletableFuture<Void>` + `.join()` | `agent.prompt("...").join()` |
| `AsyncIterator` (event streams) | Custom `EventStream<T, R>` + `Consumer<T>` | `stream.subscribe(event -> ...)` |
| Sealed interfaces | Abstract class + package-private constructors | `AgentMessage` subclassing |
| Records | Immutable POJOs (final fields + getters) | `Model`, `Usage`, `AgentState` |
| Generic literal types | Enum + String constants | `StopReason`, `ToolDispatchMode` |
| TypeBox JSON Schema | Gson `JsonObject` + `ToolSpec` builder | Schema generated in `ToolSpec.Builder.buildParameters()` |
| Builder pattern (Kotlin DSL) | Static `Builder` inner class | `AgentOptions.builder()...build()` |
| Functional interfaces | `@FunctionalInterface` | `ApiKeyResolver`, `ToolHandler`, `AgentEventListener` |

## Key Design Decisions

### D1: EventStream with Replay

**Decision**: Buffer all events and replay to late subscribers.

**Rationale**: In asynchronous scenarios, the HTTP streaming response may push events before the caller has a chance to attach a listener. Replay ensures no events are lost, at the cost of increased memory usage for the event buffer.

### D2: Synchronous Tool Execution

**Decision**: Tools execute synchronously within the agent loop, on a single-threaded executor.

**Rationale**: Simplifies reasoning about state mutations. The `ToolDispatcher` interface allows offloading to other threads when needed (`BLOCKING` mode), but the default path is sequential. The `ToolMiddleware` chain wraps each execution for cross-cutting concerns (timeout, retry, error handling).

### D3: Two-Level Message Abstraction

**Decision**: Separate `AgentMessage` (application layer) from `Message` (LLM layer).

**Rationale**: Applications often need custom message types (system notifications, external events) that should appear in the conversation history but not be sent directly to the LLM. The `MessageConverter` function bridges the two layers.

### D4: Provider Routing by (api, provider) Pair

**Decision**: `ApiRegistry` supports both single-key (`api`) and double-key (`api`, `provider`) routing.

**Rationale**: Many providers (Groq, Mistral, xAI, Ollama) are OpenAI-compatible but have subtle differences. Double-key routing allows each to have its own `ApiProvider` implementation while sharing the `"openai-completions"` API type.

### D5: Cooperative Abort via AbortHandle

**Decision**: Use a shared `AbortHandle` (volatile boolean + listeners) instead of `Thread.interrupt()`.

**Rationale**: Thread interruption is unreliable with OkHttp and `CompletableFuture`. A cooperative abort flag is checked at safe points (before LLM calls, between tool executions, within long-running tools). The `AbortHandle` also supports listeners for cleanup actions.

### D6: JSONL Session Format

**Decision**: Store sessions as JSON Lines with `id`/`parentId` fields for tree structure.

**Rationale**: JSONL is append-only, making it safe for concurrent writes. The tree structure (via parent references) supports conversation branching without complex file operations. Each session is a single file, simplifying multi-tenant isolation.

### D7: No Auto-Registration of Built-in Tools

**Decision**: Built-in tools must be explicitly created and registered via `BuiltinTools.select()` or `BuiltinTools.all()`.

**Rationale**: Auto-registration violates the "no magic" principle. In production, most applications need only a subset of tools, and some tools (like `bash`) have security implications. Explicit registration makes the tool set visible and auditable.
