package com.pi4j.agent.event;

import com.pi4j.agent.tool.AgentToolResult;

public final class ToolExecutionUpdateEvent extends AgentEvent {
    private final String toolCallId;
    private final String toolName;
    private final AgentToolResult result;

    public ToolExecutionUpdateEvent(String toolCallId, String toolName, AgentToolResult result) {
        super("tool_execution_update");
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.result = result;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public AgentToolResult getResult() {
        return result;
    }
}
