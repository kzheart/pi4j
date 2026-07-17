package com.pi4j.ai.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An environment-originated observation injected into an agent conversation.
 * Providers that do not expose a native observation role receive a clearly
 * labelled user-message representation through {@code MessageTransformer}.
 */
public final class ObservationMessage extends Message {
    private final String source;
    private final List<ContentBlock> content;

    public ObservationMessage(String source, List<ContentBlock> content) {
        this(source, content, System.currentTimeMillis());
    }

    public ObservationMessage(String source, List<ContentBlock> content, long timestamp) {
        super("observation", timestamp);
        this.source = normalizeSource(source);
        this.content = Collections.unmodifiableList(new ArrayList<ContentBlock>(
                Objects.requireNonNull(content, "content")));
    }

    public String getSource() {
        return source;
    }

    public List<ContentBlock> getContent() {
        return content;
    }

    private static String normalizeSource(String source) {
        String normalized = Objects.requireNonNull(source, "source").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        return normalized;
    }
}
