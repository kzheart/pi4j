package com.pi4j.agent.tool;

import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.ImageContent;
import com.pi4j.ai.types.TextContent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AgentToolResult {
    private final List<ContentBlock> content;
    private final Object details;

    public AgentToolResult(List<ContentBlock> content, Object details) {
        this.content = Collections.unmodifiableList(new ArrayList<ContentBlock>(content));
        this.details = details;
    }

    public List<ContentBlock> getContent() {
        return content;
    }

    public Object getDetails() {
        return details;
    }

    public static AgentToolResult text(String text) {
        return new AgentToolResult(Collections.<ContentBlock>singletonList(new TextContent(text)), null);
    }

    public static AgentToolResult error(String errorMessage) {
        String message = errorMessage == null || errorMessage.trim().isEmpty() ? "Unknown error" : errorMessage;
        return new AgentToolResult(Collections.<ContentBlock>singletonList(new TextContent(message)), null);
    }

    public static AgentToolResult withImage(String text, String base64, String mimeType) {
        List<ContentBlock> blocks = new ArrayList<ContentBlock>();
        blocks.add(new TextContent(text));
        blocks.add(new ImageContent(base64, mimeType));
        return new AgentToolResult(blocks, null);
    }
}
