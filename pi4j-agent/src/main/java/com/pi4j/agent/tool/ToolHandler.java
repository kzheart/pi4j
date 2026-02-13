package com.pi4j.agent.tool;

import com.pi4j.ai.provider.AbortHandle;

public interface ToolHandler {
    AgentToolResult handle(String toolCallId, ToolArgs args, AbortHandle abortHandle, ToolUpdateCallback onUpdate);
}
