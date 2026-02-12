package com.pi4j.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pi4j.agent.event.AgentEvent;
import com.pi4j.agent.event.ToolExecutionEndEvent;
import com.pi4j.agent.event.ToolExecutionStartEvent;
import com.pi4j.agent.tool.AgentTool;
import com.pi4j.agent.tool.AgentToolResult;
import com.pi4j.agent.tool.ToolUpdateCallback;
import com.pi4j.ai.provider.AbortHandle;
import com.pi4j.ai.provider.ApiProvider;
import com.pi4j.ai.provider.ApiRegistry;
import com.pi4j.ai.provider.StreamOptions;
import com.pi4j.ai.stream.AssistantMessageEventStream;
import com.pi4j.ai.stream.DoneEvent;
import com.pi4j.ai.stream.StartEvent;
import com.pi4j.ai.stream.TextDeltaEvent;
import com.pi4j.ai.stream.TextEndEvent;
import com.pi4j.ai.stream.TextStartEvent;
import com.pi4j.ai.stream.ToolCallEndEvent;
import com.pi4j.ai.stream.ToolCallStartEvent;
import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.Context;
import com.pi4j.ai.types.Message;
import com.pi4j.ai.types.Model;
import com.pi4j.ai.types.StopReason;
import com.pi4j.ai.types.TextContent;
import com.pi4j.ai.types.ToolCallContent;
import com.pi4j.ai.types.ToolResultMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AgentTest {

    @AfterEach
    void cleanup() {
        ApiRegistry.clear();
    }

    @Test
    void promptWithoutToolsCompletes() throws Exception {
        ApiRegistry.register(new StaticProvider(false));

        Model model = new Model(
                "demo",
                "Demo",
                "openai-completions",
                "openai",
                "https://api.openai.com",
                false,
                Arrays.asList("text"),
                null,
                32000,
                4096,
                Collections.<String, String>emptyMap());

        Agent agent = new Agent(AgentOptions.builder()
                .model(model)
                .getApiKey(provider -> "test-key")
                .build());

        agent.prompt("hello").get();

        AgentState state = agent.getState();
        assertFalse(state.getMessages().isEmpty());
        assertFalse(state.isStreaming());
    }

    @Test
    void promptWithToolCallExecutesToolAndEmitsEvents() throws Exception {
        ApiRegistry.register(new StaticProvider(true));

        Model model = new Model(
                "demo",
                "Demo",
                "openai-completions",
                "openai",
                "https://api.openai.com",
                false,
                Arrays.asList("text"),
                null,
                32000,
                4096,
                Collections.<String, String>emptyMap());

        AgentTool tool = new AgentTool() {
            @Override
            public String getName() {
                return "sum";
            }

            @Override
            public String getDescription() {
                return "sum numbers";
            }

            @Override
            public String getLabel() {
                return "sum";
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

        List<AgentEvent> events = new CopyOnWriteArrayList<AgentEvent>();

        Agent agent = new Agent(AgentOptions.builder()
                .model(model)
                .tools(Collections.singletonList(tool))
                .getApiKey(provider -> "test-key")
                .build());
        agent.subscribe(events::add);

        agent.prompt("calc").get();

        boolean hasToolStart = false;
        boolean hasToolEnd = false;
        for (AgentEvent event : events) {
            if (event instanceof ToolExecutionStartEvent) {
                hasToolStart = true;
            }
            if (event instanceof ToolExecutionEndEvent) {
                hasToolEnd = true;
            }
        }

        assertTrue(hasToolStart);
        assertTrue(hasToolEnd);

        List<AgentMessage> messages = agent.getState().getMessages();
        boolean hasToolResult = false;
        for (AgentMessage message : messages) {
            if (message instanceof LlmAgentMessage) {
                Message llm = ((LlmAgentMessage) message).getMessage();
                if (llm instanceof ToolResultMessage) {
                    hasToolResult = true;
                }
            }
        }
        assertTrue(hasToolResult);
    }

    private static class StaticProvider implements ApiProvider {
        private final boolean withToolCall;

        private StaticProvider(boolean withToolCall) {
            this.withToolCall = withToolCall;
        }

        @Override
        public String getApi() {
            return "openai-completions";
        }

        @Override
        public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
            AssistantMessageEventStream stream = new AssistantMessageEventStream();
            stream.push(new StartEvent());

            boolean hasToolResult = false;
            for (Message message : context.getMessages()) {
                if (message instanceof ToolResultMessage) {
                    hasToolResult = true;
                    break;
                }
            }

            List<ContentBlock> content = new ArrayList<ContentBlock>();
            if (withToolCall && !hasToolResult) {
                ToolCallContent toolCall = new ToolCallContent("call_1", "sum", new java.util.LinkedHashMap<String, Object>() {{
                    put("a", 1);
                    put("b", 2);
                }});
                content.add(toolCall);
                AssistantMessage message = new AssistantMessage(
                        content,
                        getApi(),
                        model.getProvider(),
                        model.getId(),
                        null,
                        StopReason.TOOL_USE,
                        null);
                stream.push(new ToolCallStartEvent(0));
                stream.push(new ToolCallEndEvent(0, toolCall, message));
                stream.push(new DoneEvent(StopReason.TOOL_USE, message));
                stream.end(message);
                return stream;
            }

            TextContent text = new TextContent("done");
            content.add(text);
            AssistantMessage message = new AssistantMessage(
                    content,
                    getApi(),
                    model.getProvider(),
                    model.getId(),
                    null,
                    StopReason.STOP,
                    null);
            stream.push(new TextStartEvent(0));
            stream.push(new TextDeltaEvent(0, text.getText(), message));
            stream.push(new TextEndEvent(0));
            stream.push(new DoneEvent(StopReason.STOP, message));
            stream.end(message);
            return stream;
        }
    }
}
