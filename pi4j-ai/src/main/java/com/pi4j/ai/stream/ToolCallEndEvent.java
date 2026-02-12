package com.pi4j.ai.stream;

import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.ToolCallContent;
import java.util.Objects;

public final class ToolCallEndEvent extends AssistantMessageEvent {
    private final int contentIndex;
    private final ToolCallContent toolCall;
    private final AssistantMessage partial;

    public ToolCallEndEvent(int contentIndex, ToolCallContent toolCall, AssistantMessage partial) {
        super("tool_call_end");
        this.contentIndex = contentIndex;
        this.toolCall = Objects.requireNonNull(toolCall, "toolCall");
        this.partial = partial;
    }

    public int getContentIndex() {
        return contentIndex;
    }

    public ToolCallContent getToolCall() {
        return toolCall;
    }

    public AssistantMessage getPartial() {
        return partial;
    }
}
