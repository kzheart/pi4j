package com.pi4j.agent.tool;

@FunctionalInterface
public interface ToolUpdateCallback {
    void onUpdate(AgentToolResult partialResult);
}
