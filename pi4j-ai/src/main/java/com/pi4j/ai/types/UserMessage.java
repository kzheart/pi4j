package com.pi4j.ai.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class UserMessage extends Message {
    private final List<ContentBlock> content;

    public UserMessage(List<ContentBlock> content) {
        this(content, System.currentTimeMillis());
    }

    public UserMessage(List<ContentBlock> content, long timestamp) {
        super("user", timestamp);
        this.content = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(content, "content")));
    }

    public List<ContentBlock> getContent() {
        return content;
    }
}
