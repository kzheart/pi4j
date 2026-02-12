package com.pi4j.ai.stream;

import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.StopReason;
import java.util.Objects;

public final class ErrorEvent extends AssistantMessageEvent {
    private final StopReason reason;
    private final AssistantMessage error;

    public ErrorEvent(StopReason reason, AssistantMessage error) {
        super("error");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.error = Objects.requireNonNull(error, "error");
    }

    public StopReason getReason() {
        return reason;
    }

    public AssistantMessage getError() {
        return error;
    }
}
