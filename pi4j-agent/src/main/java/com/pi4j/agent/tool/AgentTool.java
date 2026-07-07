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

    /** 本工具的执行策略；默认全默认值。实现方覆写以声明分发模式、确认要求、超时与重试。 */
    default ToolExecutionPolicy getExecutionPolicy() {
        return ToolExecutionPolicy.DEFAULT;
    }
}
