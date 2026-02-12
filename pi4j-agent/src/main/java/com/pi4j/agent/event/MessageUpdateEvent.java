package com.pi4j.agent.event;

import com.pi4j.agent.AgentMessage;
import com.pi4j.ai.stream.AssistantMessageEvent;

public final class MessageUpdateEvent extends AgentEvent {
    private final AgentMessage message;
    private final AssistantMessageEvent assistantMessageEvent;

    public MessageUpdateEvent(AgentMessage message, AssistantMessageEvent assistantMessageEvent) {
        super("message_update");
        this.message = message;
        this.assistantMessageEvent = assistantMessageEvent;
    }

    public AgentMessage getMessage() {
        return message;
    }

    public AssistantMessageEvent getAssistantMessageEvent() {
        return assistantMessageEvent;
    }
}
