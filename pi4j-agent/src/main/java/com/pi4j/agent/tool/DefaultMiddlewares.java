package com.pi4j.agent.tool;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class DefaultMiddlewares {
    public static final String CONFIRMED_ATTRIBUTE = "confirmed";

    private static final ExecutorService TIMEOUT_EXECUTOR = Executors.newCachedThreadPool();

    private DefaultMiddlewares() {
    }

    public static ToolMiddleware confirmation() {
        return confirmation(new ToolConfirmation() {
            @Override
            public boolean confirm(ToolExecutionContext context) {
                if (!context.isConfirmationRequired()) {
                    return true;
                }
                return Boolean.TRUE.equals(context.getAttribute(CONFIRMED_ATTRIBUTE));
            }
        });
    }

    public static ToolMiddleware confirmation(final ToolConfirmation confirmation) {
        return new ToolMiddleware() {
            @Override
            public AgentToolResult handle(ToolExecutionContext context, ToolExecutionChain chain) throws Exception {
                if (!confirmation.confirm(context)) {
                    throw new IllegalStateException("Tool confirmation rejected: " + context.getToolName());
                }
                return chain.proceed(context);
            }
        };
    }

    public static ToolMiddleware timeout() {
        return timeout(0L);
    }

    public static ToolMiddleware timeout(final long defaultTimeoutMillis) {
        return new ToolMiddleware() {
            @Override
            public AgentToolResult handle(final ToolExecutionContext context, final ToolExecutionChain chain) throws Exception {
                long timeoutMillis = context.getTimeoutMillis() > 0L ? context.getTimeoutMillis() : defaultTimeoutMillis;
                if (timeoutMillis <= 0L) {
                    return chain.proceed(context);
                }
                Future<AgentToolResult> future = TIMEOUT_EXECUTOR.submit(new Callable<AgentToolResult>() {
                    @Override
                    public AgentToolResult call() throws Exception {
                        return chain.proceed(context);
                    }
                });
                try {
                    return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
                } catch (TimeoutException timeoutException) {
                    future.cancel(true);
                    throw new IllegalStateException(
                            "Tool execution timed out after " + timeoutMillis + "ms: " + context.getToolName(),
                            timeoutException);
                } catch (InterruptedException interruptedException) {
                    future.cancel(true);
                    Thread.currentThread().interrupt();
                    throw interruptedException;
                } catch (ExecutionException executionException) {
                    Throwable cause = executionException.getCause();
                    if (cause instanceof Exception) {
                        throw (Exception) cause;
                    }
                    throw new IllegalStateException("Unexpected middleware timeout failure.", cause);
                }
            }
        };
    }

    public static ToolMiddleware error() {
        return error(new ErrorMessageMapper() {
            @Override
            public String map(Throwable error, ToolExecutionContext context) {
                if (error.getMessage() != null && !error.getMessage().trim().isEmpty()) {
                    return error.getMessage();
                }
                return error.toString();
            }
        });
    }

    public static ToolMiddleware error(final ErrorMessageMapper mapper) {
        return new ToolMiddleware() {
            @Override
            public AgentToolResult handle(ToolExecutionContext context, ToolExecutionChain chain) {
                try {
                    return chain.proceed(context);
                } catch (Throwable throwable) {
                    return AgentToolResult.error(mapper.map(throwable, context));
                }
            }
        };
    }

    public static ToolMiddleware retry() {
        return retry(0);
    }

    public static ToolMiddleware retry(final int defaultRetries) {
        return new ToolMiddleware() {
            @Override
            public AgentToolResult handle(ToolExecutionContext context, ToolExecutionChain chain) throws Exception {
                int retries = context.getMaxRetries() > 0 ? context.getMaxRetries() : defaultRetries;
                int attempts = 0;
                while (true) {
                    try {
                        return chain.proceed(context);
                    } catch (Exception exception) {
                        if (attempts >= retries) {
                            throw exception;
                        }
                        attempts++;
                    }
                }
            }
        };
    }
}
