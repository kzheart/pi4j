package com.pi4j.agent.event;

import com.pi4j.agent.AgentMessage;

public final class MessageEndEvent extends AgentEvent {
    private final AgentMessage message;

    public MessageEndEvent(AgentMessage message) {
        super("message_end");
        this.message = message;
    }

    public AgentMessage getMessage() {
        return message;
    }
}
