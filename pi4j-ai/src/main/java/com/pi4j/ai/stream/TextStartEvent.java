package com.pi4j.ai.stream;

public final class TextStartEvent extends AssistantMessageEvent {
    private final int contentIndex;

    public TextStartEvent(int contentIndex) {
        super("text_start");
        this.contentIndex = contentIndex;
    }

    public int getContentIndex() {
        return contentIndex;
    }
}
