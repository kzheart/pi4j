# 架构与设计

Pi4J 是一个极简主义 Java 8 AI Agent 框架，继承 [pi-mono](https://github.com/badlogic/pi-mono) 的设计哲学，提供轻量级、可组合的 AI Agent 能力。

## 概览

### 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                       你的应用                               │
│                   (自定义工具 + 业务逻辑)                      │
└────────────────────────┬────────────────────────────────────┘
                         │
          ┌──────────────┴──────────────┐
          ▼                             ▼
┌──────────────────┐          ┌──────────────────┐
│   pi4j-agent     │          │   pi4j-tools     │
│  Agent 运行时核心 │◄─────────│  内置工具 (可选)   │
│  · Agent 类      │          │  · ReadTool      │
│  · 执行循环      │          │  · WriteTool     │
│  · 工具接口      │          │  · EditTool      │
│  · 会话管理      │          │  · BashTool      │
│  · 事件体系      │          │  · GrepTool      │
└────────┬─────────┘          │  · FindTool      │
         │                    │  · LsTool        │
         ▼                    └──────────────────┘
┌──────────────────┐
│    pi4j-ai       │
│  统一 LLM API 层  │
│  · 核心类型      │
│  · EventStream   │
│  · 13 个 Provider │
│  · 校验/工具类   │
└──────────────────┘
```

### 模块依赖关系

```
pi4j-tools (可选) ──→ pi4j-agent ──→ pi4j-ai
                          ↑
                      你的应用
                   (自定义工具集)
```

核心依赖极简：

| 依赖 | 版本 | 用途 |
|------|------|------|
| Gson | 2.11.0 | JSON 序列化/反序列化 |
| OkHttp | 4.12.0 | HTTP 客户端 + SSE 流式 |
| SLF4J | 1.7.25 | 日志门面 |

## 设计哲学

### 1. 核心最小化

框架只保留三大支柱：**Agent 执行循环** + **统一 LLM 接口** + **工具系统**。不包含 RAG、向量数据库、Prompt 模板引擎等附加功能。这些可以通过自定义工具或上下文转换器在应用层实现。

### 2. 工具包而非框架

Pi4J 提供构建块，不强加工作流。没有强制的生命周期回调、没有必须遵循的基类继承链。你可以只使用 `pi4j-ai` 做 LLM 调用，也可以只使用 `AgentTool` 接口定义工具。

### 3. 可组合性优先

所有工具可插拔、可替换、可扩展。内置工具不会自动注册，必须显式选择。自定义工具与内置工具地位完全平等。中间件管线支持横切关注点的组合。

### 4. 无魔法

不使用反射、不使用注解处理器、不做隐式依赖注入。所有行为显式且可预测。构造参数通过 Builder 模式明确传入，工具通过 List 显式注册。

### 5. 多租户

支持多个独立 Agent 会话并行运行。每个 Agent 实例独立管理自己的状态、消息历史和工具集。`SessionManager` 通过文件系统隔离实现多租户会话存储。

## 模块结构

### pi4j-ai — 统一 LLM API 层

| 包 | 职责 | 关键类 |
|---|------|--------|
| `types` | 核心类型定义 | `Message`, `ContentBlock`, `Tool`, `Model`, `Context` |
| `stream` | 流式事件架构 | `EventStream`, `AssistantMessageEvent` 及其子类 |
| `provider` | LLM 提供商适配 | `ApiProvider`, `ApiRegistry`, `MessageTransformer` |
| `provider.anthropic` | Anthropic 适配 | `AnthropicProvider` |
| `provider.openai` | OpenAI 适配 | `OpenAICompletionsProvider`, `OpenAIResponsesProvider` |
| `provider.google` | Google 适配 | `GoogleProvider`, `GoogleVertexProvider` |
| `util` | 工具类 | `ToolValidator`, `OverflowDetector`, `JsonUtil` |
| `constant` | 常量 | `ApiTypes`, `ThinkingLevel` |
| `model` | 模型注册 | `ModelCost`, `ModelRegistry` |

### pi4j-agent — Agent 运行时核心

| 包 | 职责 | 关键类 |
|---|------|--------|
| 根包 | Agent 核心 | `Agent`, `AgentOptions`, `AgentState`, `AgentLoop` |
| `tool` | 工具系统 | `AgentTool`, `ToolSpec`, `ToolRegistry`, `ToolMiddleware` |
| `session` | 会话持久化 | `SessionManager`, `Session`, `SessionEntry` |
| `event` | 事件体系 | `AgentEvent` 及其所有子类 |
| `func` | 函数式接口 | `MessageConverter`, `ContextTransformer`, `ApiKeyResolver` |

### pi4j-tools — 内置工具 (可选)

提供 7 个开发者工具的实现，每个工具独立，通过 `BuiltinTools` 工厂类按需创建。详见 [工具系统指南](tool-system.md#内置工具)。

### pi4j-examples — 示例

包含 5 个可运行示例：BasicAgent、CustomTool、BuiltinTools、EventListener、SessionManager。

## Agent 执行循环

Agent 的核心是一个**双层循环**结构，处理 LLM 调用、工具执行、steering 中断和 follow-up 追问。

### 双层循环详解

**内层循环（工具循环）**：当 LLM 返回工具调用时，执行工具并将结果回传 LLM，直到 LLM 不再请求工具。

**外层循环（follow-up 循环）**：当内层循环结束后，检查 follow-up 队列，如有消息则开启新一轮内层循环。

### ASCII 流程图

```
prompt("消息")
  │
  ▼
┌──────────────────────────────────────────────┐
│  外层循环 (follow-up)                         │
│  ┌────────────────────────────────────────┐  │
│  │ 内层循环 (tool execution)               │  │
│  │  1. 转换消息 → LLM 格式                │  │
│  │  2. 调用 LLM API（流式 SSE）           │  │
│  │  3. 有工具调用？                        │  │
│  │     ├─ 是 → 执行工具                   │  │
│  │     │       ├─ 检查 steering 队列      │  │
│  │     │       │   ├─ 有 → 跳过剩余工具   │  │
│  │     │       │   └─ 无 → 继续下个工具   │  │
│  │     │       └─ 工具结果回传 → 回到 2   │  │
│  │     └─ 否 → 退出内层循环               │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  检查 follow-up 队列                         │
│    ├─ 有消息 → 注入上下文 → 继续外层循环     │
│    └─ 无消息 → 退出                          │
└──────────────────────────────────────────────┘
  │
  ▼
AgentEndEvent（携带所有新消息）
```

### 时序图

```
用户              Agent              LLM              工具
 │                 │                  │                 │
 │ prompt("分析")  │                  │                 │
 │────────────────►│                  │                 │
 │                 │  AgentStartEvent │                 │
 │                 │                  │                 │
 │                 │  stream(context) │                 │
 │                 │─────────────────►│                 │
 │                 │  TextDelta...    │                 │
 │                 │◄─────────────────│                 │
 │                 │  ToolCallEnd     │                 │
 │                 │◄─────────────────│                 │
 │                 │                  │                 │
 │                 │  execute("read") │                 │
 │                 │─────────────────────────────────►│
 │                 │  AgentToolResult │                 │
 │                 │◄─────────────────────────────────│
 │                 │                  │                 │
 │                 │  stream(+result) │                 │
 │                 │─────────────────►│                 │
 │                 │  TextDelta...    │                 │
 │                 │  DoneEvent       │                 │
 │                 │◄─────────────────│                 │
 │                 │                  │                 │
 │                 │  AgentEndEvent   │                 │
 │◄────────────────│                  │                 │
```

### Steering 与 Follow-up

- **Steering 队列**：在 Agent 执行过程中注入新指令，中断当前工具执行，跳过剩余工具调用
- **Follow-up 队列**：在当前循环结束后追加消息，用于自动化链式任务

```java
// Steering：中断当前执行
agent.steer(new LlmAgentMessage(
    new UserMessage(Collections.singletonList(new TextContent("停止，改为执行其他任务")))
));

// Follow-up：循环结束后自动追问
agent.followUp(new LlmAgentMessage(
    new UserMessage(Collections.singletonList(new TextContent("请总结你刚才做了什么")))
));
```

## 流式事件架构

### EventStream 设计

`EventStream<T, R>` 是核心的异步事件流抽象，提供双重消费模式：

1. **事件流模式** — 通过 `subscribe()` 实时接收每个事件
2. **结果模式** — 通过 `result()` 获取 `CompletableFuture<R>` 等待最终结果

```java
public class EventStream<T, R> {
    public void push(T event);              // 推入事件
    public void end(R result);              // 标记流结束
    public void error(Throwable cause);     // 标记错误结束
    public Runnable subscribe(Consumer<T>); // 订阅（返回取消句柄）
    public CompletableFuture<R> result();   // 获取最终结果
}
```

### 后订阅重放原理

`EventStream` 支持迟到订阅者。所有推入的事件保存在内部队列中，新订阅者在订阅时会先收到队列中已有的事件重放，然后实时接收后续事件。通过 `synchronized(lock)` 保证重放与实时推送的原子性，避免事件丢失或乱序。

### 线程安全

- 内部使用 `ConcurrentLinkedQueue` 存储事件
- 订阅者列表使用 `CopyOnWriteArrayList`
- `push()` 与 `subscribe()` 通过 `synchronized` 块协调
- `done` 标志使用 `volatile` 保证可见性

### 事件类型层次

**LLM 层事件（pi4j-ai）**：

```
AssistantMessageEvent
├── StartEvent              // 流开始
├── TextStartEvent          // 文本块开始
├── TextDeltaEvent          // 文本增量（携带 delta 字符串）
├── TextEndEvent            // 文本块结束
├── ThinkingStartEvent      // 思维链开始
├── ThinkingDeltaEvent      // 思维链增量
├── ThinkingEndEvent        // 思维链结束
├── ToolCallStartEvent      // 工具调用开始（携带工具名）
├── ToolCallDeltaEvent      // 工具调用参数增量
├── ToolCallEndEvent        // 工具调用结束（携带完整参数）
├── DoneEvent               // 流正常结束（携带 StopReason + AssistantMessage）
└── ErrorEvent              // 流错误结束
```

**Agent 层事件（pi4j-agent）**：

```
AgentEvent
├── AgentStartEvent                 // Agent 循环开始
├── AgentEndEvent                   // Agent 循环结束，携带所有新消息
├── TurnStartEvent / TurnEndEvent   // 单次 LLM 调用 + 工具执行
├── MessageStartEvent               // 消息开始
├── MessageUpdateEvent              // 流式更新（包装 AssistantMessageEvent）
├── MessageEndEvent                 // 消息结束
├── ToolExecutionStartEvent         // 工具开始执行
├── ToolExecutionUpdateEvent        // 工具执行中间进度
└── ToolExecutionEndEvent           // 工具执行完成
```

## 消息与内容块体系

### Message 继承体系

```
AgentMessage (抽象，应用层)
└── LlmAgentMessage (包装 LLM Message)

Message (抽象，LLM 层)
├── UserMessage           // 用户消息 → List<ContentBlock>
├── AssistantMessage      // 助手消息 → List<ContentBlock> + Usage + StopReason
└── ToolResultMessage     // 工具结果 → toolCallId + toolName + List<ContentBlock>
```

### ContentBlock 体系

```
ContentBlock (抽象)
├── TextContent           // 纯文本（含可选 textSignature）
├── ImageContent          // Base64 图片（data + mimeType）
├── ThinkingContent       // 思维链（thinking + thinkingSignature）
└── ToolCallContent       // 工具调用（id + name + arguments Map）
```

### 跨 Provider 消息转换

`MessageTransformer.transform()` 处理以下兼容性问题：

| 问题 | 处理方式 |
|------|---------|
| 思维块跨模型 | 同模型保留签名，跨模型转为纯文本 |
| 工具调用 ID 格式 | Anthropic 限 `[a-zA-Z0-9_-]`，Mistral 限 9 字符 |
| 孤儿工具调用 | 自动插入合成的空 ToolResultMessage |
| 错误/中止消息 | 跳过不传给目标模型 |

## Provider 体系

### ApiProvider 接口

每个 LLM 提供商实现统一的 `ApiProvider` 接口：

```java
public interface ApiProvider {
    String getApi();  // API 类型标识
    AssistantMessageEventStream stream(Model model, Context context, StreamOptions options);
}
```

### ApiRegistry 路由

`ApiRegistry` 使用策略模式，根据 `Model.api` 和 `Model.provider` 字段路由到对应 Provider：

```java
// 注册
ApiRegistry.register(new AnthropicProvider());
ApiRegistry.register("openai-completions", "groq", new GroqProvider());

// 调用（自动路由）
AssistantMessageEventStream stream = ApiRegistry.stream(model, context, options);
```

### ProviderCompat 兼容层

大量提供商复用 `OpenAICompletionsProvider`，通过 `ProviderCompat` 检测各家差异：

- `maxTokensField`：Mistral/百炼使用 `max_tokens`，其他使用 `max_completion_tokens`
- `responseFormatLevel`：DeepSeek 仅支持 `json_object`，Ollama 不支持
- `requiresMistralToolIds`：Mistral 工具调用 ID 限 9 字符
- `thinkingFormat`：不同提供商的思维链格式差异

### SSE 流式解析

所有 Provider 使用 OkHttp 发起 HTTP 请求，通过 SSE（Server-Sent Events）协议接收流式响应。Provider 将 SSE 事件逐个转换为 `AssistantMessageEvent` 并推入 `EventStream`。

## Java 8 设计模式

Pi4J 在 Java 8 约束下采用以下替代方案：

| 现代 Java / TypeScript | Pi4J Java 8 方案 | 应用场景 |
|------------------------|------------------|---------|
| 联合类型 `A \| B \| C` | 抽象类 + 子类 + `instanceof` 检查 | `ContentBlock`, `Message`, `AgentEvent` |
| `async/await` | `CompletableFuture` + 回调 | `Agent.prompt()` 返回 `CompletableFuture<Void>` |
| `AsyncIterator` | `EventStream<T, R>` + `Consumer` 回调 | LLM 流式响应、Agent 事件流 |
| TypeBox JSON Schema | `Gson JsonObject` + `ToolSpec` Builder | 工具参数定义 |
| sealed interface | 抽象类 + 包级可见构造器 | 事件类型、内容块类型 |
| Record | 不可变 POJO（final 字段 + getter） | `Model`, `Usage`, `AgentState` |
| 泛型字面量类型 | 枚举 + 字符串常量 | `StopReason`, `ToolDispatchMode`, `ApiTypes` |
| `@FunctionalInterface` Lambda | 显式函数式接口 | `ToolHandler`, `ApiKeyResolver`, `AgentEventListener` |

## 关键设计决策

### D1：工具完全可配置

内置工具默认不注册。应用方通过 `BuiltinTools.select()` 显式选择需要的工具，或注册自己的自定义工具。这避免了不必要的能力暴露和安全风险。

### D2：EventStream 后订阅重放

所有事件保留在队列中，新订阅者可以收到历史事件重放。这使得 UI 组件可以在 Agent 已经开始执行后再订阅，而不会丢失之前的事件。

### D3：协作式中止

使用 `AbortHandle` 而非 `Thread.interrupt()`。Agent、LLM 调用、工具执行共享同一个 AbortHandle，通过 `isAborted()` 轮询实现协作式中止，更适合异步场景。

### D4：JSONL 会话存储

使用 JSON Lines 格式而非关系型数据库存储会话。每行一个条目（`SessionEntry`），支持追加写入、流式读取，适合嵌入式场景。

### D5：Provider 复用策略

只实现 4 个核心 Provider（Anthropic、OpenAI Completions、OpenAI Responses、Google），其他提供商通过继承和 `ProviderCompat` 兼容层处理差异，避免重复代码。

### D6：Gson 手写 Schema 校验

不引入额外的 JSON Schema 验证库，而是用 Gson 的 `JsonObject` 手写轻量校验（`ToolValidator`），支持类型强制转换。保持零额外依赖的设计目标。

### D7：双层循环 + 消息队列

Agent 执行循环设计为双层结构，通过 steering 和 follow-up 两个消息队列实现执行中断和链式追问，兼顾交互性和自动化场景。

---

相关文档：
- [快速开始](getting-started.md) — 5 分钟上手
- [工具系统指南](tool-system.md) — 工具定义、调度、中间件
- [LLM 提供商指南](providers.md) — 13 个提供商配置
