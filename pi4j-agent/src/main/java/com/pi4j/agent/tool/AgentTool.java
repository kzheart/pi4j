package com.pi4j.agent.tool;

import com.google.gson.JsonObject;
import com.pi4j.ai.provider.AbortHandle;
import java.util.Map;

public interface AgentTool {
    String getName();

    String getDescription();

    String getLabel();

    JsonObject getParameters();

    AgentToolResult execute(
            String toolCallId,
            Map<String, Object> params,
            AbortHandle abortHandle,
            ToolUpdateCallback onUpdate);
}
