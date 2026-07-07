package com.pi4j.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pi4j.agent.event.AgentEvent;
import com.pi4j.agent.event.MessageUpdateEvent;
import com.pi4j.agent.event.ToolExecutionEndEvent;
import com.pi4j.agent.event.ToolExecutionStartEvent;
import com.pi4j.agent.tool.AgentTool;
import com.pi4j.agent.tool.AgentToolResult;
import com.pi4j.agent.tool.ToolSpec;
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
import com.pi4j.ai.types.UserMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
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
    void promptEmitsMessageUpdateEventsForTextDeltas() throws Exception {
        ApiRegistry.register(new StaticProvider(false));

        Agent agent = new Agent(AgentOptions.builder()
                .model(buildModel())
                .getApiKey(provider -> "test-key")
                .build());
        List<MessageUpdateEvent> updates = new CopyOnWriteArrayList<MessageUpdateEvent>();
        agent.subscribe(event -> {
            if (event instanceof MessageUpdateEvent) {
                updates.add((MessageUpdateEvent) event);
            }
        });

        agent.prompt("hello").get();

        assertFalse(updates.isEmpty());
        AgentMessage updateMessage = null;
        boolean hasTextDelta = false;
        for (MessageUpdateEvent update : updates) {
            if (updateMessage == null) {
                updateMessage = update.getMessage();
            } else {
                assertTrue(update.getMessage() == updateMessage);
            }
            if (update.getAssistantMessageEvent() instanceof TextDeltaEvent) {
                hasTextDelta = true;
            }
        }
        assertTrue(hasTextDelta);
    }

    @Test
    void messageUpdateEventsDoNotReusePreviousTurnFinalMessage() throws Exception {
        ApiRegistry.register(new StaticProvider(true));

        AgentTool tool = ToolSpec.builder("sum")
                .description("sum numbers")
                .label("sum")
                .integerParam("a", true, "first")
                .integerParam("b", true, "second")
                .handler((toolCallId, args, abortHandle, onUpdate) -> AgentToolResult.text(String.valueOf(
                        args.requireInt("a") + args.requireInt("b"))))
                .build()
                .toAgentTool();

        Agent agent = new Agent(AgentOptions.builder()
                .model(buildModel())
                .tools(Collections.singletonList(tool))
                .getApiKey(provider -> "test-key")
                .build());
        List<MessageUpdateEvent> updates = new CopyOnWriteArrayList<MessageUpdateEvent>();
        agent.subscribe(event -> {
            if (event instanceof MessageUpdateEvent) {
                updates.add((MessageUpdateEvent) event);
            }
        });

        agent.prompt("calc").get();

        AgentMessage firstAssistantMessage = null;
        int assistantCount = 0;
        for (AgentMessage message : agent.getState().getMessages()) {
            if (!(message instanceof LlmAgentMessage)) {
                continue;
            }
            Message llm = ((LlmAgentMessage) message).getMessage();
            if (!(llm instanceof AssistantMessage)) {
                continue;
            }
            assistantCount++;
            if (firstAssistantMessage == null) {
                firstAssistantMessage = message;
            }
        }
        assertEquals(2, assistantCount);
        assertTrue(firstAssistantMessage != null);
        assertFalse(updates.isEmpty());

        boolean reusedPreviousTurnFinalMessage = false;
        boolean hasTextDelta = false;
        for (MessageUpdateEvent update : updates) {
            if (update.getMessage() == firstAssistantMessage) {
                reusedPreviousTurnFinalMessage = true;
            }
            if (update.getAssistantMessageEvent() instanceof TextDeltaEvent) {
                hasTextDelta = true;
            }
        }
        assertFalse(reusedPreviousTurnFinalMessage);
        assertTrue(hasTextDelta);
    }

    @Test
    void continueExecutionRunsRealLoop() throws Exception {
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

        agent.appendMessage(new LlmAgentMessage(new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("hello")))));

        agent.continueExecution().get();

        List<AgentMessage> loopMessages = agent.getState().getMessages();
        assertTrue(loopMessages.size() >= 2);
        assertTrue(loopMessages.get(loopMessages.size() - 1) instanceof LlmAgentMessage);
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

    @Test
    void promptWithToolSpecExecutesTool() throws Exception {
        ApiRegistry.register(new StaticProvider(true));
        Model model = buildModel();

        AgentTool tool = ToolSpec.builder("sum")
                .description("sum numbers")
                .label("sum")
                .integerParam("a", true, "first")
                .integerParam("b", true, "second")
                .handler((toolCallId, args, abortHandle, onUpdate) -> AgentToolResult.text(String.valueOf(
                        args.requireInt("a") + args.requireInt("b"))))
                .build()
                .toAgentTool();

        Agent agent = new Agent(AgentOptions.builder()
                .model(model)
                .tools(Collections.singletonList(tool))
                .getApiKey(provider -> "test-key")
                .build());

        agent.prompt("calc").get();

        boolean hasSumResult = false;
        for (AgentMessage message : agent.getState().getMessages()) {
            if (!(message instanceof LlmAgentMessage)) {
                continue;
            }
            Message llm = ((LlmAgentMessage) message).getMessage();
            if (!(llm instanceof ToolResultMessage)) {
                continue;
            }
            ToolResultMessage result = (ToolResultMessage) llm;
            if (!"sum".equals(result.getToolName())) {
                continue;
            }
            if (!result.getContent().isEmpty() && result.getContent().get(0) instanceof TextContent) {
                hasSumResult = "3".equals(((TextContent) result.getContent().get(0)).getText());
            }
        }
        assertTrue(hasSumResult);
    }

    @Test
    void steeringModeOneAtATimeConsumesOnePerTurn() throws Exception {
        ApiRegistry.register(new StaticProvider(false));
        Model model = buildModel();

        Agent agent = new Agent(AgentOptions.builder()
                .model(model)
                .steeringMode("one-at-a-time")
                .getApiKey(provider -> "test-key")
                .build());

        agent.appendMessage(new LlmAgentMessage(new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("base")))));
        agent.steer(new LlmAgentMessage(new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("s1")))));
        agent.steer(new LlmAgentMessage(new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("s2")))));

        agent.continueExecution().get();
        assertEquals(2, countAssistantMessages(agent.getState().getMessages()));
    }

    @Test
    void steeringModeAllConsumesInSingleTurn() throws Exception {
        ApiRegistry.register(new StaticProvider(false));
        Model model = buildModel();

        Agent agent = new Agent(AgentOptions.builder()
                .model(model)
                .steeringMode("all")
                .getApiKey(provider -> "test-key")
                .build());

        agent.appendMessage(new LlmAgentMessage(new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("base")))));
        agent.steer(new LlmAgentMessage(new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("s1")))));
        agent.steer(new LlmAgentMessage(new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("s2")))));

        agent.continueExecution().get();
        assertEquals(1, countAssistantMessages(agent.getState().getMessages()));
    }

    @Test
    void followUpModeOneAtATimeConsumesOnePerOuterLoop() throws Exception {
        ApiRegistry.register(new StaticProvider(false));
        Model model = buildModel();

        Agent agent = new Agent(AgentOptions.builder()
                .model(model)
                .followUpMode("one-at-a-time")
                .getApiKey(provider -> "test-key")
                .build());

        agent.appendMessage(new LlmAgentMessage(new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("base")))));
        agent.followUp(new LlmAgentMessage(new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("f1")))));
        agent.followUp(new LlmAgentMessage(new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("f2")))));

        agent.continueExecution().get();
        assertEquals(3, countAssistantMessages(agent.getState().getMessages()));
    }

    @Test
    void steeringAfterToolSkipsRemainingToolCalls() throws Exception {
        ApiRegistry.register(new MultiToolProvider());
        Model model = buildModel();

        AtomicReference<Agent> agentRef = new AtomicReference<Agent>();
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
                if ("call_1".equals(toolCallId)) {
                    Agent currentAgent = agentRef.get();
                    currentAgent.steer(new LlmAgentMessage(new UserMessage(
                            Collections.<ContentBlock>singletonList(new TextContent("interrupt")))));
                }
                int a = ((Number) params.get("a")).intValue();
                int b = ((Number) params.get("b")).intValue();
                return AgentToolResult.text(String.valueOf(a + b));
            }
        };

        Agent agent = new Agent(AgentOptions.builder()
                .model(model)
                .tools(Collections.singletonList(tool))
                .steeringMode("one-at-a-time")
                .getApiKey(provider -> "test-key")
                .build());
        agentRef.set(agent);

        agent.prompt("calc").get();

        int skippedResults = 0;
        for (AgentMessage message : agent.getState().getMessages()) {
            if (!(message instanceof LlmAgentMessage)) {
                continue;
            }
            Message llm = ((LlmAgentMessage) message).getMessage();
            if (llm instanceof ToolResultMessage) {
                ToolResultMessage toolResult = (ToolResultMessage) llm;
                if ("call_2".equals(toolResult.getToolCallId()) && toolResult.isError()) {
                    skippedResults++;
                }
            }
        }
        assertEquals(1, skippedResults);
    }

    @Test
    void queueManagementApisWork() {
        Agent agent = new Agent(AgentOptions.builder()
                .model(buildModel())
                .build());
        agent.steer(new LlmAgentMessage(new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("s")))));
        agent.followUp(new LlmAgentMessage(new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("f")))));
        assertTrue(agent.hasQueuedMessages());

        agent.clearSteeringQueue();
        assertTrue(agent.hasQueuedMessages());

        agent.clearFollowUpQueue();
        assertFalse(agent.hasQueuedMessages());
    }

    @Test
    void executionErrorCreatesAssistantErrorMessage() throws Exception {
        ApiRegistry.register(new FailingProvider());
        Agent agent = new Agent(AgentOptions.builder()
                .model(buildModel())
                .getApiKey(provider -> "test-key")
                .build());

        agent.prompt("hello").get();

        AssistantMessage last = lastAssistantMessage(agent.getState().getMessages());
        assertEquals(StopReason.ERROR, last.getStopReason());
        assertTrue(last.getErrorMessage().contains("boom"));
    }

    @Test
    void promptWithUnregisteredToolNameProducesErrorResultAndCompletes() throws Exception {
        ApiRegistry.register(new UnregisteredToolProvider());
        Model model = buildModel();

        Agent agent = new Agent(AgentOptions.builder()
                .model(model)
                .getApiKey(provider -> "test-key")
                .build());

        agent.prompt("use phantom tool").get();

        assertFalse(agent.getState().isStreaming());
        assertEquals(null, agent.getState().getError());

        boolean hasPhantomErrorResult = false;
        for (AgentMessage message : agent.getState().getMessages()) {
            if (!(message instanceof LlmAgentMessage)) {
                continue;
            }
            Message llm = ((LlmAgentMessage) message).getMessage();
            if (!(llm instanceof ToolResultMessage)) {
                continue;
            }
            ToolResultMessage result = (ToolResultMessage) llm;
            if (!"phantom".equals(result.getToolName())) {
                continue;
            }
            if (!result.isError()) {
                continue;
            }
            if (!result.getContent().isEmpty() && result.getContent().get(0) instanceof TextContent) {
                hasPhantomErrorResult = "Tool not found: phantom".equals(
                        ((TextContent) result.getContent().get(0)).getText());
            }
        }
        assertTrue(hasPhantomErrorResult);
    }

    @Test
    void abortCreatesAssistantAbortedMessage() throws Exception {
        ApiRegistry.register(new BlockingProvider());
        Agent agent = new Agent(AgentOptions.builder()
                .model(buildModel())
                .getApiKey(provider -> "test-key")
                .build());

        java.util.concurrent.CompletableFuture<Void> future = agent.prompt("hello");
        Thread.sleep(30L);
        agent.abort();
        future.get();

        AssistantMessage last = lastAssistantMessage(agent.getState().getMessages());
        assertEquals(StopReason.ABORTED, last.getStopReason());
    }

    private Model buildModel() {
        return new Model(
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
    }

    private int countAssistantMessages(List<AgentMessage> messages) {
        int count = 0;
        for (AgentMessage message : messages) {
            if (message instanceof LlmAgentMessage) {
                if (((LlmAgentMessage) message).getMessage() instanceof AssistantMessage) {
                    count++;
                }
            }
        }
        return count;
    }

    private AssistantMessage lastAssistantMessage(List<AgentMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentMessage message = messages.get(i);
            if (!(message instanceof LlmAgentMessage)) {
                continue;
            }
            Message llm = ((LlmAgentMessage) message).getMessage();
            if (llm instanceof AssistantMessage) {
                return (AssistantMessage) llm;
            }
        }
        throw new AssertionError("assistant message not found");
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

    private static final class MultiToolProvider implements ApiProvider {

        @Override
        public String getApi() {
            return "openai-completions";
        }

        @Override
        public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
            AssistantMessageEventStream stream = new AssistantMessageEventStream();
            stream.push(new StartEvent());

            int toolResultCount = 0;
            for (Message message : context.getMessages()) {
                if (message instanceof ToolResultMessage) {
                    toolResultCount++;
                }
            }

            if (toolResultCount == 0) {
                List<ContentBlock> content = new ArrayList<ContentBlock>();
                content.add(new ToolCallContent("call_1", "sum", new java.util.LinkedHashMap<String, Object>() {{
                    put("a", 1);
                    put("b", 2);
                }}));
                content.add(new ToolCallContent("call_2", "sum", new java.util.LinkedHashMap<String, Object>() {{
                    put("a", 3);
                    put("b", 4);
                }}));
                AssistantMessage message = new AssistantMessage(
                        content,
                        getApi(),
                        model.getProvider(),
                        model.getId(),
                        null,
                        StopReason.TOOL_USE,
                        null);
                stream.push(new ToolCallStartEvent(0));
                stream.push(new ToolCallEndEvent(0, (ToolCallContent) content.get(0), message));
                stream.push(new ToolCallStartEvent(1));
                stream.push(new ToolCallEndEvent(1, (ToolCallContent) content.get(1), message));
                stream.push(new DoneEvent(StopReason.TOOL_USE, message));
                stream.end(message);
                return stream;
            }

            TextContent text = new TextContent("done");
            AssistantMessage message = new AssistantMessage(
                    Collections.<ContentBlock>singletonList(text),
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

    private static final class UnregisteredToolProvider implements ApiProvider {

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
            if (!hasToolResult) {
                ToolCallContent toolCall = new ToolCallContent("call_1", "phantom", new java.util.LinkedHashMap<String, Object>());
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

    private static final class FailingProvider implements ApiProvider {
        @Override
        public String getApi() {
            return "openai-completions";
        }

        @Override
        public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
            throw new IllegalStateException("boom");
        }
    }

    private static final class BlockingProvider implements ApiProvider {
        @Override
        public String getApi() {
            return "openai-completions";
        }

        @Override
        public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
            AssistantMessageEventStream stream = new AssistantMessageEventStream();
            AbortHandle abortHandle = options.getAbortHandle();
            while (!abortHandle.isAborted()) {
                try {
                    Thread.sleep(5L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            abortHandle.throwIfAborted();
            return stream;
        }
    }
}
