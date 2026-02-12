package com.pi4j.ai.types;

import java.util.Objects;

public final class ThinkingContent extends ContentBlock {
    private final String thinking;
    private final String thinkingSignature;

    public ThinkingContent(String thinking) {
        this(thinking, null);
    }

    public ThinkingContent(String thinking, String thinkingSignature) {
        super("thinking");
        this.thinking = Objects.requireNonNull(thinking, "thinking");
        this.thinkingSignature = thinkingSignature;
    }

    public String getThinking() {
        return thinking;
    }

    public String getThinkingSignature() {
        return thinkingSignature;
    }
}
