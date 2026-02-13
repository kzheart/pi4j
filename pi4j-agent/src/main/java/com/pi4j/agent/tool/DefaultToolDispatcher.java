package com.pi4j.agent.tool;

public final class DefaultToolDispatcher implements ToolDispatcher {
    @Override
    public AgentToolResult dispatch(ToolExecutionContext context, ToolInvocation invocation) throws Exception {
        return invocation.invoke();
    }
}
