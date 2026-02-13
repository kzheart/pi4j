package com.pi4j.agent.tool;

public interface ToolMiddleware {
    AgentToolResult handle(ToolExecutionContext context, ToolExecutionChain chain) throws Exception;
}
