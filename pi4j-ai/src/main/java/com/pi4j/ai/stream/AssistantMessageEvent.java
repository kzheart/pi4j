package com.pi4j.ai.stream;

import java.util.Objects;

public abstract class AssistantMessageEvent {
    private final String type;

    protected AssistantMessageEvent(String type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public String getType() {
        return type;
    }
}
