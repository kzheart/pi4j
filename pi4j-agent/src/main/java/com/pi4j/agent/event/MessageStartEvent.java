package com.pi4j.agent.event;

import com.pi4j.agent.AgentMessage;

public final class MessageStartEvent extends AgentEvent {
    private final AgentMessage message;

    public MessageStartEvent(AgentMessage message) {
        super("message_start");
        this.message = message;
    }

    public AgentMessage getMessage() {
        return message;
    }
}
