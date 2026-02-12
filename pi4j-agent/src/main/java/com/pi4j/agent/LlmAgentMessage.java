package com.pi4j.agent;

import com.pi4j.ai.types.Message;
import java.util.Objects;

public final class LlmAgentMessage extends AgentMessage {
    private final Message message;

    public LlmAgentMessage(Message message) {
        super(message.getRole(), message.getTimestamp());
        this.message = Objects.requireNonNull(message, "message");
    }

    public Message getMessage() {
        return message;
    }
}
