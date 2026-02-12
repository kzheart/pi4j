package com.pi4j.ai.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ToolResultMessage extends Message {
    private final String toolCallId;
    private final String toolName;
    private final List<ContentBlock> content;
    private final Object details;
    private final boolean error;

    public ToolResultMessage(
            String toolCallId,
            String toolName,
            List<ContentBlock> content,
            Object details,
            boolean error) {
        this(toolCallId, toolName, content, details, error, System.currentTimeMillis());
    }

    public ToolResultMessage(
            String toolCallId,
            String toolName,
            List<ContentBlock> content,
            Object details,
            boolean error,
            long timestamp) {
        super("toolResult", timestamp);
        this.toolCallId = Objects.requireNonNull(toolCallId, "toolCallId");
        this.toolName = Objects.requireNonNull(toolName, "toolName");
        this.content = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(content, "content")));
        this.details = details;
        this.error = error;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public List<ContentBlock> getContent() {
        return content;
    }

    public Object getDetails() {
        return details;
    }

    public boolean isError() {
        return error;
    }
}
