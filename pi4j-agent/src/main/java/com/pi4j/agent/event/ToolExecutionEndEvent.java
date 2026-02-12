package com.pi4j.agent.event;

import com.pi4j.agent.tool.AgentToolResult;

public final class ToolExecutionEndEvent extends AgentEvent {
    private final String toolCallId;
    private final String toolName;
    private final AgentToolResult result;
    private final boolean error;

    public ToolExecutionEndEvent(String toolCallId, String toolName, AgentToolResult result, boolean error) {
        super("tool_execution_end");
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.result = result;
        this.error = error;
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

    public boolean isError() {
        return error;
    }
}
