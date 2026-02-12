# Pi4J - 极简主义 AI Agent 框架设计文档

> 基于 [pi-mono](https://github.com/badlogic/pi-mono) 的极简哲学，用 Java 8 重新实现的 AI Agent 框架。
> 可嵌入任何 Java 应用，提供轻量级、可组合的 AI Agent 能力。

---

## 一、设计哲学

### 1.1 从 Pi 继承的核心理念

- **核心最小化**：只保留 Agent 执行循环 + 统一 LLM 接口 + 工具系统三个核心
- **工具包而非框架**：提供构建块，不强加工作流
- **可组合性优先**：所有工具可插拔、可替换、可扩展
- **无魔法**：不做隐式的事情，行为可预测

### 1.2 设计目标

- **工具完全可配置**：内置工具（文件读写、bash 等）默认不注册，由使用方按需选择
- **自定义工具优先**：框架提供简洁的工具定义接口，自定义工具与内置工具地位平等
- **多租户**：支持多个独立的 Agent 会话并行运行
- **轻量级**：无重量级依赖，适合嵌入任何 Java 应用

### 1.3 Java 8 约束下的设计取舍

| 原版 (TypeScript)         | Java 8 替代方案                    |
|---------------------------|-----------------------------------|
| 联合类型 `A \| B \| C`     | 抽象类 + 子类 + Visitor 模式       |
| async/await               | CompletableFuture + 回调           |
| AsyncIterator (事件流)     | 自定义 EventStream + Consumer 回调  |
| TypeBox JSON Schema       | Gson JsonObject + 手写 Schema      |
| sealed interface          | 抽象类 + 包内可见构造器             |
| Record                    | 不可变 POJO (final 字段 + getter)  |
| 泛型字面量类型             | 枚举 + 字符串常量                  |

---

## 二、模块结构

```
pi4j/
├── pi4j-ai/              # 统一 LLM API 层
│   ├── types/             # 消息、工具、模型等核心类型
│   ├── stream/            # 流式事件架构
│   ├── provider/          # LLM 提供商适配器
│   │   ├── anthropic/
│   │   ├── openai/
│   │   ├── google/
│   │   ├── bedrock/
│   │   ├── mistral/
│   │   ├── groq/
│   │   └── ...
│   └── util/              # 验证、JSON 解析等工具
│
├── pi4j-agent/            # Agent 运行时核心
│   ├── agent/             # Agent 类 + 执行循环
│   ├── tool/              # 工具接口 + 注册表
│   ├── session/           # 会话管理
│   └── event/             # 事件类型 + 订阅机制
│
├── pi4j-tools/            # 内置工具集 (可选依赖)
│   ├── ReadTool
│   ├── WriteTool
│   ├── EditTool
│   ├── BashTool
│   ├── GrepTool
│   ├── FindTool
│   └── LsTool
│
└── pi4j-examples/         # 示例
```

### 模块依赖关系

```
pi4j-tools (可选) ──→ pi4j-agent ──→ pi4j-ai
                          ↑
                      你的应用
                   (自定义工具集)
```

**关键点**：`pi4j-tools` 是可选依赖。应用方只需依赖 `pi4j-agent`，然后注册自己的工具。

---

## 三、pi4j-ai 模块设计

### 3.1 核心类型

#### 3.1.1 消息体系

```
Message (抽象类)
├── UserMessage           # 用户消息
├── AssistantMessage      # 助手消息 (含工具调用)
└── ToolResultMessage     # 工具执行结果
```

```java
public abstract class Message {
    private final String role;  // "user" | "assistant" | "toolResult"
    private final long timestamp;

    // 子类通过构造器设置 role
}

public class UserMessage extends Message {
    private final List<ContentBlock> content;
    // content 可以是 TextContent 或 ImageContent
}

public class AssistantMessage extends Message {
    private final List<ContentBlock> content;
    // content 可以是 TextContent, ThinkingContent 或 ToolCallContent
    private final String api;        // "anthropic-messages" | "openai-completions" | ...
    private final String provider;   // "anthropic" | "openai" | ...
    private final String model;      // "claude-opus-4-6" | ...
    private final Usage usage;
    private final StopReason stopReason;
    private final String errorMessage; // 可空
}

public class ToolResultMessage extends Message {
    private final String toolCallId;
    private final String toolName;
    private final List<ContentBlock> content;
    private final Object details;    // 可空，用于 UI/日志
    private final boolean isError;
}
```

#### 3.1.2 内容块体系

```
ContentBlock (抽象类)
├── TextContent           # 纯文本
├── ImageContent          # Base64 图片
├── ThinkingContent       # 思维链内容
└── ToolCallContent       # 工具调用
```

```java
public abstract class ContentBlock {
    private final String type; // "text" | "image" | "thinking" | "toolCall"
}

public class TextContent extends ContentBlock {
    private final String text;
    private final String textSignature;   // 可空，Google 专用
}

public class ImageContent extends ContentBlock {
    private final String data;      // base64
    private final String mimeType;  // "image/jpeg" 等
}

public class ThinkingContent extends ContentBlock {
    private final String thinking;
    private final String thinkingSignature; // 可空，用于跨轮次保留
}

public class ToolCallContent extends ContentBlock {
    private final String id;
    private final String name;
    private final Map<String, Object> arguments;
    private final String thoughtSignature; // 可空，Google 专用
}
```

#### 3.1.3 辅助类型

```java
public class Usage {
    private final int input;
    private final int output;
    private final int cacheRead;
    private final int cacheWrite;
    private final int totalTokens;
    private final Cost cost;

    public static class Cost {
        private final double input;
        private final double output;
        private final double cacheRead;
        private final double cacheWrite;
        private final double total;
    }
}

public enum StopReason {
    STOP,       // 正常结束
    LENGTH,     // 达到最大 token
    TOOL_USE,   // 需要执行工具
    ERROR,      // 错误
    ABORTED     // 用户中止
}

public class Tool {
    private final String name;
    private final String description;
    private final JsonObject parameters;  // JSON Schema 格式
}

public class Context {
    private final String systemPrompt;
    private final List<Message> messages;
    private final List<Tool> tools;
}
```

#### 3.1.4 模型定义

```java
public class Model {
    private final String id;            // "claude-opus-4-6"
    private final String name;          // "Opus 4.6"
    private final String api;           // "anthropic-messages"
    private final String provider;      // "anthropic"
    private final String baseUrl;       // "https://api.anthropic.com"
    private final boolean reasoning;    // 是否支持推理模式
    private final List<String> input;   // ["text", "image"]
    private final ModelCost cost;
    private final int contextWindow;    // 200000
    private final int maxTokens;        // 16384
    private final Map<String, String> headers; // 自定义 HTTP 头

    public static class ModelCost {
        private final double input;       // $/百万 token
        private final double output;
        private final double cacheRead;
        private final double cacheWrite;
    }
}
```

### 3.2 流式事件架构

#### 3.2.1 事件类型

```
AssistantMessageEvent (抽象类)
├── StartEvent              # 流开始
├── TextStartEvent          # 文本块开始
├── TextDeltaEvent          # 文本增量
├── TextEndEvent            # 文本块结束
├── ThinkingStartEvent      # 思维链开始
├── ThinkingDeltaEvent      # 思维链增量
├── ThinkingEndEvent        # 思维链结束
├── ToolCallStartEvent      # 工具调用开始
├── ToolCallDeltaEvent      # 工具调用参数增量
├── ToolCallEndEvent        # 工具调用结束
├── DoneEvent               # 流正常结束
└── ErrorEvent              # 流错误结束
```

```java
public abstract class AssistantMessageEvent {
    private final String type;
}

public class TextDeltaEvent extends AssistantMessageEvent {
    private final int contentIndex;
    private final String delta;
    private final AssistantMessage partial;
}

public class ToolCallEndEvent extends AssistantMessageEvent {
    private final int contentIndex;
    private final ToolCallContent toolCall;
    private final AssistantMessage partial;
}

public class DoneEvent extends AssistantMessageEvent {
    private final StopReason reason;
    private final AssistantMessage message;
}

public class ErrorEvent extends AssistantMessageEvent {
    private final StopReason reason; // ERROR 或 ABORTED
    private final AssistantMessage error;
}
```

#### 3.2.2 EventStream 核心类

```java
/**
 * 线程安全的异步事件流。
 *
 * 生产者通过 push() 写入事件，消费者通过 subscribe() 订阅。
 * 支持双重消费模式：
 *   1. 事件流模式 - 实时接收每个事件
 *   2. 结果模式 - 等待最终结果 (CompletableFuture)
 */
public class EventStream<T, R> {
    private final Queue<T> queue;
    private final List<Consumer<T>> listeners;
    private final CompletableFuture<R> finalResult;
    private volatile boolean done;

    /** 推入事件，通知所有监听器 */
    public void push(T event);

    /** 标记流结束，设置最终结果 */
    public void end(R result);

    /** 标记流因错误结束 */
    public void error(Throwable cause);

    /** 订阅事件流，返回取消句柄 */
    public Runnable subscribe(Consumer<T> listener);

    /** 获取最终结果的 Future */
    public CompletableFuture<R> result();
}

/** 助手消息专用事件流 */
public class AssistantMessageEventStream
    extends EventStream<AssistantMessageEvent, AssistantMessage> {
}
```

### 3.3 Provider 体系

#### 3.3.1 Provider 接口

```java
/**
 * LLM 提供商的统一接口。
 * 每个提供商实现此接口，注册到 ApiRegistry 中。
 */
public interface ApiProvider {

    /** 此 Provider 处理的 API 类型标识 */
    String getApi();  // "anthropic-messages" | "openai-completions" | ...

    /**
     * 流式调用 LLM。
     * 返回的 EventStream 可以：
     *   1. subscribe() 实时接收事件
     *   2. result() 等待最终 AssistantMessage
     */
    AssistantMessageEventStream stream(
        Model model,
        Context context,
        StreamOptions options
    );
}
```

#### 3.3.2 StreamOptions

```java
public class StreamOptions {
    private final String apiKey;
    private final Double temperature;
    private final Integer maxTokens;
    private final String reasoning;        // ThinkingLevel: "off"|"minimal"|"low"|"medium"|"high"
    private final Integer thinkingBudget;  // 思维 token 预算
    private final String toolChoice;       // "auto"|"any"|"none"|具体工具名
    private final String cacheRetention;   // "short"|"long"
    private final String sessionId;        // 用于缓存
    private final Map<String, String> headers; // 额外 HTTP 头
    private final AbortHandle abortHandle; // 中止句柄

    // Builder 模式构造
    public static Builder builder() { return new Builder(); }
}
```

#### 3.3.3 ApiRegistry (Provider 注册表)

```java
/**
 * Provider 注册表 — 策略模式。
 * 根据 Model 的 api 字段路由到对应 Provider。
 */
public class ApiRegistry {
    private static final Map<String, ApiProvider> registry = new ConcurrentHashMap<>();

    /** 注册一个 Provider */
    public static void register(ApiProvider provider);

    /** 根据 api 类型获取 Provider */
    public static ApiProvider getProvider(String api);

    /** 流式调用的便捷入口 */
    public static AssistantMessageEventStream stream(
        Model model, Context context, StreamOptions options
    ) {
        ApiProvider provider = getProvider(model.getApi());
        return provider.stream(model, context, options);
    }
}
```

#### 3.3.4 要实现的 Provider 列表

| Provider 类                   | API 标识                    | 说明                        |
|------------------------------|----------------------------|-----------------------------|
| `AnthropicProvider`          | `anthropic-messages`       | Claude 系列                  |
| `OpenAICompletionsProvider`  | `openai-completions`       | GPT 系列 (Chat Completions) |
| `OpenAIResponsesProvider`    | `openai-responses`         | GPT 系列 (Responses API)    |
| `GoogleProvider`             | `google-generative-ai`     | Gemini 系列                  |
| `GoogleVertexProvider`       | `google-vertex`            | Vertex AI                    |
| `BedrockProvider`            | `bedrock-converse-stream`  | AWS Bedrock                  |
| `MistralProvider`            | `openai-completions`       | Mistral (OpenAI 兼容)       |
| `GroqProvider`               | `openai-completions`       | Groq (OpenAI 兼容)          |
| `XAIProvider`                | `openai-completions`       | xAI/Grok (OpenAI 兼容)      |
| `OpenRouterProvider`         | `openai-completions`       | OpenRouter (聚合)            |
| `OllamaProvider`             | `openai-completions`       | Ollama (本地)                |
| `CustomOpenAIProvider`       | `openai-completions`       | 任何 OpenAI 兼容 API         |

**注意**：大部分提供商（Mistral、Groq、xAI、OpenRouter、Ollama 等）复用 `OpenAICompletionsProvider`，
通过兼容性检测 (`ProviderCompat`) 处理各家差异。实际需要从零实现的只有 4 个核心 Provider：
- Anthropic (自有协议)
- OpenAI Completions (覆盖大量兼容商)
- Google Generative AI (自有协议)
- AWS Bedrock (自有协议)

#### 3.3.5 跨提供商消息转换

```java
/**
 * 处理跨 Provider 的消息兼容性。
 * 当同一对话中切换模型时（如 Claude → GPT），需要转换消息格式。
 */
public class MessageTransformer {

    /**
     * 转换消息列表以适配目标模型。
     *
     * 处理逻辑：
     * 1. 思维块：同模型保留签名，跨模型转为纯文本
     * 2. 工具调用 ID：适配目标提供商格式要求
     * 3. 孤儿工具调用：自动插入合成的空结果
     * 4. 错误/中止消息：跳过
     */
    public static List<Message> transform(
        List<Message> messages,
        Model targetModel
    );
}
```

### 3.4 工具验证

```java
/**
 * 基于 JSON Schema 验证工具调用参数。
 * 使用 Gson 手写轻量验证，不引入额外依赖。
 *
 * 验证内容：
 * 1. 必填字段 (required) 是否存在
 * 2. 字段类型是否匹配 (string/number/integer/boolean/array/object)
 * 3. 类型强制转换 (如字符串 "123" → 数字 123)
 */
public class ToolValidator {

    /**
     * 验证工具调用参数是否符合 Schema。
     *
     * @return 验证后的参数（可能经过类型强制转换）
     * @throws ToolValidationException 验证失败时
     */
    public static Map<String, Object> validate(
        Tool tool,
        ToolCallContent toolCall
    );
}
```

### 3.5 上下文溢出检测

```java
/**
 * 检测 LLM 返回的错误是否为上下文窗口溢出。
 * 各提供商的错误消息格式不同，通过正则模式匹配。
 */
public class OverflowDetector {

    // 预编译的错误模式（覆盖所有主流提供商）
    private static final List<Pattern> OVERFLOW_PATTERNS = Arrays.asList(
        Pattern.compile("prompt is too long", Pattern.CASE_INSENSITIVE),
        Pattern.compile("exceeds the context window", Pattern.CASE_INSENSITIVE),
        Pattern.compile("input token count.*exceeds the maximum", Pattern.CASE_INSENSITIVE),
        // ... 更多模式
    );

    public static boolean isContextOverflow(AssistantMessage message, Integer contextWindow);
}
```

---

## 四、pi4j-agent 模块设计

### 4.1 Agent 核心类

```java
/**
 * Agent — 有状态的 AI 代理执行引擎。
 *
 * 核心职责：
 * 1. 管理对话状态（消息历史、当前模型、工具集）
 * 2. 执行 LLM 调用 → 工具执行 → 结果回传 的循环
 * 3. 发出细粒度事件供外部监听
 * 4. 支持中断（steering）和后续消息（follow-up）
 *
 * 线程安全性：
 * - 同一时刻只允许一个 prompt 执行
 * - 事件监听器在调用线程上同步触发
 * - abort() 可从任意线程调用
 */
public class Agent {

    // ========== 构造 ==========

    public Agent(AgentOptions options);

    // ========== 状态访问 ==========

    public AgentState getState();
    public boolean isStreaming();

    // ========== 配置修改 ==========

    public void setSystemPrompt(String prompt);
    public void setModel(Model model);
    public void setThinkingLevel(String level);
    public void setTools(List<AgentTool> tools);
    public void replaceMessages(List<AgentMessage> messages);
    public void appendMessage(AgentMessage message);
    public void clearMessages();

    // ========== 核心执行 ==========

    /**
     * 发送用户消息并启动 Agent 循环。
     *
     * 循环流程：
     * 1. 将用户消息加入上下文
     * 2. 调用 LLM 获取响应
     * 3. 如果响应包含工具调用 → 执行工具 → 将结果加入上下文 → 回到 2
     * 4. 如果无工具调用 → 检查后续消息队列 → 有则回到 2，无则结束
     *
     * @return CompletableFuture 在 Agent 循环完成时 resolve
     */
    public CompletableFuture<Void> prompt(String text);
    public CompletableFuture<Void> prompt(String text, List<ImageContent> images);
    public CompletableFuture<Void> prompt(AgentMessage message);
    public CompletableFuture<Void> prompt(List<AgentMessage> messages);

    /**
     * 从当前上下文继续执行（用于重试或处理队列消息）。
     */
    public CompletableFuture<Void> continueExecution();

    /**
     * 中止当前执行。可从任意线程调用。
     */
    public void abort();

    /**
     * 等待当前执行完成。
     */
    public CompletableFuture<Void> waitForIdle();

    /**
     * 重置所有状态。
     */
    public void reset();

    // ========== 消息队列 ==========

    /**
     * 注入引导消息 — 中断当前工具执行，跳过剩余工具。
     * 用途：用户在 Agent 执行过程中发送新指令。
     */
    public void steer(AgentMessage message);

    /**
     * 注入后续消息 — Agent 停止后自动处理。
     * 用途：自动化流程中的链式任务。
     */
    public void followUp(AgentMessage message);

    // ========== 事件订阅 ==========

    /**
     * 订阅 Agent 事件。
     *
     * @return 取消订阅的 Runnable
     */
    public Runnable subscribe(AgentEventListener listener);
}
```

### 4.2 AgentOptions (构建选项)

```java
public class AgentOptions {

    // 初始状态
    private String systemPrompt;
    private Model model;
    private String thinkingLevel;           // "off"|"minimal"|"low"|"medium"|"high"
    private List<AgentTool> tools;
    private List<AgentMessage> initialMessages;

    /**
     * 消息转换函数：AgentMessage[] → Message[]
     *
     * 默认实现：过滤出 user/assistant/toolResult 消息。
     * 可自定义以支持自定义消息类型的转换。
     */
    private MessageConverter convertToLlm;

    /**
     * 上下文转换函数（可选）。
     * 在 convertToLlm 之前应用，用于修剪、注入上下文等。
     * 典型用途：上下文压缩。
     */
    private ContextTransformer transformContext;

    /**
     * API Key 动态解析器。
     * 支持短期令牌（如 OAuth token）。
     */
    private ApiKeyResolver getApiKey;

    /** 引导消息模式："all" 一次处理全部 | "one-at-a-time" 逐条处理 */
    private String steeringMode;   // 默认 "all"

    /** 后续消息模式 */
    private String followUpMode;   // 默认 "all"

    // Builder 模式
    public static Builder builder() { return new Builder(); }
}
```

### 4.3 函数式接口

```java
/** AgentMessage 列表 → LLM Message 列表 */
@FunctionalInterface
public interface MessageConverter {
    List<Message> convert(List<AgentMessage> messages);
}

/** 上下文预处理（压缩、裁剪等） */
@FunctionalInterface
public interface ContextTransformer {
    List<AgentMessage> transform(List<AgentMessage> messages, AbortHandle abortHandle);
}

/** 动态 API Key 解析 */
@FunctionalInterface
public interface ApiKeyResolver {
    String resolve(String provider);
}

/** Agent 事件监听器 */
@FunctionalInterface
public interface AgentEventListener {
    void onEvent(AgentEvent event);
}
```

### 4.4 AgentState (Agent 状态快照)

```java
public class AgentState {
    private final String systemPrompt;
    private final Model model;
    private final String thinkingLevel;
    private final List<AgentTool> tools;
    private final List<AgentMessage> messages;
    private final boolean isStreaming;
    private final AgentMessage streamMessage;  // 可空，当前流式消息
    private final Set<String> pendingToolCalls;
    private final String error;                // 可空

    // 所有字段 getter，无 setter（不可变快照）
}
```

### 4.5 AgentMessage (应用层消息)

```java
/**
 * Agent 层消息 — 是 LLM Message 的超集。
 *
 * 支持通过继承 Message 来创建自定义消息类型。
 * 自定义消息不会直接发给 LLM，而是通过 convertToLlm 转换或过滤。
 *
 * 例如可以定义：
 *   - SystemNotification（系统通知）
 *   - ExternalEvent（外部事件记录）
 *   这些消息可以在 convertToLlm 中转为 UserMessage 的文本。
 */
public abstract class AgentMessage {
    private final String role;
    private final long timestamp;
}

// 标准 LLM 消息也是 AgentMessage
// UserMessage extends AgentMessage
// AssistantMessage extends AgentMessage
// ToolResultMessage extends AgentMessage
```

### 4.6 AgentEvent 体系

```
AgentEvent (抽象类)
│
├── AgentStartEvent                 # Agent 循环开始
├── AgentEndEvent                   # Agent 循环结束，携带所有新消息
│
├── TurnStartEvent                  # 一个 Turn 开始 (Turn = 一次 LLM 调用 + 工具执行)
├── TurnEndEvent                    # Turn 结束，携带助手消息和工具结果
│
├── MessageStartEvent               # 消息开始（user/assistant/toolResult）
├── MessageUpdateEvent              # 流式更新（仅 assistant）
├── MessageEndEvent                 # 消息结束
│
├── ToolExecutionStartEvent         # 工具开始执行
├── ToolExecutionUpdateEvent        # 工具执行中间更新（进度）
└── ToolExecutionEndEvent           # 工具执行完成
```

```java
public abstract class AgentEvent {
    private final String type;
}

public class AgentStartEvent extends AgentEvent {
    // type = "agent_start"
}

public class AgentEndEvent extends AgentEvent {
    // type = "agent_end"
    private final List<AgentMessage> messages;
}

public class TurnEndEvent extends AgentEvent {
    // type = "turn_end"
    private final AssistantMessage message;
    private final List<ToolResultMessage> toolResults;
}

public class MessageUpdateEvent extends AgentEvent {
    // type = "message_update"
    private final AgentMessage message;
    private final AssistantMessageEvent assistantMessageEvent; // 原始流式事件
}

public class ToolExecutionStartEvent extends AgentEvent {
    // type = "tool_execution_start"
    private final String toolCallId;
    private final String toolName;
    private final Map<String, Object> args;
}

public class ToolExecutionEndEvent extends AgentEvent {
    // type = "tool_execution_end"
    private final String toolCallId;
    private final String toolName;
    private final AgentToolResult result;
    private final boolean isError;
}
```

### 4.7 AgentTool 接口

```java
/**
 * Agent 工具 — 框架中最重要的扩展点。
 *
 * 自定义工具只需实现此接口：
 *
 *   public class SearchTool implements AgentTool {
 *       public String getName() { return "search"; }
 *       public String getDescription() { return "搜索知识库"; }
 *       public JsonObject getParameters() { return schema; }  // Gson JsonObject
 *       public AgentToolResult execute(...) {
 *           // 调用业务 API
 *       }
 *   }
 */
public interface AgentTool {

    /** 工具唯一名称，LLM 调用时使用 */
    String getName();

    /** 工具描述，帮助 LLM 理解何时使用此工具 */
    String getDescription();

    /** 人类可读的标签，用于 UI 显示 */
    String getLabel();

    /**
     * 参数的 JSON Schema 定义。
     * LLM 会根据此 Schema 生成工具调用参数。
     *
     * 示例：
     * {
     *   "type": "object",
     *   "properties": {
     *     "x": { "type": "integer", "description": "X 坐标" },
     *     "y": { "type": "integer", "description": "Y 坐标" },
     *     "z": { "type": "integer", "description": "Z 坐标" }
     *   },
     *   "required": ["x", "y", "z"]
     * }
     */
    JsonObject getParameters();

    /**
     * 执行工具。
     *
     * @param toolCallId  工具调用唯一 ID
     * @param params      验证后的参数 (Map 形式)
     * @param abortHandle 中止句柄，工具应定期检查 isAborted()
     * @param onUpdate    进度回调，用于长耗时操作的中间反馈
     * @return 工具执行结果
     */
    AgentToolResult execute(
        String toolCallId,
        Map<String, Object> params,
        AbortHandle abortHandle,
        ToolUpdateCallback onUpdate
    );
}

/** 工具执行结果 */
public class AgentToolResult {
    private final List<ContentBlock> content;  // 返回给 LLM 的内容
    private final Object details;              // 额外信息（日志/UI）

    // 便捷构造方法
    public static AgentToolResult text(String text);
    public static AgentToolResult error(String errorMessage);
    public static AgentToolResult withImage(String text, String base64, String mimeType);
}

/** 工具进度回调 */
@FunctionalInterface
public interface ToolUpdateCallback {
    void onUpdate(AgentToolResult partialResult);
}
```

### 4.8 ToolRegistry (工具注册表)

```java
/**
 * 工具注册表 — 管理可用工具集。
 *
 * 典型用法：
 *
 *   ToolRegistry registry = new ToolRegistry();
 *   registry.register(new SearchTool(knowledgeBase));
 *   registry.register(new QueryTool(database));
 *   registry.register(new NotifyTool(notifier));
 *
 *   Agent agent = Agent.builder()
 *       .tools(registry.getAll())
 *       .build();
 */
public class ToolRegistry {
    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public void register(AgentTool tool);
    public void unregister(String toolName);
    public void replace(String toolName, AgentTool newTool);
    public AgentTool get(String name);
    public List<AgentTool> getAll();
    public boolean has(String name);
}
```

### 4.9 AbortHandle (中止机制)

```java
/**
 * 协作式中止机制。
 *
 * 替代 Java 的 Thread.interrupt()，更适合异步场景。
 * Agent、LLM 调用、工具执行共享同一个 AbortHandle。
 */
public class AbortHandle {
    private volatile boolean aborted = false;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public void abort();
    public boolean isAborted();
    public void throwIfAborted();  // 抛出 AbortException
    public void addListener(Runnable listener);
    public void removeListener(Runnable listener);
}
```

### 4.10 Agent 执行循环 (AgentLoop)

```java
/**
 * Agent 核心执行循环 — 内部实现，不直接暴露。
 *
 * 双层循环结构：
 *
 * 外层循环 (follow-up):
 *   while (有后续消息) {
 *     内层循环 (tool execution):
 *       while (有工具调用 || 有引导消息) {
 *         1. 处理待处理消息（注入上下文）
 *         2. 调用 LLM 获取助手响应
 *         3. 如果有工具调用 → 顺序执行工具
 *         4. 每个工具执行后检查引导消息
 *         5. 如果有引导消息 → 跳过剩余工具，注入引导消息
 *       }
 *     检查后续消息队列
 *   }
 *
 * 流程图：
 *
 *   prompt() ─→ [用户消息] ─→ LLM ─→ [助手响应]
 *                                         │
 *                              ┌─── 有工具调用? ───┐
 *                              │ 是                 │ 否
 *                              ↓                    ↓
 *                        执行工具               检查 follow-up
 *                              │                    │
 *                        ┌── 有引导? ──┐      ┌─ 有消息? ─┐
 *                        │ 是          │ 否    │ 是        │ 否
 *                        ↓             ↓      ↓           ↓
 *                   跳过剩余工具   工具结果   继续循环    结束
 *                   注入引导消息   回传 LLM
 *                        │             │
 *                        └──→ LLM ←────┘
 */
class AgentLoop {

    static EventStream<AgentEvent, List<AgentMessage>> run(
        List<AgentMessage> prompts,
        AgentLoopConfig config,
        AbortHandle abortHandle
    );

    static EventStream<AgentEvent, List<AgentMessage>> continueRun(
        AgentLoopConfig config,
        AbortHandle abortHandle
    );
}
```

### 4.11 Session 管理

```java
/**
 * 会话管理器 — 持久化对话历史。
 *
 * 使用 JSONL (JSON Lines) 格式，每行一个条目。
 * 支持树状分支结构（通过 id + parentId 构建）。
 *
 * 每个用户/租户一个会话文件：
 *   sessions/{user_id}.jsonl
 */
public class SessionManager {

    public SessionManager(Path sessionDir);

    /** 创建新会话 */
    public Session create(String sessionId);

    /** 加载已有会话 */
    public Session load(String sessionId);

    /** 列出所有会话 */
    public List<SessionInfo> list();

    /** 删除会话 */
    public void delete(String sessionId);
}

public class Session {
    /** 追加消息条目 */
    public void appendMessage(AgentMessage message);

    /** 获取当前分支的所有消息 */
    public List<AgentMessage> getMessages();

    /** 从指定条目创建分支 */
    public Session fork(String fromEntryId);

    /** 获取会话信息 */
    public SessionInfo getInfo();
}

/** JSONL 条目格式 */
public class SessionEntry {
    private final String type;      // "session"|"message"|"compaction"|...
    private final String id;        // UUID
    private final String parentId;  // 父条目 UUID
    private final long timestamp;
    private final Object data;      // 条目内容
}
```

---

## 五、pi4j-tools 模块设计（可选）

### 5.1 内置工具清单

所有工具独立实现，可单独注册：

| 工具          | 名称    | 参数                                 | 说明              |
|--------------|---------|--------------------------------------|-------------------|
| `ReadTool`   | `read`  | path, offset?, limit?                | 读取文件内容       |
| `WriteTool`  | `write` | path, content                        | 写入文件           |
| `EditTool`   | `edit`  | path, oldText, newText               | 查找替换编辑       |
| `BashTool`   | `bash`  | command, timeout?                    | 执行 shell 命令    |
| `GrepTool`   | `grep`  | pattern, path?, glob?, ignoreCase?   | 内容搜索           |
| `FindTool`   | `find`  | pattern, path?, limit?               | 文件查找           |
| `LsTool`     | `ls`    | path?, limit?                        | 目录列表           |

### 5.2 工具可配置性

```java
/**
 * 内置工具的工厂类。
 *
 * 所有工具都需要显式创建和注册，不会自动加入。
 * 每个工具支持通过 Operations 接口自定义底层操作。
 */
public class BuiltinTools {

    /** 创建文件读取工具 */
    public static ReadTool readTool(Path workDir);
    public static ReadTool readTool(Path workDir, ReadOperations ops);

    /** 创建 Bash 工具 */
    public static BashTool bashTool(Path workDir);
    public static BashTool bashTool(Path workDir, BashOperations ops);

    // ... 其他工具

    /** 创建全部内置工具 */
    public static List<AgentTool> all(Path workDir);

    /** 创建指定的内置工具子集 */
    public static List<AgentTool> select(Path workDir, String... names);
}

/** 可插拔的底层操作接口 */
public interface ReadOperations {
    byte[] readFile(Path absolutePath) throws IOException;
    boolean exists(Path absolutePath);
}

public interface BashOperations {
    BashResult exec(String command, Path cwd, int timeoutSeconds, AbortHandle abort);
}
```

### 5.3 截断机制

```java
/**
 * 输出截断工具。
 * 防止工具返回过大的内容撑爆 LLM 上下文。
 */
public class Truncator {

    public static final int DEFAULT_MAX_LINES = 2000;
    public static final int DEFAULT_MAX_BYTES = 50 * 1024; // 50KB

    /** 保留头部（用于文件读取） */
    public static TruncationResult truncateHead(String content, int maxLines, int maxBytes);

    /** 保留尾部（用于命令输出） */
    public static TruncationResult truncateTail(String content, int maxLines, int maxBytes);

    /** 单行截断 */
    public static String truncateLine(String line, int maxChars);
}

public class TruncationResult {
    private final String content;
    private final boolean truncated;
    private final String truncatedBy;     // "lines" | "bytes" | null
    private final int totalLines;
    private final int totalBytes;
    private final int outputLines;
    private final int outputBytes;
}
```

---

## 六、使用示例

### 6.1 自定义工具示例

```java
/**
 * 天气查询工具
 */
public class WeatherTool implements AgentTool {

    private final WeatherService weatherService;

    public WeatherTool(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @Override
    public String getName() { return "get_weather"; }

    @Override
    public String getLabel() { return "查询天气"; }

    @Override
    public String getDescription() {
        return "查询指定城市的当前天气信息，包括温度、湿度、天气状况。";
    }

    @Override
    public JsonObject getParameters() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();
        JsonObject cityProp = new JsonObject();
        cityProp.addProperty("type", "string");
        cityProp.addProperty("description", "城市名称");
        props.add("city", cityProp);
        schema.add("properties", props);

        JsonArray required = new JsonArray();
        required.add("city");
        schema.add("required", required);
        return schema;
    }

    @Override
    public AgentToolResult execute(
            String toolCallId,
            Map<String, Object> params,
            AbortHandle abortHandle,
            ToolUpdateCallback onUpdate) {

        String city = (String) params.get("city");
        WeatherInfo info = weatherService.query(city);

        return AgentToolResult.text(
            city + " 当前天气: " + info.getCondition() +
            ", 温度: " + info.getTemperature() + "°C" +
            ", 湿度: " + info.getHumidity() + "%"
        );
    }
}
```

### 6.2 Agent 初始化示例

```java
public class AgentManager {

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    public Agent createAgent(String userId) {
        // 1. 注册自定义工具
        ToolRegistry tools = new ToolRegistry();
        tools.register(new WeatherTool(weatherService));
        tools.register(new SearchTool(knowledgeBase));
        tools.register(new NotifyTool(notifier));

        // 2. 构建 Agent
        Agent agent = Agent.builder()
            .systemPrompt("你是一个智能助手，使用提供的工具帮助用户完成任务。")
            .model(Models.get("anthropic", "claude-sonnet-4-5-20250929"))
            .tools(tools.getAll())
            .build();

        // 3. 监听事件
        agent.subscribe(event -> {
            if (event instanceof ToolExecutionStartEvent) {
                ToolExecutionStartEvent e = (ToolExecutionStartEvent) event;
                System.out.println("正在执行: " + e.getToolName());
            } else if (event instanceof AgentEndEvent) {
                AgentEndEvent e = (AgentEndEvent) event;
                String reply = extractFinalReply(e.getMessages());
                System.out.println("回复: " + reply);
            }
        });

        agents.put(userId, agent);
        return agent;
    }

    public void onUserMessage(String userId, String message) {
        Agent agent = agents.get(userId);
        if (agent != null) {
            agent.prompt(message);
        }
    }
}
```

---

## 七、依赖选型

### 7.1 与宿主环境共享的依赖 (provided)

以下依赖在 Paper 等宿主环境中已存在，声明为 `provided` 避免重复打包：

| 依赖                | Paper 内置版本 | 用途                |
|--------------------|---------------|---------------------|
| `com.google.code.gson:gson` | 2.11.0 | JSON 序列化/反序列化 |
| `org.slf4j:slf4j-api`       | 1.7.25 | 日志门面            |
| `com.google.guava:guava`    | 21.0+  | 集合工具等          |

### 7.2 pi4j-ai 依赖

| 依赖                      | 版本     | 用途                                  |
|--------------------------|---------|---------------------------------------|
| `gson` (provided)         | 2.11.0  | JSON 处理，核心使用 `JsonObject` / `JsonElement` |
| `slf4j-api` (provided)    | 1.7.25  | 日志                                  |
| `okhttp3`                 | 4.12+   | HTTP 客户端 (SSE 流式支持)             |

**说明**：
- `gson` 和 `slf4j` 标记为 provided，在 Paper 环境中由服务器提供，独立使用时需自行引入
- `okhttp3` 是唯一的额外依赖，Paper 不内置 HTTP 客户端，且 Java 8 没有 `java.net.http.HttpClient`
- 原版 Pi 使用 TypeBox + AJV 做 JSON Schema 验证，Java 侧改为用 Gson 手写轻量验证，不引入额外验证库

### 7.3 pi4j-agent 依赖

| 依赖          | 版本   | 用途              |
|--------------|-------|-------------------|
| `pi4j-ai`    | -     | LLM 调用          |
| (无额外依赖)  |       | 保持轻量           |

### 7.4 pi4j-tools 依赖（可选）

| 依赖          | 版本   | 用途              |
|--------------|-------|-------------------|
| `pi4j-agent` | -     | Agent 工具接口     |
| (无额外依赖)  |       | 使用 JDK 自带 API  |

---

## 八、开发路线图

### Phase 1：pi4j-ai 核心类型 + 事件流

目标文件清单：
```
pi4j-ai/src/main/java/com/pi4j/ai/
├── types/
│   ├── Message.java
│   ├── UserMessage.java
│   ├── AssistantMessage.java
│   ├── ToolResultMessage.java
│   ├── ContentBlock.java
│   ├── TextContent.java
│   ├── ImageContent.java
│   ├── ThinkingContent.java
│   ├── ToolCallContent.java
│   ├── Usage.java
│   ├── StopReason.java
│   ├── Tool.java
│   ├── Context.java
│   └── Model.java
├── stream/
│   ├── EventStream.java
│   ├── AssistantMessageEvent.java (+ 所有子类)
│   └── AssistantMessageEventStream.java
└── util/
    ├── ToolValidator.java
    ├── OverflowDetector.java
    └── JsonUtil.java
```

### Phase 2：LLM Provider 实现

目标文件清单：
```
pi4j-ai/src/main/java/com/pi4j/ai/
├── provider/
│   ├── ApiProvider.java
│   ├── ApiRegistry.java
│   ├── StreamOptions.java
│   ├── AbortHandle.java
│   ├── MessageTransformer.java
│   ├── ProviderCompat.java           # OpenAI 兼容层差异配置
│   ├── anthropic/
│   │   └── AnthropicProvider.java
│   ├── openai/
│   │   ├── OpenAICompletionsProvider.java
│   │   └── OpenAIResponsesProvider.java
│   ├── google/
│   │   ├── GoogleProvider.java
│   │   └── GoogleShared.java
│   └── bedrock/
│       └── BedrockProvider.java
└── model/
    ├── ModelRegistry.java
    ├── ModelCost.java
    └── Models.java                   # 预定义模型常量
```

### Phase 3：Agent 核心

目标文件清单：
```
pi4j-agent/src/main/java/com/pi4j/agent/
├── Agent.java
├── AgentOptions.java
├── AgentState.java
├── AgentMessage.java
├── AgentLoop.java                    # 内部，包级可见
├── tool/
│   ├── AgentTool.java
│   ├── AgentToolResult.java
│   ├── ToolUpdateCallback.java
│   └── ToolRegistry.java
├── event/
│   ├── AgentEvent.java
│   ├── AgentEventListener.java
│   ├── AgentStartEvent.java
│   ├── AgentEndEvent.java
│   ├── TurnStartEvent.java
│   ├── TurnEndEvent.java
│   ├── MessageStartEvent.java
│   ├── MessageUpdateEvent.java
│   ├── MessageEndEvent.java
│   ├── ToolExecutionStartEvent.java
│   ├── ToolExecutionUpdateEvent.java
│   └── ToolExecutionEndEvent.java
├── session/
│   ├── SessionManager.java
│   ├── Session.java
│   ├── SessionEntry.java
│   └── SessionInfo.java
└── func/
    ├── MessageConverter.java
    ├── ContextTransformer.java
    └── ApiKeyResolver.java
```

### Phase 4：内置工具集（可选）

目标文件清单：
```
pi4j-tools/src/main/java/com/pi4j/tools/
├── BuiltinTools.java                 # 工厂类
├── Truncator.java
├── TruncationResult.java
├── PathUtils.java
├── read/
│   ├── ReadTool.java
│   └── ReadOperations.java
├── write/
│   ├── WriteTool.java
│   └── WriteOperations.java
├── edit/
│   ├── EditTool.java
│   └── EditOperations.java
├── bash/
│   ├── BashTool.java
│   └── BashOperations.java
├── grep/
│   └── GrepTool.java
├── find/
│   └── FindTool.java
└── ls/
    └── LsTool.java
```

---

## 九、关键设计决策记录

### D1: 为什么用 Java 8 而不是更高版本？

许多 Java 应用生态仍以 Java 8 作为最低兼容版本。
使用 Java 8 确保框架可以嵌入任何 Java 运行环境，最大化兼容性。

### D2: 为什么不用 Spring / Reactor / RxJava？

- **轻量级**：作为嵌入式库，不应引入大框架
- **兼容性**：避免与宿主应用的依赖冲突
- **简单性**：Agent 核心的异步需求用 CompletableFuture + EventStream 足够

### D3: 为什么工具不自动注册？

不同应用场景对工具的需求完全不同，内置的文件/bash 工具可能无意义甚至有安全风险。
工具必须显式注册，使用方完全控制 Agent 能访问的能力集。

### D4: 为什么用 JSONL 而不是数据库做会话持久化？

- **零依赖**：不需要额外的数据库
- **可移植**：一个文件就是一个会话，易于备份和迁移
- **可调试**：纯文本格式，人类可读
- **树状结构**：通过 id/parentId 支持分支，比关系型数据库更自然
- **多租户友好**：每个用户一个文件，简单直观

### D5: 工具执行为什么是顺序而非并行？

Pi 原版就是顺序执行。原因：
1. 工具可能有副作用和依赖关系
2. 引导消息需要在工具间检查
3. 顺序执行行为可预测
4. 简化实现和调试

### D6: 为什么不做上下文压缩 (Compaction)？

上下文压缩是 Pi 的 `coding-agent` 层功能，不在核心 Agent 中。
如果需要，可以通过 `ContextTransformer` 在应用层实现。
初版不做，后续按需添加。

### D7: EventStream vs CompletableFuture

两者共存：
- `EventStream`：细粒度实时事件（UI 展示、进度跟踪）
- `CompletableFuture`：最终结果（简单场景只关心结果）

使用方可以选择只用其中之一。

---

## 十、核心流程时序图

### 10.1 完整 Agent 执行流程

```
Caller                  Agent               AgentLoop            LLM Provider         Tool
  │                       │                     │                     │                  │
  │── prompt("查天气") ──→│                     │                     │                  │
  │                       │── runLoop() ───────→│                     │                  │
  │                       │                     │                     │                  │
  │                       │◄─ agent_start ──────│                     │                  │
  │                       │◄─ turn_start ───────│                     │                  │
  │                       │◄─ message_start(u) ─│                     │                  │
  │                       │◄─ message_end(u) ───│                     │                  │
  │                       │                     │                     │                  │
  │                       │                     │── stream() ────────→│                  │
  │                       │                     │◄─ text_delta ───────│                  │
  │                       │◄─ message_update ───│                     │                  │
  │                       │                     │◄─ toolcall_end ─────│                  │
  │                       │◄─ message_end(a) ───│                     │                  │
  │                       │                     │                     │                  │
  │                       │◄─ tool_exec_start ──│── execute() ───────────────────────────→│
  │                       │                     │◄─ result ───────────────────────────────│
  │                       │◄─ tool_exec_end ────│                     │                  │
  │                       │                     │                     │                  │
  │                       │                     │── stream() ────────→│   (带工具结果)    │
  │                       │                     │◄─ text_delta ───────│                  │
  │                       │◄─ message_update ───│                     │                  │
  │                       │                     │◄─ done ─────────────│                  │
  │                       │◄─ message_end(a) ───│                     │                  │
  │                       │◄─ turn_end ─────────│                     │                  │
  │                       │◄─ agent_end ────────│                     │                  │
  │                       │                     │                     │                  │
  │◄─ result ─────────────│                     │                     │                  │
```

### 10.2 引导消息 (Steering) 中断流程

```
Caller                  Agent               AgentLoop                    Tool
  │                       │                     │                          │
  │                       │                     │── execute(tool_1) ──────→│
  │── steer("停止!") ────→│                     │◄─ result ───────────────│
  │                       │                     │                          │
  │                       │                     │── check steering ────→ 有引导消息!
  │                       │                     │                          │
  │                       │                     │── skip(tool_2) 跳过     │
  │                       │                     │── skip(tool_3) 跳过     │
  │                       │                     │                          │
  │                       │                     │── stream(引导消息) ─→ LLM
  │                       │                     │◄─ "好的,我停下来了" ──────│
```
