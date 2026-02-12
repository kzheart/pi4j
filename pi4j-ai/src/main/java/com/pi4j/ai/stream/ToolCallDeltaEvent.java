package com.pi4j.ai.stream;

import com.pi4j.ai.types.AssistantMessage;
import java.util.Objects;

public final class ToolCallDeltaEvent extends AssistantMessageEvent {
    private final int contentIndex;
    private final String delta;
    private final AssistantMessage partial;

    public ToolCallDeltaEvent(int contentIndex, String delta, AssistantMessage partial) {
        super("tool_call_delta");
        this.contentIndex = contentIndex;
        this.delta = Objects.requireNonNull(delta, "delta");
        this.partial = partial;
    }

    public int getContentIndex() {
        return contentIndex;
    }

    public String getDelta() {
        return delta;
    }

    public AssistantMessage getPartial() {
        return partial;
    }
}
