package com.pi4j.ai.types;

import java.util.Objects;

public final class TextContent extends ContentBlock {
    private final String text;
    private final String textSignature;

    public TextContent(String text) {
        this(text, null);
    }

    public TextContent(String text, String textSignature) {
        super("text");
        this.text = Objects.requireNonNull(text, "text");
        this.textSignature = textSignature;
    }

    public String getText() {
        return text;
    }

    public String getTextSignature() {
        return textSignature;
    }
}
