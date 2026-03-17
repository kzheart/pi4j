# LLM 提供商指南

## 概览

Pi4J 的统一 API 层（`pi4j-ai`）将 13 个 LLM 提供商抽象为统一的 `ApiProvider` 接口。无论使用哪个提供商，你的 Agent 代码只需切换 `Model` 配置即可，无需修改业务逻辑。

设计目标：

- **一次编写，多处运行** — 同一个 Agent 可以无缝切换不同 LLM
- **流式优先** — 所有 Provider 统一返回 `AssistantMessageEventStream`
- **兼容性自动处理** — 跨提供商的消息格式差异由框架内部处理
- **按需注册** — 只注册实际使用的 Provider，避免不必要的初始化

## 支持的提供商

| 提供商 | API 类型 | 说明 | 实现类 |
|--------|---------|------|--------|
| Anthropic | `anthropic-messages` | Claude 系列（自有协议） | `AnthropicProvider` |
| OpenAI Completions | `openai-completions` | GPT 系列（Chat Completions API） | `OpenAICompletionsProvider` |
| OpenAI Responses | `openai-responses` | GPT 系列（Responses API） | `OpenAIResponsesProvider` |
| Google Gemini | `google-generative-ai` | Gemini 系列（Google AI Studio） | `GoogleProvider` |
| Google Vertex AI | `google-vertex` | Vertex AI 模型 | `GoogleVertexProvider` |
| Groq | `openai-completions` | LLaMA、Mixtral 等（OpenAI 兼容） | `GroqProvider` |
| Mistral | `openai-completions` | Mistral 系列（OpenAI 兼容，有差异） | `MistralProvider` |
| xAI | `openai-completions` | Grok 系列（OpenAI 兼容） | `XAIProvider` |
| OpenRouter | `openai-completions` | 聚合路由（OpenAI 兼容） | `OpenRouterProvider` |
| Ollama | `openai-completions` | 本地开源模型（OpenAI 兼容） | `OllamaProvider` |
| 百炼 | `openai-completions` | 阿里云百炼模型（OpenAI 兼容，有差异） | `BailianProvider` |
| 自定义 OpenAI | `openai-completions` | 任何 OpenAI 兼容端点 | `CustomOpenAIProvider` |

> 实际从零实现的核心 Provider 只有 4 个：Anthropic、OpenAI Completions、OpenAI Responses、Google。其他提供商均复用 `OpenAICompletionsProvider`，通过 `ProviderCompat` 兼容层处理各家差异。

## 配置

### 模型定义

每个 LLM 调用都需要一个 `Model` 实例，定义了模型 ID、API 类型、Provider 标识等信息：

```java
import com.pi4j.ai.types.Model;

Model model = new Model(
    "claude-sonnet-4-20250514",          // id: 模型标识符
    "Claude Sonnet 4",                   // name: 显示名称
    "anthropic-messages",                // api: API 类型（决定路由到哪个 Provider）
    "anthropic",                         // provider: 提供商标识
    "https://api.anthropic.com",         // baseUrl: API 基础 URL
    false,                               // reasoning: 是否支持推理/思考模式
    Arrays.asList("text", "image"),      // input: 支持的输入类型
    new Model.ModelCost(3.0, 15.0, 0.3, 3.75), // cost: 费用（$/百万 token）
    200000,                              // contextWindow: 上下文窗口大小
    8192,                                // maxTokens: 最大输出 token 数
    Collections.<String, String>emptyMap() // headers: 自定义请求头
);
```

`Model` 字段说明：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `String` | 模型 ID，发送给 API 的标识符 |
| `name` | `String` | 人类可读的显示名称 |
| `api` | `String` | API 类型标识，用于路由到对应 Provider |
| `provider` | `String` | 提供商标识，用于精确匹配 Provider 实例 |
| `baseUrl` | `String` | API 基础 URL |
| `reasoning` | `boolean` | 是否支持推理/思考模式 |
| `input` | `List<String>` | 支持的输入类型（`"text"`, `"image"`） |
| `cost` | `ModelCost` | 费用信息（input/output/cacheRead/cacheWrite，单位 $/百万 token） |
| `contextWindow` | `int` | 上下文窗口大小（token 数） |
| `maxTokens` | `int` | 最大输出 token 数 |
| `headers` | `Map<String, String>` | 额外的 HTTP 请求头 |

### API 密钥解析

通过 `ApiKeyResolver` 函数式接口动态解析 API 密钥：

```java
// 从环境变量读取
AgentOptions.builder()
    .getApiKey(provider -> System.getenv("ANTHROPIC_API_KEY"))
    .build();

// 根据 Provider 动态分发
AgentOptions.builder()
    .getApiKey(provider -> {
        switch (provider) {
            case "anthropic": return System.getenv("ANTHROPIC_API_KEY");
            case "openai":    return System.getenv("OPENAI_API_KEY");
            case "groq":      return System.getenv("GROQ_API_KEY");
            default:          return System.getenv("LLM_API_KEY");
        }
    })
    .build();

// 从配置中心或 Vault 动态获取
AgentOptions.builder()
    .getApiKey(provider -> configService.getSecret("llm." + provider + ".apiKey"))
    .build();
```

### 自定义请求头

在 `Model` 中设置额外的 HTTP 请求头：

```java
Map<String, String> headers = new LinkedHashMap<>();
headers.put("anthropic-beta", "prompt-caching-2024-07-31");
headers.put("X-Custom-Header", "my-value");

Model model = new Model(
    "claude-sonnet-4-20250514", "Claude Sonnet 4",
    "anthropic-messages", "anthropic",
    "https://api.anthropic.com",
    false, Arrays.asList("text"), null, 200000, 8192,
    headers
);
```

## 提供商详解

### Anthropic

使用 Anthropic 自有的 Messages API 协议。

```java
Model claudeSonnet = new Model(
    "claude-sonnet-4-20250514",
    "Claude Sonnet 4",
    "anthropic-messages",            // API 类型
    "anthropic",                     // Provider
    "https://api.anthropic.com",
    false,
    Arrays.asList("text", "image"),
    new Model.ModelCost(3.0, 15.0, 0.3, 3.75),
    200000, 8192,
    Collections.<String, String>emptyMap()
);

Agent agent = new Agent(AgentOptions.builder()
    .systemPrompt("你是一个助手")
    .model(claudeSonnet)
    .getApiKey(p -> System.getenv("ANTHROPIC_API_KEY"))
    .build());
```

支持特性：思考模式（reasoning）、Prompt Caching、图片输入、工具调用。

### OpenAI Completions

使用 OpenAI Chat Completions API。

```java
Model gpt4o = new Model(
    "gpt-4o",
    "GPT-4o",
    "openai-completions",            // API 类型
    "openai",                        // Provider
    "https://api.openai.com",
    false,
    Arrays.asList("text", "image"),
    new Model.ModelCost(2.5, 10.0, 0.0, 0.0),
    128000, 16384,
    Collections.<String, String>emptyMap()
);

Agent agent = new Agent(AgentOptions.builder()
    .systemPrompt("你是一个助手")
    .model(gpt4o)
    .getApiKey(p -> System.getenv("OPENAI_API_KEY"))
    .build());
```

### OpenAI Responses

使用 OpenAI 新版 Responses API。

```java
Model gpt4oResponses = new Model(
    "gpt-4o",
    "GPT-4o (Responses)",
    "openai-responses",              // API 类型
    "openai",                        // Provider
    "https://api.openai.com",
    false,
    Arrays.asList("text", "image"),
    new Model.ModelCost(2.5, 10.0, 0.0, 0.0),
    128000, 16384,
    Collections.<String, String>emptyMap()
);
```

### Google Gemini

使用 Google Generative AI API（通过 Google AI Studio）。

```java
Model gemini = new Model(
    "gemini-2.0-flash",
    "Gemini 2.0 Flash",
    "google-generative-ai",          // API 类型
    "google",                        // Provider
    "https://generativelanguage.googleapis.com",
    false,
    Arrays.asList("text", "image"),
    null, 1048576, 8192,
    Collections.<String, String>emptyMap()
);

Agent agent = new Agent(AgentOptions.builder()
    .systemPrompt("你是一个助手")
    .model(gemini)
    .getApiKey(p -> System.getenv("GOOGLE_API_KEY"))
    .build());
```

### Google Vertex AI

使用 Google Vertex AI API。

```java
Model vertexGemini = new Model(
    "gemini-2.0-flash",
    "Gemini 2.0 Flash (Vertex)",
    "google-vertex",                 // API 类型
    "google-vertex",                 // Provider
    "https://us-central1-aiplatform.googleapis.com",
    false,
    Arrays.asList("text", "image"),
    null, 1048576, 8192,
    Collections.<String, String>emptyMap()
);
```

### OpenAI 兼容提供商

Groq、Mistral、xAI、OpenRouter、Ollama 均复用 `openai-completions` API 类型，通过 `provider` 字段区分。`ProviderCompat` 自动处理各家差异。

**Groq**：

```java
Model groqLlama = new Model(
    "llama-3.3-70b-versatile",
    "LLaMA 3.3 70B",
    "openai-completions", "groq",
    "https://api.groq.com/openai",
    false, Arrays.asList("text"),
    null, 128000, 8192,
    Collections.<String, String>emptyMap()
);
```

**Mistral**：

```java
Model mistralLarge = new Model(
    "mistral-large-latest",
    "Mistral Large",
    "openai-completions", "mistral",
    "https://api.mistral.ai",
    false, Arrays.asList("text"),
    null, 128000, 8192,
    Collections.<String, String>emptyMap()
);
```

> Mistral 差异：使用 `max_tokens` 字段、工具调用 ID 限 9 字符、工具结果需要 `name` 字段、思维链转为纯文本。

**xAI（Grok）**：

```java
Model grok = new Model(
    "grok-3",
    "Grok 3",
    "openai-completions", "xai",
    "https://api.x.ai",
    false, Arrays.asList("text"),
    null, 131072, 8192,
    Collections.<String, String>emptyMap()
);
```

**OpenRouter**：

```java
Model openRouter = new Model(
    "anthropic/claude-sonnet-4-20250514",
    "Claude Sonnet 4 (OpenRouter)",
    "openai-completions", "openrouter",
    "https://openrouter.ai/api",
    false, Arrays.asList("text"),
    null, 200000, 8192,
    Collections.<String, String>emptyMap()
);
```

**Ollama**（本地部署）：

```java
Model ollama = new Model(
    "llama3.2",
    "LLaMA 3.2 (Local)",
    "openai-completions", "ollama",
    "http://localhost:11434",
    false, Arrays.asList("text"),
    null, 8192, 2048,
    Collections.<String, String>emptyMap()
);

// Ollama 无需 API Key
AgentOptions.builder()
    .getApiKey(p -> "ollama")
    .build();
```

### 百炼

阿里云百炼模型，基于 OpenAI 兼容协议，有特定差异处理：

```java
Model bailian = new Model(
    "qwen-plus",
    "通义千问 Plus",
    "openai-completions", "bailian",
    "https://dashscope.aliyuncs.com/compatible-mode",
    false, Arrays.asList("text"),
    null, 128000, 8192,
    Collections.<String, String>emptyMap()
);

Agent agent = new Agent(AgentOptions.builder()
    .systemPrompt("你是一个助手")
    .model(bailian)
    .getApiKey(p -> System.getenv("DASHSCOPE_API_KEY"))
    .build());
```

> 百炼差异：使用 `max_tokens` 字段、思维链格式为 `"bailian"`。

### 自定义 OpenAI（任意兼容端点）

连接任何 OpenAI 兼容的 API 端点：

```java
Model custom = new Model(
    "my-model",
    "My Custom Model",
    "openai-completions", "custom-openai",
    "https://my-api.example.com/v1",
    false, Arrays.asList("text"),
    null, 32000, 4096,
    Collections.<String, String>emptyMap()
);
```

## 添加自定义提供商

### 实现 ApiProvider 接口

```java
import com.pi4j.ai.provider.ApiProvider;
import com.pi4j.ai.provider.StreamOptions;
import com.pi4j.ai.stream.AssistantMessageEventStream;
import com.pi4j.ai.types.Context;
import com.pi4j.ai.types.Model;

public class MyCustomProvider implements ApiProvider {

    @Override
    public String getApi() {
        return "my-custom-api";  // 自定义 API 类型标识
    }

    @Override
    public AssistantMessageEventStream stream(
            Model model, Context context, StreamOptions options) {

        AssistantMessageEventStream stream = new AssistantMessageEventStream();

        // 在后台线程中处理 API 调用
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 构建请求
                // 2. 发起 HTTP 调用
                // 3. 解析 SSE 响应，逐个推入事件
                stream.push(new StartEvent(...));
                stream.push(new TextDeltaEvent(...));
                // ...
                stream.push(new DoneEvent(stopReason, finalMessage));
                stream.end(finalMessage);
            } catch (Exception e) {
                stream.error(e);
            }
        });

        return stream;
    }
}
```

### 注册到 ApiRegistry

```java
import com.pi4j.ai.provider.ApiRegistry;

// 方式一：按 API 类型注册（默认）
ApiRegistry.register(new MyCustomProvider());

// 方式二：按 API 类型 + Provider 名称注册（精确匹配）
ApiRegistry.register("openai-completions", "my-provider", new MyCustomProvider());
```

注册后，使用对应的 `api` 和 `provider` 字段创建 `Model` 即可自动路由。

## 跨提供商消息转换

当同一对话中切换模型时（如 Claude → GPT），`MessageTransformer` 自动处理消息格式差异：

```java
List<Message> transformed = MessageTransformer.transform(messages, targetModel);
```

处理规则：

| 场景 | 处理方式 |
|------|---------|
| 思维块 — 同模型 | 保留原始内容和签名 |
| 思维块 — 跨模型 | 转换为纯 TextContent |
| 工具调用 ID — Anthropic | 限 `[a-zA-Z0-9_-]`，最长 64 字符 |
| 工具调用 ID — Mistral | 截断为 9 字符 |
| 孤儿工具调用 | 自动插入合成的空 ToolResultMessage（标记为错误） |
| 错误/中止消息 | 跳过，不传给目标模型 |
| 空白文本块 | 自动移除 |

## 错误处理

### OverflowDetector

`OverflowDetector` 检测 LLM 返回的错误是否为上下文窗口溢出：

```java
boolean overflow = OverflowDetector.isContextOverflow(
    assistantMessage,
    model.getContextWindow()
);
```

检测方式：

1. **错误消息模式匹配** — 覆盖所有主流提供商的错误格式（15+ 种模式）
2. **Token 用量检查** — `inputTokens > contextWindow` 时视为溢出

匹配的错误模式包括：
- `"prompt is too long"`
- `"exceeds the context window"`
- `"input token count.*exceeds the maximum"`
- `"context_length_exceeded"`
- `"too many tokens"`
- 等等

### AbortException

当用户调用 `agent.abort()` 时，`AbortHandle.throwIfAborted()` 抛出 `AbortException`，Provider 应捕获此异常并清理 HTTP 连接。

## 流式处理

### SSE 解析

所有 Provider 通过 OkHttp 发起 HTTP 请求，使用 SSE（Server-Sent Events）协议接收流式响应。SSE 事件格式：

```
data: {"type":"content_block_delta","delta":{"text":"你好"}}

data: {"type":"message_stop"}

data: [DONE]
```

### EventStream 集成

Provider 将 SSE 事件逐个解析并转换为 `AssistantMessageEvent`，推入 `AssistantMessageEventStream`：

```
SSE 响应流
    │
    ▼
Provider 解析逻辑
    │
    ├─ content_block_start  → TextStartEvent / ToolCallStartEvent
    ├─ content_block_delta  → TextDeltaEvent / ToolCallDeltaEvent
    ├─ content_block_stop   → TextEndEvent / ToolCallEndEvent
    ├─ message_stop         → DoneEvent
    └─ error                → ErrorEvent
    │
    ▼
AssistantMessageEventStream
    │
    ├─ subscribe() → Agent 事件处理
    └─ result()    → CompletableFuture<AssistantMessage>
```

Agent 层通过 `subscribe()` 实时接收事件并转换为 `AgentEvent`（如 `MessageUpdateEvent`），同时通过 `result()` 等待最终的 `AssistantMessage` 以继续执行循环。

---

相关文档：
- [快速开始](getting-started.md) — 上手第一个 Agent
- [架构与设计](architecture.md) — 模块结构和执行循环
- [工具系统指南](tool-system.md) — 工具定义、调度和中间件
