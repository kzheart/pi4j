# Pi4J - 极简主义 Java AI Agent 框架

> 基于 Java 8 构建的轻量级、可组合 AI Agent 框架。
> 继承 [pi-mono](https://github.com/badlogic/pi-mono) 的极简哲学 —— 工具包，而非框架。

[![Java](https://img.shields.io/badge/Java-8%2B-orange)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](LICENSE)
[![Version](https://img.shields.io/badge/version-1.0.8--SNAPSHOT-green)]()

[English](README.md) | 中文

## 特性

- **Agent 执行循环** — 内置多轮工具调用、流式输出和中止支持的执行引擎
- **13 个 LLM 提供商** — Anthropic、OpenAI（Completions 和 Responses API）、Google Gemini/Vertex、Groq、Mistral、Ollama、xAI、OpenRouter、百炼，以及自定义 OpenAI 兼容端点
- **可组合工具系统** — 声明式工具定义，支持 JSON Schema 参数校验、3 种调度模式和中间件管线
- **事件驱动** — 细粒度事件流，支持迟到订阅者的事件重放
- **会话管理** — 基于 JSONL 的会话持久化，支持多租户隔离
- **内置工具** — 开箱即用的 7 个开发者工具（文件读写、bash、grep、find、ls）
- **零魔法** — 无重量级依赖、无反射、无注解处理。仅依赖 Gson + OkHttp + SLF4J
- **Java 8 兼容** — 可嵌入任何 Java 应用，包括遗留系统

## 快速开始

### 安装

通过 Gradle 添加依赖：

```kotlin
repositories {
    mavenLocal()
}

dependencies {
    implementation("com.pi4j:pi4j-agent:1.0.8-SNAPSHOT")
    implementation("com.pi4j:pi4j-tools:1.0.8-SNAPSHOT") // 可选：内置工具
}
```

### 3 行代码创建 Agent

```java
Agent agent = new Agent(AgentOptions.builder()
    .systemPrompt("你是一个有用的助手")
    .model(model)
    .getApiKey(provider -> System.getenv("ANTHROPIC_API_KEY"))
    .build());

agent.prompt("你好！").join();

// 获取响应
AgentState state = agent.getState();
```

### 自定义工具

使用声明式 `ToolSpec` 构建器定义工具：

```java
AgentTool weatherTool = ToolSpec.builder("get_weather")
    .description("查询城市天气信息")
    .stringParam("city", true, "城市名称")
    .integerParam("days", false, "预报天数（默认 1）")
    .handler((id, args, abort, onUpdate) -> {
        String city = args.getString("city");
        int days = args.getInt("days", 1);
        String weather = fetchWeather(city, days);
        return AgentToolResult.text(weather);
    })
    .build()
    .toAgentTool();
```

或者直接实现 `AgentTool` 接口以获得完全控制：

```java
AgentTool timeTool = new AgentTool() {
    @Override public String getName() { return "get_time"; }
    @Override public String getDescription() { return "查询指定时区的当前时间"; }
    @Override public String getLabel() { return "获取时间"; }
    @Override public JsonObject getParameters() { /* JSON Schema */ }
    @Override public AgentToolResult execute(String id, Map<String, Object> params,
                                             AbortHandle abort, ToolUpdateCallback onUpdate) {
        String zone = String.valueOf(params.get("zone"));
        return AgentToolResult.text(ZonedDateTime.now(ZoneId.of(zone)).toString());
    }
};
```

### 内置工具

一行代码挂载开发者工具：

```java
// 选择特定工具
List<AgentTool> tools = BuiltinTools.select(Paths.get("."), "read", "write", "bash", "grep");

// 或挂载全部 7 个工具
List<AgentTool> allTools = BuiltinTools.all(Paths.get("."));

Agent agent = new Agent(AgentOptions.builder()
    .systemPrompt("你是一个编码助手")
    .model(model)
    .tools(tools)
    .build());
```

可用的内置工具：

| 工具 | 说明 |
|------|------|
| `read` | 读取文件内容（支持行号偏移/限制） |
| `write` | 创建或覆盖文件 |
| `edit` | 编辑指定行范围 |
| `bash` | 执行 shell 命令（支持超时和中止） |
| `grep` | 跨文件正则搜索 |
| `find` | 按 glob 模式查找文件 |
| `ls` | 列出目录内容 |

### 事件监听

订阅细粒度的执行事件：

```java
Runnable unsubscribe = agent.subscribe(event -> {
    if (event instanceof MessageUpdateEvent) {
        MessageUpdateEvent update = (MessageUpdateEvent) event;
        if (update.getAssistantMessageEvent() instanceof TextDeltaEvent) {
            // 流式输出到控制台
            System.out.print(((TextDeltaEvent) update.getAssistantMessageEvent()).getDelta());
        }
    }
    if (event instanceof ToolExecutionStartEvent) {
        System.out.println("正在执行: " + ((ToolExecutionStartEvent) event).getToolName());
    }
});

agent.prompt("分析这个代码库").join();
unsubscribe.run(); // 停止监听
```

事件层次结构：

```
AgentEvent
├── AgentStartEvent / AgentEndEvent
├── TurnStartEvent / TurnEndEvent
├── MessageStartEvent / MessageUpdateEvent / MessageEndEvent
└── ToolExecutionStartEvent / ToolExecutionUpdateEvent / ToolExecutionEndEvent
```

### 会话管理

跨重启持久化对话：

```java
SessionManager sessionManager = new SessionManager(Paths.get("sessions"));

// 创建并填充会话
Session session = sessionManager.create("user-123");
session.appendMessage(new LlmAgentMessage(new UserMessage(
    Collections.singletonList(new TextContent("你好"))
)));

// 加载已有会话
Session loaded = sessionManager.load("user-123");

// 列出所有会话
for (SessionInfo info : sessionManager.list()) {
    System.out.println(info.getSessionId());
}
```

## 架构

```
pi4j/
├── pi4j-ai          # 统一 LLM API 层
│   ├── types/        #   核心类型：Message、ContentBlock、Tool、Model
│   ├── stream/       #   支持事件重放的 EventStream
│   ├── provider/     #   13 个 LLM 提供商适配器
│   └── util/         #   校验、JSON 工具类
│
├── pi4j-agent        # Agent 运行时核心
│   ├── agent/        #   Agent 类、执行循环、配置选项
│   ├── tool/         #   工具接口、ToolSpec、调度器、中间件
│   ├── session/      #   JSONL 会话持久化
│   └── event/        #   Agent 事件层次
│
├── pi4j-tools        # 内置工具实现
│   └── (7 个工具)    #   read、write、edit、bash、grep、find、ls
│
└── pi4j-examples     # 使用示例
    └── (5 个示例)    #   基础、自定义工具、事件、内置工具、会话
```

### 模块依赖关系

```
pi4j-tools (可选) ──→ pi4j-agent ──→ pi4j-ai
                          ↑
                      你的应用
                   (自定义工具集)
```

### 设计原则

1. **核心最小化** — 只有三大支柱：Agent 循环 + LLM 接口 + 工具系统
2. **工具包而非框架** — 提供构建块，不强加工作流
3. **可组合性优先** — 所有工具可插拔、可替换、可扩展
4. **无魔法** — 无隐式行为，一切显式且可预测
5. **多租户** — 支持多个独立 Agent 会话并行运行

### Agent 执行循环

```
prompt("消息")
  │
  ▼
┌──────────────────────────────────────────┐
│  while (有工具调用 || 有待处理消息)       │
│    ├─ 转换消息 → LLM 格式               │
│    ├─ 调用 LLM API（流式 SSE）          │
│    ├─ 如果有工具调用 → 执行工具          │
│    └─ 检查 steering 队列（中断）         │
│                                          │
│  检查 follow-up 队列                     │
│    └─ 如果有消息 → 继续循环              │
└──────────────────────────────────────────┘
  │
  ▼
AgentEndEvent
```

- **Steering 队列**：在执行过程中中断当前循环
- **Follow-up 队列**：在当前循环结束后追加消息

### 支持的 LLM 提供商

| 提供商 | API 类型 | 模型 |
|--------|----------|------|
| Anthropic | `anthropic-messages` | Claude 3.5 Sonnet、Claude Opus 等 |
| OpenAI | `openai-completions` | GPT-4o、GPT-4 Turbo 等 |
| OpenAI | `openai-responses` | GPT-4o（Responses API） |
| Google | `google-gemini` | Gemini Pro、Gemini Ultra |
| Google | `google-vertex` | Vertex AI 模型 |
| Groq | `groq` | LLaMA、Mixtral |
| Mistral | `mistral` | Mistral Large 等 |
| xAI | `xai` | Grok |
| OpenRouter | `openrouter` | 聚合模型 |
| Ollama | `ollama` | 本地开源模型 |
| 百炼 | `bailian` | 阿里云模型 |
| 自定义 | `custom-openai` | 任何 OpenAI 兼容端点 |

### 工具系统

工具系统提供 3 种调度模式：

- **Serial（串行）** — 按顺序逐个执行工具
- **Parallel（并行）** — 同时执行所有工具
- **Custom（自定义）** — 用户自定义调度逻辑

中间件管线处理横切关注点：

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

### Java 8 设计模式

由于框架目标是 Java 8，多种现代模式被适配：

| 现代 Java / TypeScript | Pi4J Java 8 方案 |
|------------------------|------------------|
| 联合类型 `A \| B \| C` | 抽象类 + 子类 + Visitor 模式 |
| `async/await` | `CompletableFuture` + 回调 |
| `AsyncIterator` | 自定义 `EventStream<T, R>` + `Consumer` |
| Sealed interfaces | 抽象类 + 包级可见构造器 |
| Records | 不可变 POJO（final 字段 + getter） |

## 配置

### AgentOptions

| 选项 | 类型 | 说明 |
|------|------|------|
| `systemPrompt` | `String` | 系统提示词（必需） |
| `model` | `Model` | LLM 模型配置（必需） |
| `tools` | `List<AgentTool>` | 可用工具 |
| `temperature` | `Double` | 采样温度（0-1） |
| `maxTokens` | `Integer` | 最大输出 token 数 |
| `thinkingLevel` | `String` | 思考模式："off"、"basic"、"deep" |
| `thinkingBudget` | `Integer` | 思考 token 预算 |
| `responseFormat` | `JsonObject` | 响应格式（JSON mode 等） |
| `toolChoice` | `String` | 工具选择策略 |
| `steeringMode` | `String` | Steering 队列模式："all" / "one-at-a-time" |
| `followUpMode` | `String` | Follow-up 队列模式："all" / "one-at-a-time" |
| `getApiKey` | `ApiKeyResolver` | API 密钥解析函数 |
| `toolDispatcher` | `ToolDispatcher` | 自定义工具调度器 |
| `toolMiddlewares` | `List<ToolMiddleware>` | 工具中间件链 |
| `convertToLlm` | `MessageConverter` | 自定义消息转换器 |
| `transformContext` | `ContextTransformer` | 上下文转换器 |

## 依赖

Pi4J 保持最小化依赖：

| 依赖 | 版本 | 用途 |
|------|------|------|
| Gson | 2.11.0 | JSON 序列化 |
| OkHttp | 4.12.0 | HTTP 客户端 + SSE |
| SLF4J | 1.7.25 | 日志门面 |

## 构建

```bash
# 构建所有模块
./gradlew build

# 运行测试
./gradlew test

# 发布到 Maven Local
./gradlew publishToMavenLocal
```

## 示例

参见 [`pi4j-examples`](pi4j-examples/) 获取完整的可运行示例：

| 示例 | 说明 |
|------|------|
| [BasicAgentExample](pi4j-examples/src/main/java/com/pi4j/examples/BasicAgentExample.java) | 最小 Agent 配置与提问 |
| [CustomToolExample](pi4j-examples/src/main/java/com/pi4j/examples/CustomToolExample.java) | 定义和使用自定义工具 |
| [BuiltinToolsExample](pi4j-examples/src/main/java/com/pi4j/examples/BuiltinToolsExample.java) | 挂载内置开发者工具 |
| [EventListenerExample](pi4j-examples/src/main/java/com/pi4j/examples/EventListenerExample.java) | 订阅执行事件 |
| [SessionManagerExample](pi4j-examples/src/main/java/com/pi4j/examples/SessionManagerExample.java) | 会话持久化与加载 |

## 文档

- [English Documentation](docs/en/)
- [中文文档](docs/zh/)

## 许可证

[Apache License 2.0](LICENSE)
