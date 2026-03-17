# Tool System Guide

This guide covers everything about defining, executing, dispatching, and validating tools in Pi4J.

## Overview

Tools are the primary extension point of Pi4J. They allow the LLM to perform actions beyond text generation -- querying databases, calling APIs, reading files, executing commands, and more.

The tool lifecycle in Pi4J:

1. **Definition** — You define tools with a name, description, JSON Schema parameters, and an execution handler
2. **Registration** — Tools are passed to `AgentOptions.builder().tools(...)` when creating an agent
3. **Selection** — The LLM decides which tools to call based on the user's request and tool descriptions
4. **Validation** — Pi4J validates the LLM's arguments against the JSON Schema before execution
5. **Execution** — The tool handler runs, optionally reporting progress and checking for abort
6. **Result** — The result is appended to the conversation and sent back to the LLM

## Defining Tools

### Using ToolSpec Builder

The `ToolSpec` builder provides a declarative API that generates JSON Schema automatically:

```java
import com.pi4j.agent.tool.AgentTool;
import com.pi4j.agent.tool.AgentToolResult;
import com.pi4j.agent.tool.ToolSpec;

AgentTool searchTool = ToolSpec.builder("search_documents")
    .description("Search the document database by query")
    .label("Search Documents")  // human-readable label for UI
    .stringParam("query", true, "Search query string")
    .integerParam("limit", false, "Maximum number of results (default: 10)")
    .booleanParam("exact_match", false, "Use exact matching instead of fuzzy")
    .handler((toolCallId, args, abortHandle, onUpdate) -> {
        String query = args.getString("query");
        int limit = args.getInt("limit", 10);
        boolean exact = args.getBoolean("exact_match", false);

        List<Document> results = documentService.search(query, limit, exact);
        return AgentToolResult.text(formatResults(results));
    })
    .build()
    .toAgentTool();
```

The builder supports four parameter types:

| Method | JSON Schema Type | ToolArgs Accessor |
|--------|-----------------|-------------------|
| `stringParam(name, required, description)` | `"string"` | `args.getString(name)` |
| `numberParam(name, required, description)` | `"number"` | `args.getDouble(name, defaultValue)` |
| `integerParam(name, required, description)` | `"integer"` | `args.getInt(name, defaultValue)` |
| `booleanParam(name, required, description)` | `"boolean"` | `args.getBoolean(name, defaultValue)` |

The `ToolArgs` wrapper provides typed access to parameter values with default value support. Under the hood, parameters are stored as `Map<String, Object>` from Gson deserialization.

### Implementing AgentTool Interface

For complex schemas (nested objects, arrays, enums), implement `AgentTool` directly with a hand-crafted JSON Schema:

```java
public class CreateIssueTool implements AgentTool {
    @Override public String getName() { return "create_issue"; }
    @Override public String getDescription() { return "Create a new project issue"; }
    @Override public String getLabel() { return "Create Issue"; }

    @Override
    public JsonObject getParameters() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        // string param with enum constraint
        JsonObject priority = new JsonObject();
        priority.addProperty("type", "string");
        JsonArray enumValues = new JsonArray();
        enumValues.add("low"); enumValues.add("medium"); enumValues.add("high");
        priority.add("enum", enumValues);
        props.add("priority", priority);
        // array param
        JsonObject labels = new JsonObject();
        labels.addProperty("type", "array");
        JsonObject items = new JsonObject();
        items.addProperty("type", "string");
        labels.add("items", items);
        props.add("labels", labels);
        schema.add("properties", props);
        return schema;
    }

    @Override
    public AgentToolResult execute(String toolCallId, Map<String, Object> params,
            AbortHandle abortHandle, ToolUpdateCallback onUpdate) {
        String priority = (String) params.getOrDefault("priority", "medium");
        Issue issue = issueTracker.create(priority);
        return AgentToolResult.text("Created issue #" + issue.getId());
    }
}
```

### Tool Parameters & JSON Schema

Pi4J uses standard [JSON Schema](https://json-schema.org/) for tool parameter definitions. The schema is passed directly to the LLM, which generates conforming arguments.

A typical schema structure:

```json
{
  "type": "object",
  "properties": {
    "param_name": {
      "type": "string | number | integer | boolean | array | object",
      "description": "What this parameter does"
    }
  },
  "required": ["param_name"]
}
```

Tips for effective schemas:
- Write clear, specific descriptions -- the LLM uses them to decide parameter values
- Mark parameters as `required` only when they have no sensible default
- Use `"enum"` to constrain string values to a known set
- Keep schemas simple; deeply nested objects confuse some LLMs

## Tool Execution

### Execution Lifecycle

When the LLM returns a tool call, Pi4J processes it through this pipeline:

```
LLM Response (with ToolCallContent)
  |
  v
1. ToolValidator.validate()          -- Validate args against JSON Schema
  |                                     (type checking, required fields, coercion)
  v
2. ToolExecutionStartEvent fired     -- Notify listeners
  |
  v
3. ToolExecutor.execute()            -- Run middleware chain
  |
  v
4. ToolMiddleware pipeline           -- Timeout, retry, error handling, etc.
  |
  v
5. ToolDispatcher.dispatch()         -- Route to execution thread
  |
  v
6. AgentTool.execute()               -- Your tool handler runs
  |
  v
7. ToolExecutionEndEvent fired       -- Notify listeners with result
  |
  v
8. ToolResultMessage appended        -- Result added to conversation
  |
  v
9. Next tool call (or back to LLM)
```

### AbortHandle

The `AbortHandle` provides cooperative cancellation shared across the agent, LLM calls, and tool executions:

```java
@Override
public AgentToolResult execute(
        String toolCallId,
        Map<String, Object> params,
        AbortHandle abortHandle,
        ToolUpdateCallback onUpdate) {

    for (int i = 0; i < 1000; i++) {
        // Check periodically in long-running operations
        abortHandle.throwIfAborted(); // throws AbortException

        processItem(i);
    }
    return AgentToolResult.text("Processed 1000 items");
}
```

Key methods:

| Method | Description |
|--------|-------------|
| `abortHandle.isAborted()` | Check if abort was requested (non-throwing) |
| `abortHandle.throwIfAborted()` | Throw `AbortException` if aborted |
| `abortHandle.addListener(runnable)` | Register a cleanup callback |
| `abortHandle.removeListener(runnable)` | Unregister a cleanup callback |

### ToolUpdateCallback

For long-running tools, report intermediate progress to subscribers:

```java
@Override
public AgentToolResult execute(
        String toolCallId,
        Map<String, Object> params,
        AbortHandle abortHandle,
        ToolUpdateCallback onUpdate) {

    List<String> files = findFiles(params);

    for (int i = 0; i < files.size(); i++) {
        abortHandle.throwIfAborted();

        String result = processFile(files.get(i));

        // Report progress -- triggers ToolExecutionUpdateEvent
        onUpdate.onUpdate(AgentToolResult.text(
            "Processing " + (i + 1) + "/" + files.size() + ": " + files.get(i)));
    }

    return AgentToolResult.text("Processed " + files.size() + " files");
}
```

### AgentToolResult

Tool results can contain text, errors, or images:

```java
// Plain text result
AgentToolResult.text("The answer is 42");

// Error result (sent back to LLM so it can recover)
AgentToolResult.error("File not found: /path/to/missing.txt");

// Result with an image attachment
AgentToolResult.withImage(
    "Generated chart for Q4 revenue",
    base64ImageData,
    "image/png"
);
```

The `details` field (accessible via the full constructor) carries arbitrary metadata for logging or UI display, but is not sent to the LLM.

## Tool Dispatching

The `ToolDispatcher` controls which thread executes the tool. Pi4J supports three dispatch modes via the `ToolDispatchMode` enum:

### DIRECT Mode (Default)

Tool executes on the agent's own executor thread. This is the simplest and default mode:

```java
// Default behavior -- no special configuration needed
Agent agent = new Agent(AgentOptions.builder()
    .tools(myTools)
    // ...
    .build());
```

### BLOCKING Mode

Tool is submitted to a separate thread pool via `DefaultToolDispatcher`, freeing the agent executor. Useful for I/O-bound tools:

```java
// Set via middleware that modifies the context
ToolMiddleware blockingMiddleware = (context, chain) -> {
    if ("slow_query".equals(context.getToolName())) {
        return chain.proceed(context.withDispatchMode(ToolDispatchMode.BLOCKING));
    }
    return chain.proceed(context);
};
```

### HOST_DISPATCHER Mode

Delegates to a user-provided `ToolDispatcher` for fully custom execution logic (e.g., dispatching to a UI thread, a remote worker, or a sandbox):

```java
ToolDispatcher customDispatcher = (context, invocation) -> {
    // Run on a custom thread, sandbox, or remote worker
    return myExecutionService.run(() -> invocation.invoke());
};

Agent agent = new Agent(AgentOptions.builder()
    .toolDispatcher(new DefaultToolDispatcher(
        Executors.newCachedThreadPool(), customDispatcher))
    // ...
    .build());
```

## Middleware Pipeline

The `ToolMiddleware` interface enables cross-cutting concerns around tool execution. Middlewares form a chain -- each can inspect, modify, or short-circuit the execution.

### ToolMiddleware Interface

```java
public interface ToolMiddleware {
    AgentToolResult handle(ToolExecutionContext context, ToolExecutionChain chain)
        throws Exception;
}
```

The `ToolExecutionChain.proceed(context)` call passes control to the next middleware (or the tool itself if at the end of the chain).

### Built-in Middlewares

Pi4J provides four built-in middlewares via `DefaultMiddlewares`:

#### Timeout Middleware

Wraps tool execution with a deadline:

```java
// With a default timeout of 30 seconds
ToolMiddleware timeout = DefaultMiddlewares.timeout(30_000);

// Per-tool timeout via context
ToolMiddleware perToolTimeout = (context, chain) -> {
    if ("bash".equals(context.getToolName())) {
        return chain.proceed(context.withTimeoutMillis(60_000));
    }
    return chain.proceed(context);
};
```

#### Retry Middleware

Retries failed tool executions:

```java
// Default: 0 retries (use per-tool maxRetries)
ToolMiddleware retry = DefaultMiddlewares.retry();

// With a default of 3 retries
ToolMiddleware retry3 = DefaultMiddlewares.retry(3);
```

#### Error Middleware

Catches exceptions and converts them to `AgentToolResult.error()` so the LLM can see the error and recover:

```java
ToolMiddleware errorHandler = DefaultMiddlewares.error();

// With custom error message mapping
ToolMiddleware customError = DefaultMiddlewares.error((throwable, context) ->
    "Tool " + context.getToolName() + " failed: " + throwable.getMessage());
```

#### Confirmation Middleware

Gates tool execution on a confirmation check (for dangerous operations):

```java
ToolMiddleware confirmation = DefaultMiddlewares.confirmation();

// With custom confirmation logic
ToolMiddleware uiConfirmation = DefaultMiddlewares.confirmation(context -> {
    if (!context.isConfirmationRequired()) return true;
    return showConfirmDialog("Allow " + context.getToolName() + "?");
});
```

### Configuring the Middleware Pipeline

Middlewares are applied in order. The first middleware in the list is the outermost:

```java
Agent agent = new Agent(AgentOptions.builder()
    .systemPrompt("You are a helpful assistant.")
    .model(model)
    .tools(tools)
    .toolMiddlewares(Arrays.asList(
        DefaultMiddlewares.error(),        // outermost: catch all errors
        DefaultMiddlewares.timeout(30_000), // timeout enforcement
        DefaultMiddlewares.retry(2)         // retry on failure
    ))
    .getApiKey(provider -> apiKey)
    .build());
```

Execution order: `error -> timeout -> retry -> tool.execute()`.

## Built-in Tools

The `pi4j-tools` module provides 7 developer tools. Each tool is an `AgentTool` implementation that operates within a configurable working directory.

### ReadTool

Reads file contents with optional line range.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `path` | string | Yes | File path (relative to work directory) |
| `offset` | integer | No | Start line number (1-based) |
| `limit` | integer | No | Number of lines to read |

Output is automatically truncated by the `Truncator` (default: 2000 lines / 50KB).

### WriteTool

Creates or overwrites a file.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `path` | string | Yes | File path |
| `content` | string | Yes | File content to write |

### EditTool

Performs find-and-replace editing on a file.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `path` | string | Yes | File path |
| `oldText` | string | Yes | Text to find |
| `newText` | string | Yes | Replacement text |

### BashTool

Executes a shell command with abort support.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `command` | string | Yes | Shell command to execute |
| `timeout` | integer | No | Timeout in seconds |

Output (stdout + stderr) is truncated from the tail to preserve the most recent output.

### GrepTool

Regex search across files.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `pattern` | string | Yes | Regex pattern |
| `path` | string | No | Search directory (default: work directory) |
| `glob` | string | No | File glob filter (e.g., `"*.java"`) |
| `ignoreCase` | boolean | No | Case-insensitive matching |

### FindTool

Find files by glob pattern.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `pattern` | string | Yes | Glob pattern (e.g., `"**/*.xml"`) |
| `path` | string | No | Search directory |
| `limit` | integer | No | Maximum results |

### LsTool

List directory contents.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `path` | string | No | Directory path (default: work directory) |
| `limit` | integer | No | Maximum entries |

### Mounting Built-in Tools

```java
import com.pi4j.tools.BuiltinTools;
import java.nio.file.Paths;

// All 7 tools
List<AgentTool> all = BuiltinTools.all(Paths.get("/project/root"));

// Select by name
List<AgentTool> subset = BuiltinTools.select(Paths.get("/project/root"),
    "read", "grep", "find", "ls");

// Individual tool with custom operations
ReadTool reader = BuiltinTools.readTool(Paths.get("/project/root"));
BashTool bash = BuiltinTools.bashTool(Paths.get("/project/root"));
```

## Tool Validation

`ToolValidator` validates tool call arguments against the JSON Schema before execution. This catches malformed LLM outputs early and provides clear error messages back to the LLM.

### Validation Rules

| Check | Description |
|-------|-------------|
| Required fields | Missing required properties raise `ToolValidationException` |
| Type checking | Values must match declared type (`string`, `number`, `integer`, `boolean`, `array`, `object`) |
| Type coercion | String `"123"` is coerced to number `123`; string `"true"` to boolean `true` |

### Validation Flow

```java
// Internal -- called automatically by Agent before tool execution
Map<String, Object> validated = ToolValidator.validate(toolDefinition, toolCallContent);
// If validation fails, a ToolValidationException is caught and
// an error result is sent back to the LLM
```

When validation fails, the error message is returned to the LLM as an `AgentToolResult.error()`, giving the LLM a chance to correct its parameters and retry.

## Best Practices

1. **Write descriptive tool descriptions** — The LLM selects tools based on descriptions. Be specific about when to use the tool, what it returns, and any limitations.

2. **Keep parameter schemas simple** — Flat objects with primitive types work best across all providers. Avoid deeply nested schemas.

3. **Check AbortHandle in long-running tools** — Call `abortHandle.throwIfAborted()` inside loops. This ensures responsive cancellation.

4. **Use the error middleware** — Always include `DefaultMiddlewares.error()` in your middleware chain so that tool exceptions are converted to LLM-readable error messages rather than crashing the agent loop.

5. **Return structured text** — Format tool results as structured text (JSON, tables, or lists) rather than free-form prose. This helps the LLM parse and use the results accurately.

6. **Use ToolSpec for simple tools** — The declarative builder reduces boilerplate. Reserve the `AgentTool` interface for complex schemas.

7. **Set timeouts for external calls** — Use `DefaultMiddlewares.timeout()` or per-tool `context.withTimeoutMillis()` to prevent hung tools from blocking the agent indefinitely.

8. **Limit output size** — Use the `Truncator` utility for tools that may produce large outputs. The built-in tools already handle this; apply the same pattern to custom tools.

For more context on how tools fit into the overall execution model, see the [Architecture Guide](architecture.md#agent-execution-loop). For provider-specific tool behavior, see the [Providers Guide](providers.md).
