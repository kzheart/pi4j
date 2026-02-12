package com.pi4j.agent.event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolExecutionStartEvent extends AgentEvent {
    private final String toolCallId;
    private final String toolName;
    private final Map<String, Object> args;

    public ToolExecutionStartEvent(String toolCallId, String toolName, Map<String, Object> args) {
        super("tool_execution_start");
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.args = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(args));
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public Map<String, Object> getArgs() {
        return args;
    }
}
