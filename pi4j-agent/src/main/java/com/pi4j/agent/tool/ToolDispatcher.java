package com.pi4j.agent.tool;

public interface ToolDispatcher {
    AgentToolResult dispatch(ToolExecutionContext context, ToolInvocation invocation) throws Exception;
}
