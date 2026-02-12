package com.pi4j.ai.types;

import java.util.Objects;

public abstract class ContentBlock {
    private final String type;

    protected ContentBlock(String type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public String getType() {
        return type;
    }
}
