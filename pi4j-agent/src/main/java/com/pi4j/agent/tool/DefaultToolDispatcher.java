package com.pi4j.agent.tool;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class DefaultToolDispatcher implements ToolDispatcher {
    private final ExecutorService blockingExecutor;
    private final ToolDispatcher hostDispatcher;

    public DefaultToolDispatcher() {
        this(Executors.newCachedThreadPool(), null);
    }

    public DefaultToolDispatcher(ExecutorService blockingExecutor, ToolDispatcher hostDispatcher) {
        if (blockingExecutor == null) {
            throw new IllegalArgumentException("blockingExecutor is required.");
        }
        this.blockingExecutor = blockingExecutor;
        this.hostDispatcher = hostDispatcher;
    }

    @Override
    public AgentToolResult dispatch(ToolExecutionContext context, ToolInvocation invocation) throws Exception {
        context.getAbortHandle().throwIfAborted();
        if (context.getDispatchMode() == ToolDispatchMode.DIRECT) {
            return invocation.invoke();
        }
        if (context.getDispatchMode() == ToolDispatchMode.BLOCKING) {
            return dispatchBlocking(invocation);
        }
        if (hostDispatcher == null) {
            throw new IllegalStateException("HOST_DISPATCHER mode requires a host dispatcher.");
        }
        return hostDispatcher.dispatch(context, invocation);
    }

    private AgentToolResult dispatchBlocking(final ToolInvocation invocation) throws Exception {
        Future<AgentToolResult> future = blockingExecutor.submit(new Callable<AgentToolResult>() {
            @Override
            public AgentToolResult call() throws Exception {
                return invocation.invoke();
            }
        });
        try {
            return future.get();
        } catch (InterruptedException interruptedException) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw interruptedException;
        } catch (ExecutionException executionException) {
            Throwable cause = executionException.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new IllegalStateException("Unexpected tool dispatch failure.", cause);
        }
    }
}
