# 工具系统指南

## 概览

工具是 Pi4J Agent 与外部世界交互的桥梁。当 LLM 判断需要执行某个操作时（如查询天气、读取文件、执行命令），它会生成一个工具调用请求，Agent 框架负责路由到对应的工具实现、执行并将结果回传给 LLM。

Pi4J 的工具系统具有以下特点：

- 自定义工具与内置工具地位平等
- 基于 JSON Schema 的参数定义和校验
- 3 种调度模式（DIRECT / BLOCKING / HOST_DISPATCHER）
- 中间件管线处理横切关注点
- 协作式中止和进度回调

## 定义工具

### 使用 ToolSpec Builder

`ToolSpec` 提供声明式 API，适合参数结构简单的工具：

```java
import com.pi4j.agent.tool.ToolSpec;
import com.pi4j.agent.tool.AgentTool;
import com.pi4j.agent.tool.AgentToolResult;

AgentTool searchTool = ToolSpec.builder("search_docs")
    .description("在知识库中搜索文档")
    .label("搜索文档")
    .stringParam("query", true, "搜索关键词")
    .integerParam("limit", false, "返回结果数量上限，默认 10")
    .booleanParam("exact", false, "是否精确匹配")
    .handler((toolCallId, args, abortHandle, onUpdate) -> {
        String query = args.requireString("query");
        int limit = args.has("limit") ? args.requireInt("limit") : 10;
        boolean exact = args.has("exact") ? args.requireBoolean("exact") : false;

        // 执行搜索逻辑
        String results = doSearch(query, limit, exact);
        return AgentToolResult.text(results);
    })
    .build()
    .toAgentTool();
```

Builder 支持的参数类型方法：

| 方法 | JSON Schema 类型 | Java 取值方法 |
|------|-----------------|--------------|
| `stringParam(name, required, desc)` | `"string"` | `args.requireString(name)` |
| `numberParam(name, required, desc)` | `"number"` | `args.requireNumber(name)` → `double` |
| `integerParam(name, required, desc)` | `"integer"` | `args.requireInt(name)` → `int` |
| `booleanParam(name, required, desc)` | `"boolean"` | `args.requireBoolean(name)` → `boolean` |

`ToolArgs` 类提供类型安全的参数访问：

```java
public class ToolArgs {
    boolean has(String key);           // 参数是否存在
    Object get(String key);            // 获取原始值
    String requireString(String key);  // 获取字符串（必填）
    int requireInt(String key);        // 获取整数（必填）
    double requireNumber(String key);  // 获取浮点数（必填）
    boolean requireBoolean(String key);// 获取布尔值（必填）
    Map<String, Object> asMap();       // 转为 Map
}
```

### 实现 AgentTool 接口

需要复杂 JSON Schema（嵌套对象、数组、枚举）时，直接实现接口：

```java
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pi4j.agent.tool.AgentTool;
import com.pi4j.agent.tool.AgentToolResult;
import com.pi4j.agent.tool.ToolUpdateCallback;
import com.pi4j.ai.provider.AbortHandle;
import java.util.Map;

public class DatabaseQueryTool implements AgentTool {

    @Override
    public String getName() { return "query_database"; }

    @Override
    public String getDescription() {
        return "执行 SQL 查询并返回结果";
    }

    @Override
    public String getLabel() { return "数据库查询"; }

    @Override
    public JsonObject getParameters() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject sql = new JsonObject();
        sql.addProperty("type", "string");
        sql.addProperty("description", "SQL 查询语句");
        props.add("sql", sql);

        JsonObject maxRows = new JsonObject();
        maxRows.addProperty("type", "integer");
        maxRows.addProperty("description", "最大返回行数");
        props.add("maxRows", maxRows);

        schema.add("properties", props);

        JsonArray required = new JsonArray();
        required.add("sql");
        schema.add("required", required);

        return schema;
    }

    @Override
    public AgentToolResult execute(
            String toolCallId,
            Map<String, Object> params,
            AbortHandle abortHandle,
            ToolUpdateCallback onUpdate) {

        String sql = String.valueOf(params.get("sql"));
        int maxRows = params.containsKey("maxRows")
            ? ((Number) params.get("maxRows")).intValue()
            : 100;

        try {
            String result = executeQuery(sql, maxRows);
            return AgentToolResult.text(result);
        } catch (Exception e) {
            return AgentToolResult.error("查询失败: " + e.getMessage());
        }
    }
}
```

### 工具参数与 JSON Schema

工具参数通过 `getParameters()` 返回标准 JSON Schema 格式：

```json
{
  "type": "object",
  "properties": {
    "city": {
      "type": "string",
      "description": "城市名称"
    },
    "unit": {
      "type": "string",
      "description": "温度单位",
      "enum": ["celsius", "fahrenheit"]
    },
    "details": {
      "type": "object",
      "properties": {
        "humidity": { "type": "boolean" },
        "wind": { "type": "boolean" }
      }
    }
  },
  "required": ["city"]
}
```

支持的类型：`string`、`number`、`integer`、`boolean`、`array`、`object`。

## 工具执行

### 执行生命周期

当 LLM 返回工具调用时，框架按以下流程处理：

```
LLM 返回 ToolCallContent
        │
        ▼
1. ToolValidator.validate()         ← 基于 JSON Schema 校验参数
        │                              （含类型强制转换）
        ▼
2. ToolMiddleware 管线              ← 依次执行中间件链
        │
        ▼
3. AgentTool.execute()              ← 调用工具实现
        │
        ▼
4. AgentToolResult                  ← 返回结果
        │
        ▼
5. 构造 ToolResultMessage           ← 包装为消息回传 LLM
```

相关事件触发顺序：

| 事件 | 时机 |
|------|------|
| `ToolExecutionStartEvent` | 工具开始执行前 |
| `ToolExecutionUpdateEvent` | 工具通过 `onUpdate` 报告进度 |
| `ToolExecutionEndEvent` | 工具执行完成后 |

### AbortHandle

`AbortHandle` 提供协作式中止机制。Agent、LLM 调用和工具执行共享同一个句柄：

```java
public class AbortHandle {
    void abort();                    // 触发中止
    boolean isAborted();             // 检查是否已中止
    void throwIfAborted();           // 如已中止则抛出 AbortException
    void addListener(Runnable r);    // 注册中止回调
    void removeListener(Runnable r); // 移除回调
}
```

工具实现中应定期检查中止状态：

```java
@Override
public AgentToolResult execute(String id, Map<String, Object> params,
                               AbortHandle abort, ToolUpdateCallback onUpdate) {
    for (int i = 0; i < 100; i++) {
        abort.throwIfAborted(); // 如已中止则抛出异常
        processChunk(i);
    }
    return AgentToolResult.text("完成");
}
```

### ToolUpdateCallback

长耗时工具可以通过回调报告中间进度：

```java
@Override
public AgentToolResult execute(String id, Map<String, Object> params,
                               AbortHandle abort, ToolUpdateCallback onUpdate) {
    List<String> files = findFiles(params);
    for (int i = 0; i < files.size(); i++) {
        abort.throwIfAborted();
        processFile(files.get(i));

        // 报告进度
        onUpdate.onUpdate(AgentToolResult.text(
            "已处理 " + (i + 1) + "/" + files.size() + " 个文件"
        ));
    }
    return AgentToolResult.text("全部处理完成");
}
```

进度更新会触发 `ToolExecutionUpdateEvent`，UI 层可据此显示进度条。

### AgentToolResult

工具执行结果通过 `AgentToolResult` 返回，提供三种便捷构造方法：

```java
// 纯文本结果
AgentToolResult.text("查询结果：晴天，25°C")

// 错误结果
AgentToolResult.error("API 调用失败：超时")

// 带图片的结果（文本 + Base64 图片）
AgentToolResult.withImage("截图如下", base64Data, "image/png")
```

## 工具调度

`ToolDispatchMode` 定义工具的执行方式：

### DIRECT 模式

默认模式，工具直接在 Agent 执行线程中同步执行：

```java
// ToolDispatchMode.DIRECT — 默认行为，无需额外配置
```

### BLOCKING 模式

工具执行被阻塞，直到外部确认。适用于需要用户审批的操作：

```java
ToolExecutionContext ctx = context.withDispatchMode(ToolDispatchMode.BLOCKING);
```

### HOST_DISPATCHER 模式

由宿主应用的自定义 `ToolDispatcher` 接管调度：

```java
ToolDispatcher customDispatcher = (context, invocation) -> {
    // 自定义调度逻辑：如线程池执行、排队、优先级等
    return invocation.invoke();
};

Agent agent = new Agent(AgentOptions.builder()
    .toolDispatcher(customDispatcher)
    // ...
    .build());
```

`ToolDispatcher` 接口：

```java
public interface ToolDispatcher {
    AgentToolResult dispatch(ToolExecutionContext context, ToolInvocation invocation)
        throws Exception;
}

public interface ToolInvocation {
    AgentToolResult invoke() throws Exception;
}
```

## 中间件管线

`ToolMiddleware` 接口允许在工具执行前后插入横切逻辑：

```java
public interface ToolMiddleware {
    AgentToolResult handle(ToolExecutionContext context, ToolExecutionChain chain)
        throws Exception;
}

public interface ToolExecutionChain {
    AgentToolResult proceed(ToolExecutionContext context) throws Exception;
}
```

### 自定义中间件示例

**超时中间件**：

```java
public class TimeoutMiddleware implements ToolMiddleware {
    private final long timeoutMillis;

    public TimeoutMiddleware(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public AgentToolResult handle(ToolExecutionContext context,
                                  ToolExecutionChain chain) throws Exception {
        ToolExecutionContext withTimeout = context.withTimeoutMillis(timeoutMillis);
        return chain.proceed(withTimeout);
    }
}
```

**重试中间件**：

```java
public class RetryMiddleware implements ToolMiddleware {
    private final int maxRetries;

    public RetryMiddleware(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    @Override
    public AgentToolResult handle(ToolExecutionContext context,
                                  ToolExecutionChain chain) throws Exception {
        ToolExecutionContext ctx = context.withMaxRetries(maxRetries);
        Exception lastError = null;
        for (int i = 0; i <= maxRetries; i++) {
            try {
                return chain.proceed(ctx);
            } catch (Exception e) {
                lastError = e;
                ctx.getAbortHandle().throwIfAborted();
            }
        }
        throw lastError;
    }
}
```

**日志中间件**：

```java
public class LoggingMiddleware implements ToolMiddleware {
    @Override
    public AgentToolResult handle(ToolExecutionContext context,
                                  ToolExecutionChain chain) throws Exception {
        long start = System.currentTimeMillis();
        System.out.println("工具开始: " + context.getToolName());
        try {
            AgentToolResult result = chain.proceed(context);
            System.out.println("工具完成: " + context.getToolName()
                + " (" + (System.currentTimeMillis() - start) + "ms)");
            return result;
        } catch (Exception e) {
            System.out.println("工具失败: " + context.getToolName()
                + " - " + e.getMessage());
            throw e;
        }
    }
}
```

### 配置中间件链

```java
import java.util.Arrays;

Agent agent = new Agent(AgentOptions.builder()
    .systemPrompt("你是一个助手")
    .model(model)
    .tools(tools)
    .toolMiddlewares(Arrays.asList(
        new LoggingMiddleware(),
        new TimeoutMiddleware(30_000),
        new RetryMiddleware(3)
    ))
    .getApiKey(provider -> System.getenv("API_KEY"))
    .build());
```

中间件按注册顺序组成链式调用：`Logging → Timeout → Retry → 工具执行`。

## 内置工具

`pi4j-tools` 模块提供 7 个开发者工具，通过 `BuiltinTools` 工厂类创建：

### ReadTool

| 属性 | 值 |
|------|---|
| 名称 | `read` |
| 参数 | `path`(string, 必填), `offset`(integer, 可选), `limit`(integer, 可选) |
| 说明 | 读取文件内容，支持行号偏移和限制。输出自动截断（默认 2000 行 / 50KB） |

### WriteTool

| 属性 | 值 |
|------|---|
| 名称 | `write` |
| 参数 | `path`(string, 必填), `content`(string, 必填) |
| 说明 | 创建或覆盖文件内容 |

### EditTool

| 属性 | 值 |
|------|---|
| 名称 | `edit` |
| 参数 | `path`(string, 必填), `oldText`(string, 必填), `newText`(string, 必填) |
| 说明 | 查找替换编辑，基于文本匹配 |

### BashTool

| 属性 | 值 |
|------|---|
| 名称 | `bash` |
| 参数 | `command`(string, 必填), `timeout`(integer, 可选) |
| 说明 | 执行 shell 命令，支持超时控制和中止。输出自动截断（保留尾部） |

### GrepTool

| 属性 | 值 |
|------|---|
| 名称 | `grep` |
| 参数 | `pattern`(string, 必填), `path`(string, 可选), `glob`(string, 可选), `ignoreCase`(boolean, 可选) |
| 说明 | 跨文件正则搜索 |

### FindTool

| 属性 | 值 |
|------|---|
| 名称 | `find` |
| 参数 | `pattern`(string, 必填), `path`(string, 可选), `limit`(integer, 可选) |
| 说明 | 按 glob 模式查找文件 |

### LsTool

| 属性 | 值 |
|------|---|
| 名称 | `ls` |
| 参数 | `path`(string, 可选), `limit`(integer, 可选) |
| 说明 | 列出目录内容 |

### 使用示例

```java
import com.pi4j.tools.BuiltinTools;
import java.nio.file.Paths;

// 选择部分工具
List<AgentTool> tools = BuiltinTools.select(
    Paths.get("/workspace"), "read", "write", "bash", "grep"
);

// 使用全部工具
List<AgentTool> allTools = BuiltinTools.all(Paths.get("/workspace"));

// 单独创建特定工具
AgentTool bashTool = BuiltinTools.bashTool(Paths.get("/workspace"));
```

## 工具校验

`ToolValidator` 基于 JSON Schema 对 LLM 生成的工具调用参数进行校验：

```java
Map<String, Object> validated = ToolValidator.validate(tool, toolCallContent);
```

校验流程：

1. **必填字段检查** — 确保 `required` 数组中的字段存在
2. **类型匹配** — 检查字段值是否符合 Schema 定义的类型
3. **类型强制转换** — 自动处理 LLM 返回类型不精确的情况

强制转换规则：

| Schema 类型 | 转换逻辑 |
|------------|---------|
| `string` | `String.valueOf(value)` |
| `number` | 字符串 `"3.14"` → `Double 3.14` |
| `integer` | 字符串 `"42"` → `Integer 42` |
| `boolean` | 字符串 `"true"/"false"` → `Boolean` |
| `array` | 仅检查是否为 `List`，不做转换 |
| `object` | 仅检查是否为 `Map`，不做转换 |

校验失败时抛出 `ToolValidationException`。

## 最佳实践

1. **优先使用 ToolSpec** — 对于简单参数的工具，ToolSpec 比手写 JSON Schema 更简洁、不易出错

2. **提供清晰的描述** — `description` 是 LLM 决定何时调用工具的关键信息，应清晰描述工具的功能和使用场景

3. **检查中止状态** — 长耗时工具应定期调用 `abortHandle.throwIfAborted()`，确保可及时中止

4. **使用进度回调** — 对于耗时操作，通过 `onUpdate` 报告进度，提升用户体验

5. **返回结构化信息** — 工具返回的文本应结构化、易于 LLM 理解，避免返回过大的内容

6. **使用中间件处理通用逻辑** — 超时、重试、日志等横切关注点放入中间件，保持工具实现的纯粹

7. **限制工具数量** — 注册过多工具会增加 LLM 上下文消耗和选择困难，按需注册

---

相关文档：
- [快速开始](getting-started.md) — 上手第一个 Agent
- [架构与设计](architecture.md) — 执行循环和事件流详解
- [LLM 提供商指南](providers.md) — 配置不同的 LLM 提供商
