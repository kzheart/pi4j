package com.pi4j.ai.stream;

public final class ThinkingEndEvent extends AssistantMessageEvent {
    private final int contentIndex;

    public ThinkingEndEvent(int contentIndex) {
        super("thinking_end");
        this.contentIndex = contentIndex;
    }

    public int getContentIndex() {
        return contentIndex;
    }
}
