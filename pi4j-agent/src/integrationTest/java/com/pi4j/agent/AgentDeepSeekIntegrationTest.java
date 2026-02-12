package com.pi4j.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pi4j.agent.event.AgentEvent;
import com.pi4j.agent.event.MessageEndEvent;
import com.pi4j.agent.event.MessageUpdateEvent;
import com.pi4j.agent.event.ToolExecutionEndEvent;
import com.pi4j.agent.event.ToolExecutionStartEvent;
import com.pi4j.agent.tool.AgentTool;
import com.pi4j.agent.tool.AgentToolResult;
import com.pi4j.agent.tool.ToolUpdateCallback;
import com.pi4j.ai.provider.AbortHandle;
import com.pi4j.ai.provider.ApiRegistry;
import com.pi4j.ai.provider.openai.OpenAICompletionsProvider;
import com.pi4j.ai.stream.AssistantMessageEvent;
import com.pi4j.ai.stream.TextDeltaEvent;
import com.pi4j.ai.stream.ToolCallDeltaEvent;
import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.Message;
import com.pi4j.ai.types.Model;
import com.pi4j.ai.types.TextContent;
import com.pi4j.ai.types.ToolCallContent;
import com.pi4j.ai.types.ToolResultMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Test
    void agentComplexToolWorkflowShowsStateAndModelOutput() throws Exception {
        String apiKey = requireApiKey();
        ApiRegistry.register(new OpenAICompletionsProvider());

        AgentTool extractNumbersTool = new AgentTool() {
            @Override
            public String getName() {
                return "extract_numbers";
            }

            @Override
            public String getDescription() {
                return "从文本中提取整数数组并返回结果";
            }

            @Override
            public String getLabel() {
                return "数字抽取工具";
            }

            @Override
            public JsonObject getParameters() {
                JsonObject schema = new JsonObject();
                schema.addProperty("type", "object");

                JsonObject props = new JsonObject();
                JsonObject payload = new JsonObject();
                payload.addProperty("type", "string");
                props.add("payload", payload);
                schema.add("properties", props);

                JsonArray required = new JsonArray();
                required.add("payload");
                schema.add("required", required);
                return schema;
            }

            @Override
            public AgentToolResult execute(
                    String toolCallId,
                    Map<String, Object> params,
                    AbortHandle abortHandle,
                    ToolUpdateCallback onUpdate) {
                String payload = String.valueOf(params.get("payload"));
                List<Integer> numbers = new ArrayList<Integer>();
                String[] tokens = payload.split("[^0-9-]+");
                for (String token : tokens) {
                    if (token == null || token.trim().isEmpty() || "-".equals(token.trim())) {
                        continue;
                    }
                    numbers.add(Integer.parseInt(token.trim()));
                }
                return AgentToolResult.text("{\"numbers\":" + numbers.toString() + ",\"count\":" + numbers.size() + "}");
            }
        };

        AgentTool calculateStatisticsTool = new AgentTool() {
            @Override
            public String getName() {
                return "calculate_statistics";
            }

            @Override
            public String getDescription() {
                return "对数字数组计算统计信息";
            }

            @Override
            public String getLabel() {
                return "统计计算工具";
            }

            @Override
            public JsonObject getParameters() {
                JsonObject schema = new JsonObject();
                schema.addProperty("type", "object");

                JsonObject props = new JsonObject();
                JsonObject numbers = new JsonObject();
                numbers.addProperty("type", "array");
                JsonObject metadata = new JsonObject();
                metadata.addProperty("type", "object");
                props.add("numbers", numbers);
                props.add("metadata", metadata);
                schema.add("properties", props);

                JsonArray required = new JsonArray();
                required.add("numbers");
                schema.add("required", required);
                return schema;
            }

            @Override
            public AgentToolResult execute(
                    String toolCallId,
                    Map<String, Object> params,
                    AbortHandle abortHandle,
                    ToolUpdateCallback onUpdate) {
                List<Object> rawNumbers = (List<Object>) params.get("numbers");
                List<Double> values = new ArrayList<Double>();
                for (Object rawNumber : rawNumbers) {
                    if (rawNumber instanceof Number) {
                        values.add(((Number) rawNumber).doubleValue());
                    } else {
                        values.add(Double.parseDouble(String.valueOf(rawNumber)));
                    }
                }
                double sum = 0.0;
                double min = Double.POSITIVE_INFINITY;
                double max = Double.NEGATIVE_INFINITY;
                for (Double value : values) {
                    sum += value;
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                }
                double avg = values.isEmpty() ? 0.0 : (sum / values.size());
                String payload = String.format(
                        "{\"sum\":%.2f,\"avg\":%.2f,\"min\":%.2f,\"max\":%.2f,\"count\":%d}",
                        sum, avg, min, max, values.size());
                return AgentToolResult.text(payload);
            }
        };

        AgentTool renderFinalReportTool = new AgentTool() {
            @Override
            public String getName() {
                return "render_final_report";
            }

            @Override
            public String getDescription() {
                return "渲染最终文本报告";
            }

            @Override
            public String getLabel() {
                return "报告渲染工具";
            }

            @Override
            public JsonObject getParameters() {
                JsonObject schema = new JsonObject();
                schema.addProperty("type", "object");

                JsonObject props = new JsonObject();
                JsonObject title = new JsonObject();
                title.addProperty("type", "string");
                JsonObject stats = new JsonObject();
                stats.addProperty("type", "object");
                JsonObject constraints = new JsonObject();
                constraints.addProperty("type", "array");
                props.add("title", title);
                props.add("stats", stats);
                props.add("constraints", constraints);
                schema.add("properties", props);

                JsonArray required = new JsonArray();
                required.add("title");
                required.add("stats");
                schema.add("required", required);
                return schema;
            }

            @Override
            public AgentToolResult execute(
                    String toolCallId,
                    Map<String, Object> params,
                    AbortHandle abortHandle,
                    ToolUpdateCallback onUpdate) {
                String title = String.valueOf(params.get("title"));
                Object stats = params.get("stats");
                return AgentToolResult.text("FINAL_REPORT|title=" + title + "|stats=" + String.valueOf(stats));
            }
        };

        boolean complexFlowSuccess = false;
        for (int attempt = 1; attempt <= 3 && !complexFlowSuccess; attempt++) {
            final int attemptNo = attempt;
            List<AgentEvent> events = new CopyOnWriteArrayList<AgentEvent>();
            List<AgentState> states = new CopyOnWriteArrayList<AgentState>();
            AtomicInteger stateIndex = new AtomicInteger();
            StringBuilder streamedOutput = new StringBuilder();
            StringBuilder toolCallOutput = new StringBuilder();
            long startAt = System.currentTimeMillis();

            Agent agent = new Agent(AgentOptions.builder()
                    .model(deepSeekOpenAiModel())
                    .systemPrompt("你是一个严谨的任务执行助手。涉及数据处理时必须通过工具调用完成，不要跳过工具。")
                    .tools(Arrays.asList(extractNumbersTool, calculateStatisticsTool, renderFinalReportTool))
                    .getApiKey(provider -> apiKey)
                    .temperature(0.0)
                    .maxTokens(512)
                    .toolChoice("auto")
                    .build());

            agent.subscribeState(state -> {
                states.add(state);
                int index = stateIndex.incrementAndGet();
                long elapsed = System.currentTimeMillis() - startAt;
                System.out.println(String.format(
                        "STATE_TRACE attempt=%d idx=%d elapsedMs=%d streaming=%s pending=%d messages=%d error=%s",
                        attemptNo,
                        index,
                        elapsed,
                        state.isStreaming(),
                        state.getPendingToolCalls().size(),
                        state.getMessages().size(),
                        state.getError()));
            });

            agent.subscribe(event -> {
                events.add(event);
                if (event instanceof ToolExecutionStartEvent) {
                    ToolExecutionStartEvent startEvent = (ToolExecutionStartEvent) event;
                    System.out.println("TOOL_START attempt=" + attemptNo + " name=" + startEvent.getToolName()
                            + " args=" + startEvent.getArgs());
                } else if (event instanceof ToolExecutionEndEvent) {
                    ToolExecutionEndEvent endEvent = (ToolExecutionEndEvent) event;
                    System.out.println("TOOL_END attempt=" + attemptNo + " name=" + endEvent.getToolName()
                            + " error=" + endEvent.isError() + " content=" + endEvent.getResult().getContent());
                } else if (event instanceof MessageUpdateEvent) {
                    AssistantMessageEvent assistantEvent =
                            ((MessageUpdateEvent) event).getAssistantMessageEvent();
                    if (assistantEvent instanceof TextDeltaEvent) {
                        String delta = ((TextDeltaEvent) assistantEvent).getDelta();
                        if (!delta.isEmpty()) {
                            streamedOutput.append(delta);
                            System.out.print(delta);
                        }
                    } else if (assistantEvent instanceof ToolCallDeltaEvent) {
                        String delta = ((ToolCallDeltaEvent) assistantEvent).getDelta();
                        if (!delta.isEmpty()) {
                            toolCallOutput.append(delta);
                            System.out.println("MODEL_TOOL_CALL_DELTA attempt=" + attemptNo + " delta=" + delta);
                        }
                    }
                } else if (event instanceof MessageEndEvent) {
                    AgentMessage message = ((MessageEndEvent) event).getMessage();
                    String summary = summarizeAssistantMessage(message);
                    if (!summary.isEmpty()) {
                        System.out.println("MODEL_TURN_END attempt=" + attemptNo + " " + summary);
                    }
                }
            });

            System.out.println("\n=== COMPLEX_AGENT_ATTEMPT " + attemptNo + " ===");
            agent.prompt(
                            "请按顺序完成复杂任务："
                                    + "1) 先调用 extract_numbers，payload='批次A数据: 12, 7, 31, 5, 19, 20'；"
                                    + "2) 再调用 calculate_statistics，numbers使用上一步结果中的numbers字段，metadata={source:'integration-test',batch:'A1'}；"
                                    + "3) 最后调用 render_final_report，title='批次A统计报告'，stats使用上一步统计对象，constraints=['中文','保留两位小数']；"
                                    + "最后只输出 render_final_report 返回的文本，不要添加其他说明。")
                    .get(180, TimeUnit.SECONDS);

            String finalOutput = extractLatestAssistantText(agent.getState().getMessages());
            System.out.println("\nMODEL_OUTPUT_STREAM=" + streamedOutput.toString());
            System.out.println("MODEL_TOOL_CALL_OUTPUT=" + toolCallOutput.toString());
            System.out.println("MODEL_OUTPUT_FINAL=" + finalOutput);

            Set<String> executedTools = executedToolNames(events);
            complexFlowSuccess = executedTools.contains("extract_numbers")
                    && executedTools.contains("calculate_statistics")
                    && executedTools.contains("render_final_report")
                    && sawPendingToolCall(states)
                    && sawStreamingState(states)
                    && endedInIdleState(states)
                    && finalOutput.contains("FINAL_REPORT|")
                    && !finalOutput.trim().isEmpty();
        }

        assertTrue(complexFlowSuccess);
    }

    private String summarizeAssistantMessage(AgentMessage agentMessage) {
        if (!(agentMessage instanceof LlmAgentMessage)) {
            return "";
        }
        Message message = ((LlmAgentMessage) agentMessage).getMessage();
        if (!(message instanceof AssistantMessage)) {
            return "";
        }

        List<String> texts = new ArrayList<String>();
        List<String> toolCalls = new ArrayList<String>();
        for (ContentBlock block : ((AssistantMessage) message).getContent()) {
            if (block instanceof TextContent) {
                String text = ((TextContent) block).getText();
                if (text != null && !text.trim().isEmpty()) {
                    texts.add(text.trim());
                }
            } else if (block instanceof ToolCallContent) {
                ToolCallContent toolCall = (ToolCallContent) block;
                toolCalls.add(toolCall.getName() + toolCall.getArguments());
            }
        }

        StringBuilder summary = new StringBuilder();
        if (!texts.isEmpty()) {
            summary.append("text=").append(String.join(" | ", texts));
        }
        if (!toolCalls.isEmpty()) {
            if (summary.length() > 0) {
                summary.append(" ; ");
            }
            summary.append("toolCalls=").append(toolCalls);
        }
        return summary.toString();
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

    private Set<String> executedToolNames(List<AgentEvent> events) {
        Set<String> toolNames = new LinkedHashSet<String>();
        for (AgentEvent event : events) {
            if (event instanceof ToolExecutionEndEvent) {
                toolNames.add(((ToolExecutionEndEvent) event).getToolName());
            }
        }
        return toolNames;
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
