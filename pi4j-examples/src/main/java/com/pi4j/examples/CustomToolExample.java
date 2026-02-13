package com.pi4j.examples;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pi4j.agent.Agent;
import com.pi4j.agent.AgentMessage;
import com.pi4j.agent.AgentOptions;
import com.pi4j.agent.LlmAgentMessage;
import com.pi4j.agent.tool.AgentTool;
import com.pi4j.agent.tool.AgentToolResult;
import com.pi4j.agent.tool.ToolUpdateCallback;
import com.pi4j.ai.provider.AbortHandle;
import com.pi4j.ai.provider.ApiRegistry;
import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.TextContent;
import com.pi4j.examples.support.ExampleModels;
import com.pi4j.examples.support.ToolCallProvider;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class CustomToolExample {
    private static final String API = "mock-tool-api";

    private CustomToolExample() {
    }

    public static void main(String[] args) {
        ApiRegistry.clear();
        ApiRegistry.register(new ToolCallProvider(API));

        AgentTool getTimeTool = new AgentTool() {
            @Override
            public String getName() {
                return "get_time";
            }

            @Override
            public String getDescription() {
                return "查询指定时区当前时间";
            }

            @Override
            public String getLabel() {
                return "Get Time";
            }

            @Override
            public JsonObject getParameters() {
                JsonObject schema = new JsonObject();
                schema.addProperty("type", "object");

                JsonObject properties = new JsonObject();
                JsonObject zone = new JsonObject();
                zone.addProperty("type", "string");
                properties.add("zone", zone);

                schema.add("properties", properties);
                JsonArray required = new JsonArray();
                required.add("zone");
                schema.add("required", required);
                return schema;
            }

            @Override
            public AgentToolResult execute(
                    String toolCallId,
                    Map<String, Object> params,
                    AbortHandle abortHandle,
                    ToolUpdateCallback onUpdate) {
                String zone = String.valueOf(params.get("zone"));
                ZonedDateTime now = ZonedDateTime.now(ZoneId.of(zone));
                return AgentToolResult.text(now.toString());
            }
        };

        Agent agent = new Agent(AgentOptions.builder()
                .systemPrompt("你是工具调用示例助手")
                .model(ExampleModels.mockModel("mock-tool-model", API, "mock-tool"))
                .tools(Collections.singletonList(getTimeTool))
                .getApiKey(provider -> "example-key")
                .build());

        agent.prompt("现在上海时间是多少?").join();
        System.out.println(lastAssistantText(agent.getState().getMessages()));
    }

    private static String lastAssistantText(List<AgentMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentMessage message = messages.get(i);
            if (!(message instanceof LlmAgentMessage) || !"assistant".equals(message.getRole())) {
                continue;
            }
            AssistantMessage assistant = (AssistantMessage) ((LlmAgentMessage) message).getMessage();
            for (ContentBlock block : assistant.getContent()) {
                if (block instanceof TextContent) {
                    return ((TextContent) block).getText();
                }
            }
        }
        return "";
    }
}
