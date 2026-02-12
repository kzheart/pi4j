package com.pi4j.ai.stream;

public final class ToolCallStartEvent extends AssistantMessageEvent {
    private final int contentIndex;

    public ToolCallStartEvent(int contentIndex) {
        super("tool_call_start");
        this.contentIndex = contentIndex;
    }

    public int getContentIndex() {
        return contentIndex;
    }
}
