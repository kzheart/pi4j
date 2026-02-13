package com.pi4j.examples;

import com.pi4j.agent.Agent;
import com.pi4j.agent.AgentOptions;
import com.pi4j.agent.event.MessageUpdateEvent;
import com.pi4j.ai.provider.ApiRegistry;
import com.pi4j.ai.stream.TextDeltaEvent;
import com.pi4j.examples.support.ExampleModels;
import com.pi4j.examples.support.SimpleTextProvider;

public final class EventListenerExample {
    private static final String API = "mock-event-api";

    private EventListenerExample() {
    }

    public static void main(String[] args) {
        ApiRegistry.clear();
        ApiRegistry.register(new SimpleTextProvider(API, "这是一段流式事件文本。"));

        Agent agent = new Agent(AgentOptions.builder()
                .systemPrompt("你是事件演示助手")
                .model(ExampleModels.mockModel("mock-event-model", API, "mock-event"))
                .getApiKey(provider -> "example-key")
                .build());

        agent.subscribe(event -> {
            if (event instanceof MessageUpdateEvent) {
                MessageUpdateEvent updateEvent = (MessageUpdateEvent) event;
                if (updateEvent.getAssistantMessageEvent() instanceof TextDeltaEvent) {
                    TextDeltaEvent deltaEvent = (TextDeltaEvent) updateEvent.getAssistantMessageEvent();
                    System.out.print(deltaEvent.getDelta());
                }
            }
        });

        agent.prompt("开始演示").join();
        System.out.println();
    }
}
