package com.pi4j.ai.stream;

import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.StopReason;
import java.util.Objects;

public final class DoneEvent extends AssistantMessageEvent {
    private final StopReason reason;
    private final AssistantMessage message;

    public DoneEvent(StopReason reason, AssistantMessage message) {
        super("done");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.message = Objects.requireNonNull(message, "message");
    }

    public StopReason getReason() {
        return reason;
    }

    public AssistantMessage getMessage() {
        return message;
    }
}
