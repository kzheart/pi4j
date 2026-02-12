package com.pi4j.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pi4j.agent.event.AgentEvent;
import com.pi4j.agent.event.ToolExecutionEndEvent;
import com.pi4j.agent.tool.AgentTool;
import com.pi4j.agent.tool.AgentToolResult;
import com.pi4j.agent.tool.ToolUpdateCallback;
import com.pi4j.ai.provider.AbortHandle;
import com.pi4j.ai.provider.ApiRegistry;
import com.pi4j.ai.provider.openai.OpenAICompletionsProvider;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.Message;
import com.pi4j.ai.types.Model;
import com.pi4j.ai.types.TextContent;
import com.pi4j.ai.types.ToolResultMessage;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class AgentDeepSeekIntegrationTest {

    @AfterEach
    void cleanup() {
        ApiRegistry.clear();
    }

    @Test
    void agentWithoutToolsWorksOnDeepSeekOpenAiCompat() throws Exception {
        String apiKey = requireApiKey();
        ApiRegistry.register(new OpenAICompletionsProvider());
        List<AgentState> states = new CopyOnWriteArrayList<AgentState>();

        Agent agent = new Agent(AgentOptions.builder()
                .model(deepSeekOpenAiModel())
                .systemPrompt("你是一个简洁助手。")
                .getApiKey(provider -> apiKey)
                .temperature(0.0)
                .maxTokens(128)
                .build());
        agent.subscribeState(states::add);

        agent.prompt("请用一句话回复：OK").get(90, TimeUnit.SECONDS);

        List<AgentMessage> messages = agent.getState().getMessages();
        String assistantText = extractLatestAssistantText(messages);

        assertFalse(assistantText.trim().isEmpty());
        assertTrue(sawStreamingState(states));
        assertTrue(endedInIdleState(states));
    }

    @Test
    void agentWithToolsWorksOnDeepSeekOpenAiCompat() throws Exception {
        String apiKey = requireApiKey();
        ApiRegistry.register(new OpenAICompletionsProvider());

        AgentTool addTool = new AgentTool() {
            @Override
            public String getName() {
                return "add_numbers";
            }

            @Override
            public String getDescription() {
                return "计算两个整数的和";
            }

            @Override
            public String getLabel() {
                return "加法工具";
            }

            @Override
            public JsonObject getParameters() {
                JsonObject schema = new JsonObject();
                schema.addProperty("type", "object");

                JsonObject props = new JsonObject();
                JsonObject a = new JsonObject();
                a.addProperty("type", "integer");
                JsonObject b = new JsonObject();
                b.addProperty("type", "integer");
                props.add("a", a);
                props.add("b", b);
                schema.add("properties", props);

                JsonArray required = new JsonArray();
                required.add("a");
                required.add("b");
                schema.add("required", required);
                return schema;
            }

            @Override
            public AgentToolResult execute(
                    String toolCallId,
                    Map<String, Object> params,
                    AbortHandle abortHandle,
                    ToolUpdateCallback onUpdate) {
                int a = ((Number) params.get("a")).intValue();
                int b = ((Number) params.get("b")).intValue();
                return AgentToolResult.text(String.valueOf(a + b));
            }
        };

        boolean executedTool = false;
        for (int i = 0; i < 2 && !executedTool; i++) {
            List<AgentEvent> events = new CopyOnWriteArrayList<AgentEvent>();
            List<AgentState> states = new CopyOnWriteArrayList<AgentState>();
            Agent agent = new Agent(AgentOptions.builder()
                    .model(deepSeekOpenAiModel())
                    .systemPrompt("你必须先调用工具完成计算，然后只输出最终答案。")
                    .tools(Collections.singletonList(addTool))
                    .getApiKey(provider -> apiKey)
                    .temperature(0.0)
                    .maxTokens(256)
                    .toolChoice("auto")
                    .build());
            agent.subscribe(events::add);
            agent.subscribeState(states::add);

            agent.prompt("请调用 add_numbers 工具计算 19 + 23，最后只回复数字。")
                    .get(120, TimeUnit.SECONDS);

            executedTool = (hasToolExecution(events) || hasToolResult(agent.getState().getMessages()))
                    && sawPendingToolCall(states)
                    && sawStreamingState(states)
                    && endedInIdleState(states);
        }

        assertTrue(executedTool);
    }

    private String requireApiKey() {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.trim().isEmpty(), "DEEPSEEK_API_KEY is not set");
        return apiKey;
    }

    private Model deepSeekOpenAiModel() {
        return new Model(
                "deepseek-chat",
                "DeepSeek Chat",
                "openai-completions",
                "deepseek",
                "https://api.deepseek.com",
                false,
                Arrays.asList("text"),
                null,
                65536,
                4096,
                Collections.<String, String>emptyMap());
    }

    private boolean hasToolExecution(List<AgentEvent> events) {
        for (AgentEvent event : events) {
            if (event instanceof ToolExecutionEndEvent) {
                return true;
            }
        }
        return false;
    }

    private boolean hasToolResult(List<AgentMessage> messages) {
        for (AgentMessage agentMessage : messages) {
            if (agentMessage instanceof LlmAgentMessage) {
                Message message = ((LlmAgentMessage) agentMessage).getMessage();
                if (message instanceof ToolResultMessage) {
                    return true;
                }
            }
        }
        return false;
    }

    private String extractLatestAssistantText(List<AgentMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentMessage agentMessage = messages.get(i);
            if (agentMessage instanceof LlmAgentMessage) {
                Message message = ((LlmAgentMessage) agentMessage).getMessage();
                if (message instanceof com.pi4j.ai.types.AssistantMessage) {
                    StringBuilder builder = new StringBuilder();
                    for (ContentBlock block : ((com.pi4j.ai.types.AssistantMessage) message).getContent()) {
                        if (block instanceof TextContent) {
                            builder.append(((TextContent) block).getText());
                        }
                    }
                    return builder.toString();
                }
            }
        }
        return "";
    }

    private boolean sawStreamingState(List<AgentState> states) {
        for (AgentState state : states) {
            if (state.isStreaming()) {
                return true;
            }
        }
        return false;
    }

    private boolean endedInIdleState(List<AgentState> states) {
        if (states.isEmpty()) {
            return false;
        }
        return !states.get(states.size() - 1).isStreaming();
    }

    private boolean sawPendingToolCall(List<AgentState> states) {
        for (AgentState state : states) {
            if (!state.getPendingToolCalls().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
