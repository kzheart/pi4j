package com.pi4j.agent.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ToolExecutor {
    private final ToolDispatcher dispatcher;
    private final List<ToolMiddleware> middlewares;

    public ToolExecutor(ToolDispatcher dispatcher, List<ToolMiddleware> middlewares) {
        this.dispatcher = dispatcher;
        this.middlewares = Collections.unmodifiableList(new ArrayList<ToolMiddleware>(middlewares));
    }

    public AgentToolResult execute(ToolExecutionContext context) throws Exception {
        context = applyPolicy(context);
        ToolExecutionChain chain = terminalChain();
        for (int index = middlewares.size() - 1; index >= 0; index--) {
            final ToolMiddleware middleware = middlewares.get(index);
            final ToolExecutionChain next = chain;
            chain = new ToolExecutionChain() {
                @Override
                public AgentToolResult proceed(ToolExecutionContext currentContext) throws Exception {
                    return middleware.handle(currentContext, next);
                }
            };
        }
        return chain.proceed(context);
    }

    private ToolExecutionContext applyPolicy(ToolExecutionContext context) {
        AgentTool tool = context.getTool();
        if (tool == null) {
            return context;
        }
        ToolExecutionPolicy policy = tool.getExecutionPolicy();
        if (policy == null) {
            return context;
        }
        ToolExecutionContext applied = context.withDispatchMode(policy.getDispatchMode());
        if (policy.isConfirmationRequired()) {
            applied = applied.requireConfirmation(true);
        }
        if (policy.getTimeoutMillis() > 0L) {
            applied = applied.withTimeoutMillis(policy.getTimeoutMillis());
        }
        if (policy.getMaxRetries() > 0) {
            applied = applied.withMaxRetries(policy.getMaxRetries());
        }
        return applied;
    }

    private ToolExecutionChain terminalChain() {
        return new ToolExecutionChain() {
            @Override
            public AgentToolResult proceed(final ToolExecutionContext context) throws Exception {
                return dispatcher.dispatch(context, new ToolInvocation() {
                    @Override
                    public AgentToolResult invoke() {
                        return context.getTool().execute(
                                context.getToolCallId(),
                                context.getParams(),
                                context.getAbortHandle(),
                                context.getOnUpdate());
                    }
                });
            }
        };
    }
}
