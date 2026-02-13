package com.pi4j.examples;

import com.pi4j.agent.Agent;
import com.pi4j.agent.AgentMessage;
import com.pi4j.agent.AgentOptions;
import com.pi4j.agent.LlmAgentMessage;
import com.pi4j.ai.provider.ApiRegistry;
import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.TextContent;
import com.pi4j.examples.support.ExampleModels;
import com.pi4j.examples.support.SimpleTextProvider;
import java.util.List;

public final class BasicAgentExample {
    private static final String API = "mock-basic-api";

    private BasicAgentExample() {
    }

    public static void main(String[] args) {
        ApiRegistry.clear();
        ApiRegistry.register(new SimpleTextProvider(API, "你好，我是一个本地 mock Agent 示例。"));

        Agent agent = new Agent(AgentOptions.builder()
                .systemPrompt("你是示例助手")
                .model(ExampleModels.mockModel("mock-basic-model", API, "mock-basic"))
                .getApiKey(provider -> "example-key")
                .build());

        agent.prompt("你好").join();

        System.out.println(lastAssistantText(agent.getState().getMessages()));
    }

    private static String lastAssistantText(List<AgentMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentMessage message = messages.get(i);
            if (!(message instanceof LlmAgentMessage)) {
                continue;
            }
            if (!"assistant".equals(message.getRole())) {
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
