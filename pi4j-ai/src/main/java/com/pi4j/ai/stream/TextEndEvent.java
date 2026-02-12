package com.pi4j.ai.stream;

public final class TextEndEvent extends AssistantMessageEvent {
    private final int contentIndex;

    public TextEndEvent(int contentIndex) {
        super("text_end");
        this.contentIndex = contentIndex;
    }

    public int getContentIndex() {
        return contentIndex;
    }
}
