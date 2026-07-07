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
    private final boolean error;

    public AgentToolResult(List<ContentBlock> content, Object details) {
        this(content, details, false);
    }

    public AgentToolResult(List<ContentBlock> content, Object details, boolean error) {
        this.content = Collections.unmodifiableList(new ArrayList<ContentBlock>(content));
        this.details = details;
        this.error = error;
    }

    public List<ContentBlock> getContent() {
        return content;
    }

    public Object getDetails() {
        return details;
    }

    /** 该结果是否为错误结果；会映射为 LLM 协议里 tool result 的 is_error 标记。 */
    public boolean isError() {
        return error;
    }

    public static AgentToolResult text(String text) {
        return new AgentToolResult(Collections.<ContentBlock>singletonList(new TextContent(text)), null, false);
    }

    public static AgentToolResult error(String errorMessage) {
        String message = errorMessage == null || errorMessage.trim().isEmpty() ? "Unknown error" : errorMessage;
        return new AgentToolResult(Collections.<ContentBlock>singletonList(new TextContent(message)), null, true);
    }

    public static AgentToolResult withImage(String text, String base64, String mimeType) {
        List<ContentBlock> blocks = new ArrayList<ContentBlock>();
        blocks.add(new TextContent(text));
        blocks.add(new ImageContent(base64, mimeType));
        return new AgentToolResult(blocks, null, false);
    }
}