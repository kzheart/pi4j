package com.pi4j.agent.tool;

public interface ToolExecutionChain {
    AgentToolResult proceed(ToolExecutionContext context) throws Exception;
}
