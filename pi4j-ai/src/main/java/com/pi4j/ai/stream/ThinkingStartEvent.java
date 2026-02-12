package com.pi4j.ai.stream;

public final class ThinkingStartEvent extends AssistantMessageEvent {
    private final int contentIndex;

    public ThinkingStartEvent(int contentIndex) {
        super("thinking_start");
        this.contentIndex = contentIndex;
    }

    public int getContentIndex() {
        return contentIndex;
    }
}
