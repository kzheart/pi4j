# Getting Started

This guide walks you through setting up Pi4J and building your first AI Agent in Java 8.

## Prerequisites

- **Java 8+** (JDK 1.8 or later)
- **Gradle** (7.x or later recommended)
- An API key from at least one LLM provider (e.g., Anthropic, OpenAI, Google)

## Installation

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

Pi4J pulls in only three transitive dependencies:

| Dependency | Version | Purpose |
|-----------|---------|---------|
| Gson | 2.11.0 | JSON serialization |
| OkHttp | 4.12.0 | HTTP client + SSE streaming |
| SLF4J | 1.7.25 | Logging facade |

To build Pi4J from source and publish to your local Maven repository:

```bash
git clone https://github.com/your-org/pi4j.git
cd pi4j
./gradlew publishToMavenLocal
```

## Registering Providers

Before creating an agent, register the built-in LLM providers. This is a one-time setup, typically at application startup:

```java
import com.pi4j.ai.provider.register.BuiltinProviderRegistry;

// Register all 13 built-in providers
BuiltinProviderRegistry.registerBuiltins();
```

See the [Providers Guide](providers.md) for details on each provider and custom registration.

## Creating Your First Agent

Here is a complete, runnable example using Anthropic Claude:

```java
import com.pi4j.agent.Agent;
import com.pi4j.agent.AgentMessage;
import com.pi4j.agent.AgentOptions;
import com.pi4j.agent.LlmAgentMessage;
import com.pi4j.ai.provider.register.BuiltinProviderRegistry;
import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.Model;
import com.pi4j.ai.types.TextContent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FirstAgent {
    public static void main(String[] args) {
        // 1. Register providers
        BuiltinProviderRegistry.registerBuiltins();

        // 2. Define the model
        Model claude = new Model(
            "claude-sonnet-4-20250514",       // id
            "Claude Sonnet 4",                // display name
            "anthropic-messages",             // api type
            "anthropic",                      // provider
            "https://api.anthropic.com",      // base URL
            false,                            // reasoning mode
            Arrays.asList("text", "image"),   // input modalities
            null,                             // cost (optional)
            200000,                           // context window
            16384,                            // max output tokens
            Collections.<String, String>emptyMap()
        );

        // 3. Build the agent
        Agent agent = new Agent(AgentOptions.builder()
            .systemPrompt("You are a helpful assistant.")
            .model(claude)
            .getApiKey(provider -> System.getenv("ANTHROPIC_API_KEY"))
            .build());

        // 4. Send a prompt and wait for completion
        agent.prompt("What is the capital of France?").join();

        // 5. Extract the response
        List<AgentMessage> messages = agent.getState().getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentMessage msg = messages.get(i);
            if (msg instanceof LlmAgentMessage && "assistant".equals(msg.getRole())) {
                AssistantMessage assistant =
                    (AssistantMessage) ((LlmAgentMessage) msg).getMessage();
                for (ContentBlock block : assistant.getContent()) {
                    if (block instanceof TextContent) {
                        System.out.println(((TextContent) block).getText());
                    }
                }
                break;
            }
        }
    }
}
```

The `prompt()` method returns a `CompletableFuture<Void>`. Calling `.join()` blocks until the agent finishes its execution loop, including any tool calls.

## Adding Tools

Tools let the LLM perform actions beyond text generation. Pi4J provides two ways to define tools.

### Using ToolSpec Builder (Recommended)

The declarative `ToolSpec` builder generates the JSON Schema automatically:

```java
import com.pi4j.agent.tool.AgentTool;
import com.pi4j.agent.tool.AgentToolResult;
import com.pi4j.agent.tool.ToolSpec;

AgentTool weatherTool = ToolSpec.builder("get_weather")
    .description("Get weather information for a city")
    .stringParam("city", true, "City name")
    .integerParam("days", false, "Forecast days (default: 1)")
    .booleanParam("metric", false, "Use metric units")
    .handler((id, args, abort, onUpdate) -> {
        String city = args.getString("city");
        int days = args.getInt("days", 1);
        String weather = fetchWeather(city, days);
        return AgentToolResult.text(weather);
    })
    .build()
    .toAgentTool();
```

Supported parameter types:

| Method | JSON Schema Type | Java Access |
|--------|-----------------|-------------|
| `stringParam(name, required, desc)` | `"string"` | `args.getString(name)` |
| `numberParam(name, required, desc)` | `"number"` | `args.getDouble(name, default)` |
| `integerParam(name, required, desc)` | `"integer"` | `args.getInt(name, default)` |
| `booleanParam(name, required, desc)` | `"boolean"` | `args.getBoolean(name, default)` |

### Implementing AgentTool Interface

For full control over the JSON Schema, implement `AgentTool` directly:

```java
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pi4j.agent.tool.AgentTool;
import com.pi4j.agent.tool.AgentToolResult;
import com.pi4j.agent.tool.ToolUpdateCallback;
import com.pi4j.ai.provider.AbortHandle;
import java.util.Map;

public class TimeTool implements AgentTool {
    @Override
    public String getName() { return "get_time"; }

    @Override
    public String getDescription() { return "Get current time in a timezone"; }

    @Override
    public String getLabel() { return "Get Time"; }

    @Override
    public JsonObject getParameters() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        JsonObject zoneProp = new JsonObject();
        zoneProp.addProperty("type", "string");
        zoneProp.addProperty("description", "IANA timezone (e.g. America/New_York)");
        props.add("zone", zoneProp);
        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("zone");
        schema.add("required", required);
        return schema;
    }

    @Override
    public AgentToolResult execute(
            String toolCallId,
            Map<String, Object> params,
            AbortHandle abortHandle,
            ToolUpdateCallback onUpdate) {
        String zone = String.valueOf(params.get("zone"));
        String time = java.time.ZonedDateTime.now(
            java.time.ZoneId.of(zone)).toString();
        return AgentToolResult.text(time);
    }
}
```

### Registering Tools with the Agent

Pass tools when building the agent:

```java
Agent agent = new Agent(AgentOptions.builder()
    .systemPrompt("You are a helpful assistant with access to tools.")
    .model(claude)
    .tools(Arrays.asList(weatherTool, new TimeTool()))
    .getApiKey(provider -> System.getenv("ANTHROPIC_API_KEY"))
    .build());
```

For details on tool dispatching, middleware, and validation, see the [Tool System Guide](tool-system.md).

## Streaming Events

Subscribe to fine-grained execution events to build real-time UIs or logging:

```java
import com.pi4j.agent.event.*;
import com.pi4j.ai.stream.TextDeltaEvent;

Runnable unsubscribe = agent.subscribe(event -> {
    if (event instanceof AgentStartEvent) {
        System.out.println("--- Agent started ---");
    }
    if (event instanceof MessageUpdateEvent) {
        MessageUpdateEvent update = (MessageUpdateEvent) event;
        if (update.getAssistantMessageEvent() instanceof TextDeltaEvent) {
            System.out.print(((TextDeltaEvent)
                update.getAssistantMessageEvent()).getDelta());
        }
    }
    if (event instanceof ToolExecutionStartEvent) {
        ToolExecutionStartEvent toolEvent = (ToolExecutionStartEvent) event;
        System.out.println("\nExecuting tool: " + toolEvent.getToolName());
    }
    if (event instanceof ToolExecutionEndEvent) {
        ToolExecutionEndEvent toolEvent = (ToolExecutionEndEvent) event;
        System.out.println("Tool finished: " + toolEvent.getToolName()
            + (toolEvent.isError() ? " (error)" : ""));
    }
    if (event instanceof AgentEndEvent) {
        System.out.println("\n--- Agent finished ---");
    }
});

agent.prompt("What's the weather in Tokyo?").join();

// Stop listening when done
unsubscribe.run();
```

The event hierarchy:

```
AgentEvent
├── AgentStartEvent / AgentEndEvent
├── TurnStartEvent / TurnEndEvent
├── MessageStartEvent / MessageUpdateEvent / MessageEndEvent
└── ToolExecutionStartEvent / ToolExecutionUpdateEvent / ToolExecutionEndEvent
```

See the [Architecture Guide](architecture.md#streaming-event-architecture) for details on `EventStream` internals and late-subscriber replay.

## Using Built-in Tools

Pi4J ships with 7 developer tools in the `pi4j-tools` module. Mount them with a single call:

```java
import com.pi4j.tools.BuiltinTools;
import java.nio.file.Paths;

// Select specific tools
List<AgentTool> tools = BuiltinTools.select(
    Paths.get("."), "read", "write", "bash", "grep");

// Or mount all 7 tools
List<AgentTool> allTools = BuiltinTools.all(Paths.get("."));

Agent agent = new Agent(AgentOptions.builder()
    .systemPrompt("You are a coding assistant. Use tools to explore and modify code.")
    .model(claude)
    .tools(tools)
    .getApiKey(provider -> System.getenv("ANTHROPIC_API_KEY"))
    .build());
```

Available built-in tools:

| Tool | Name | Parameters | Description |
|------|------|-----------|-------------|
| `ReadTool` | `read` | `path`, `offset?`, `limit?` | Read file contents with optional line range |
| `WriteTool` | `write` | `path`, `content` | Create or overwrite files |
| `EditTool` | `edit` | `path`, `oldText`, `newText` | Find-and-replace editing |
| `BashTool` | `bash` | `command`, `timeout?` | Execute shell commands |
| `GrepTool` | `grep` | `pattern`, `path?`, `glob?`, `ignoreCase?` | Regex search across files |
| `FindTool` | `find` | `pattern`, `path?`, `limit?` | Find files by glob pattern |
| `LsTool` | `ls` | `path?`, `limit?` | List directory contents |

For complete tool documentation, see the [Tool System Guide](tool-system.md#built-in-tools).

## Session Persistence

Persist conversations across application restarts using `SessionManager`:

```java
import com.pi4j.agent.session.Session;
import com.pi4j.agent.session.SessionInfo;
import com.pi4j.agent.session.SessionManager;
import com.pi4j.agent.LlmAgentMessage;
import com.pi4j.ai.types.UserMessage;
import com.pi4j.ai.types.TextContent;
import java.nio.file.Paths;
import java.util.Collections;

SessionManager sessionManager = new SessionManager(Paths.get("sessions"));

// Create a new session
Session session = sessionManager.create("user-123");

// Append a message
session.appendMessage(new LlmAgentMessage(new UserMessage(
    Collections.singletonList(new TextContent("Hello"))
)));

// Load an existing session
Session loaded = sessionManager.load("user-123");
List<AgentMessage> history = loaded.getMessages();

// List all sessions
for (SessionInfo info : sessionManager.list()) {
    System.out.println(info.getSessionId());
}

// Delete a session
sessionManager.delete("user-123");
```

Sessions are stored as JSONL (JSON Lines) files, one file per session ID. Each line is a `SessionEntry` with `type`, `id`, `parentId`, `timestamp`, and `data` fields, supporting tree-structured branching via `session.fork(entryId)`.

## Abort and Queues

### Aborting Execution

Cancel a running agent from any thread:

```java
CompletableFuture<Void> future = agent.prompt("Analyze this large codebase");

// Abort after 30 seconds
CompletableFuture.runAsync(() -> {
    try { Thread.sleep(30_000); } catch (InterruptedException e) { return; }
    agent.abort();
});
```

### Steering and Follow-up

Inject messages while the agent is running:

```java
// Steering: interrupt current execution, skip remaining tools
agent.steer(new LlmAgentMessage(new UserMessage(
    Collections.singletonList(new TextContent("Stop and focus on file X instead"))
)));

// Follow-up: process after current loop completes
agent.followUp(new LlmAgentMessage(new UserMessage(
    Collections.singletonList(new TextContent("Now summarize your changes"))
)));
```

See the [Architecture Guide](architecture.md#agent-execution-loop) for a detailed explanation of the dual-loop design.

## Next Steps

- **[Architecture & Design](architecture.md)** — Understand the module structure, execution loop, and design decisions
- **[Tool System Guide](tool-system.md)** — Deep dive into tool definitions, dispatching, middleware, and validation
- **[LLM Providers Guide](providers.md)** — Configure and use the 13 supported LLM providers
