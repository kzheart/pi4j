# 快速开始

本指南将帮助你在几分钟内启动第一个 Pi4J AI Agent。

## 前置要求

- **Java 8+** — Pi4J 以 Java 8 为编译目标，兼容所有 Java 8 及以上版本
- **Gradle** — 推荐使用 Gradle 进行依赖管理（Maven 同理）
- **LLM API Key** — 至少一个 LLM 提供商的 API 密钥（如 Anthropic、OpenAI 等）

## 安装

在 `build.gradle.kts` 中添加依赖：

```kotlin
repositories {
    mavenLocal()
}

dependencies {
    // 核心：Agent 运行时（自动包含 pi4j-ai）
    implementation("com.pi4j:pi4j-agent:1.0.8-SNAPSHOT")

    // 可选：内置工具（read、write、bash、grep 等）
    implementation("com.pi4j:pi4j-tools:1.0.8-SNAPSHOT")
}
```

如果使用 Gradle Groovy DSL：

```groovy
dependencies {
    implementation 'com.pi4j:pi4j-agent:1.0.8-SNAPSHOT'
    implementation 'com.pi4j:pi4j-tools:1.0.8-SNAPSHOT'
}
```

构建并发布到本地仓库：

```bash
git clone https://github.com/your-org/pi4j.git
cd pi4j
./gradlew publishToMavenLocal
```

## 创建你的第一个 Agent

以下是一个使用 Anthropic Claude 的完整示例：

```java
import com.pi4j.agent.Agent;
import com.pi4j.agent.AgentOptions;
import com.pi4j.agent.AgentMessage;
import com.pi4j.agent.LlmAgentMessage;
import com.pi4j.ai.types.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MyFirstAgent {
    public static void main(String[] args) {
        // 1. 定义模型
        Model model = new Model(
            "claude-sonnet-4-20250514",       // 模型 ID
            "Claude Sonnet 4",                // 显示名称
            "anthropic-messages",             // API 类型
            "anthropic",                      // 提供商
            "https://api.anthropic.com",      // API 基础 URL
            false,                            // 是否支持推理模式
            Arrays.asList("text", "image"),   // 支持的输入类型
            null,                             // 费用（可选）
            200000,                           // 上下文窗口
            8192,                             // 最大输出 token
            Collections.<String, String>emptyMap()  // 自定义请求头
        );

        // 2. 创建 Agent
        Agent agent = new Agent(AgentOptions.builder()
            .systemPrompt("你是一个有用的 AI 助手。")
            .model(model)
            .getApiKey(provider -> System.getenv("ANTHROPIC_API_KEY"))
            .build());

        // 3. 发送消息并等待完成
        agent.prompt("用一句话解释什么是 Java Agent 框架").join();

        // 4. 获取助手回复
        List<AgentMessage> messages = agent.getState().getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentMessage msg = messages.get(i);
            if (msg instanceof LlmAgentMessage
                    && "assistant".equals(msg.getRole())) {
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

## 添加工具

Pi4J 提供两种方式定义工具：声明式 `ToolSpec` 和手动实现 `AgentTool` 接口。

### 方式一：ToolSpec Builder（推荐）

`ToolSpec` 提供声明式 API，自动生成 JSON Schema：

```java
import com.pi4j.agent.tool.ToolSpec;
import com.pi4j.agent.tool.AgentTool;
import com.pi4j.agent.tool.AgentToolResult;

AgentTool weatherTool = ToolSpec.builder("get_weather")
    .description("查询城市天气信息")
    .stringParam("city", true, "城市名称")
    .integerParam("days", false, "预报天数，默认 1")
    .handler((toolCallId, args, abortHandle, onUpdate) -> {
        String city = args.requireString("city");
        int days = args.has("days") ? args.requireInt("days") : 1;
        // 调用天气 API ...
        return AgentToolResult.text(city + " 未来 " + days + " 天：晴");
    })
    .build()
    .toAgentTool();
```

`ToolSpec.Builder` 支持以下参数类型方法：

| 方法 | JSON Schema 类型 | 说明 |
|------|-----------------|------|
| `stringParam(name, required, desc)` | `string` | 字符串参数 |
| `numberParam(name, required, desc)` | `number` | 浮点数参数 |
| `integerParam(name, required, desc)` | `integer` | 整数参数 |
| `booleanParam(name, required, desc)` | `boolean` | 布尔参数 |

### 方式二：实现 AgentTool 接口

当需要手写复杂 JSON Schema（如嵌套对象、数组）时，直接实现接口：

```java
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pi4j.agent.tool.AgentTool;
import com.pi4j.agent.tool.AgentToolResult;
import com.pi4j.agent.tool.ToolUpdateCallback;
import com.pi4j.ai.provider.AbortHandle;
import java.util.Map;

AgentTool timeTool = new AgentTool() {
    @Override
    public String getName() { return "get_time"; }

    @Override
    public String getDescription() { return "查询指定时区的当前时间"; }

    @Override
    public String getLabel() { return "获取时间"; }

    @Override
    public JsonObject getParameters() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();
        JsonObject zone = new JsonObject();
        zone.addProperty("type", "string");
        zone.addProperty("description", "IANA 时区名称，如 Asia/Shanghai");
        props.add("zone", zone);

        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("zone");
        schema.add("required", required);
        return schema;
    }

    @Override
    public AgentToolResult execute(String toolCallId,
                                   Map<String, Object> params,
                                   AbortHandle abortHandle,
                                   ToolUpdateCallback onUpdate) {
        String zone = String.valueOf(params.get("zone"));
        return AgentToolResult.text(
            java.time.ZonedDateTime.now(java.time.ZoneId.of(zone)).toString()
        );
    }
};
```

### 注册工具到 Agent

```java
import java.util.Arrays;

Agent agent = new Agent(AgentOptions.builder()
    .systemPrompt("你是一个智能助手")
    .model(model)
    .tools(Arrays.asList(weatherTool, timeTool))
    .getApiKey(provider -> System.getenv("ANTHROPIC_API_KEY"))
    .build());
```

## 流式事件监听

订阅 Agent 执行过程中的细粒度事件：

```java
import com.pi4j.agent.event.*;
import com.pi4j.ai.stream.TextDeltaEvent;

Runnable unsubscribe = agent.subscribe(event -> {
    if (event instanceof AgentStartEvent) {
        System.out.println("--- Agent 开始 ---");
    }
    if (event instanceof MessageUpdateEvent) {
        MessageUpdateEvent update = (MessageUpdateEvent) event;
        if (update.getAssistantMessageEvent() instanceof TextDeltaEvent) {
            // 实时打印 LLM 输出
            System.out.print(
                ((TextDeltaEvent) update.getAssistantMessageEvent()).getDelta()
            );
        }
    }
    if (event instanceof ToolExecutionStartEvent) {
        System.out.println("\n正在执行工具: "
            + ((ToolExecutionStartEvent) event).getToolName());
    }
    if (event instanceof AgentEndEvent) {
        System.out.println("\n--- Agent 结束 ---");
    }
});

agent.prompt("上海现在几点？").join();
unsubscribe.run(); // 停止监听
```

事件层次结构详见 [架构与设计](architecture.md#agent-事件体系)。

## 使用内置工具

`pi4j-tools` 模块提供 7 个开箱即用的开发者工具：

```java
import com.pi4j.tools.BuiltinTools;
import com.pi4j.agent.tool.AgentTool;
import java.nio.file.Paths;
import java.util.List;

// 选择特定工具
List<AgentTool> tools = BuiltinTools.select(
    Paths.get("."), "read", "write", "bash", "grep"
);

// 或使用全部 7 个工具
List<AgentTool> allTools = BuiltinTools.all(Paths.get("."));

Agent codingAgent = new Agent(AgentOptions.builder()
    .systemPrompt("你是一个编码助手，可以读写文件和执行命令。")
    .model(model)
    .tools(tools)
    .getApiKey(provider -> System.getenv("ANTHROPIC_API_KEY"))
    .build());
```

| 工具 | 名称 | 说明 |
|------|------|------|
| ReadTool | `read` | 读取文件内容（支持行号偏移/限制） |
| WriteTool | `write` | 创建或覆盖文件 |
| EditTool | `edit` | 查找替换编辑 |
| BashTool | `bash` | 执行 shell 命令（支持超时和中止） |
| GrepTool | `grep` | 跨文件正则搜索 |
| FindTool | `find` | 按 glob 模式查找文件 |
| LsTool | `ls` | 列出目录内容 |

更多详情参见 [工具系统指南](tool-system.md#内置工具)。

## 会话持久化

使用 `SessionManager` 将对话持久化为 JSONL 文件：

```java
import com.pi4j.agent.LlmAgentMessage;
import com.pi4j.agent.session.Session;
import com.pi4j.agent.session.SessionInfo;
import com.pi4j.agent.session.SessionManager;
import com.pi4j.ai.types.TextContent;
import com.pi4j.ai.types.UserMessage;
import com.pi4j.ai.types.ContentBlock;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

SessionManager sessionManager = new SessionManager(Paths.get("sessions"));

// 创建新会话
Session session = sessionManager.create("user-123");

// 追加消息
List<ContentBlock> content = Collections.<ContentBlock>singletonList(
    new TextContent("你好，请帮我分析这段代码")
);
session.appendMessage(new LlmAgentMessage(new UserMessage(content)));

// 加载已有会话
Session loaded = sessionManager.load("user-123");
System.out.println("消息数: " + loaded.getMessages().size());

// 列出所有会话
for (SessionInfo info : sessionManager.list()) {
    System.out.println(info.getSessionId() + " -> " + info.getPath());
}

// 删除会话
sessionManager.delete("user-123");
```

## 下一步

- [架构与设计](architecture.md) — 深入了解模块结构、执行循环和事件流
- [工具系统指南](tool-system.md) — 工具调度、中间件、自定义工具详解
- [LLM 提供商指南](providers.md) — 13 个提供商的配置与使用
