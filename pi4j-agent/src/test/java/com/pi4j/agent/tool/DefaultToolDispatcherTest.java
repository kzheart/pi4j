package com.pi4j.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.pi4j.ai.provider.AbortHandle;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DefaultToolDispatcherTest {

    @Test
    void directModeInvokesOnCallerThread() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            DefaultToolDispatcher dispatcher = new DefaultToolDispatcher(executor, null);
            ToolExecutionContext context = baseContext().withDispatchMode(ToolDispatchMode.DIRECT);

            long callerThreadId = Thread.currentThread().getId();
            AtomicReference<Long> invocationThreadId = new AtomicReference<Long>();
            AgentToolResult result = dispatcher.dispatch(context, new ToolInvocation() {
                @Override
                public AgentToolResult invoke() {
                    invocationThreadId.set(Thread.currentThread().getId());
                    return AgentToolResult.text("ok");
                }
            });

            assertEquals("ok", ((com.pi4j.ai.types.TextContent) result.getContent().get(0)).getText());
            assertEquals(callerThreadId, invocationThreadId.get().longValue());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void blockingModeRunsOnExecutorThread() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            DefaultToolDispatcher dispatcher = new DefaultToolDispatcher(executor, null);
            ToolExecutionContext context = baseContext().withDispatchMode(ToolDispatchMode.BLOCKING);

            long callerThreadId = Thread.currentThread().getId();
            AtomicReference<Long> invocationThreadId = new AtomicReference<Long>();
            dispatcher.dispatch(context, new ToolInvocation() {
                @Override
                public AgentToolResult invoke() {
                    invocationThreadId.set(Thread.currentThread().getId());
                    return AgentToolResult.text("ok");
                }
            });

            assertNotEquals(callerThreadId, invocationThreadId.get().longValue());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void hostDispatcherModeDelegatesToConfiguredDispatcher() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AtomicReference<String> marker = new AtomicReference<String>("none");
            ToolDispatcher hostDispatcher = new ToolDispatcher() {
                @Override
                public AgentToolResult dispatch(ToolExecutionContext context, ToolInvocation invocation) {
                    marker.set("host");
                    return AgentToolResult.text("host-result");
                }
            };
            DefaultToolDispatcher dispatcher = new DefaultToolDispatcher(executor, hostDispatcher);
            ToolExecutionContext context = baseContext().withDispatchMode(ToolDispatchMode.HOST_DISPATCHER);

            AgentToolResult result = dispatcher.dispatch(context, new ToolInvocation() {
                @Override
                public AgentToolResult invoke() {
                    marker.set("invocation");
                    return AgentToolResult.text("should-not-run");
                }
            });

            assertEquals("host", marker.get());
            assertEquals("host-result", ((com.pi4j.ai.types.TextContent) result.getContent().get(0)).getText());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void hostDispatcherModeWithoutConfiguredDispatcherThrows() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            DefaultToolDispatcher dispatcher = new DefaultToolDispatcher(executor, null);
            ToolExecutionContext context = baseContext().withDispatchMode(ToolDispatchMode.HOST_DISPATCHER);

            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> dispatcher.dispatch(context, () -> AgentToolResult.text("x")));
            assertTrue(exception.getMessage().contains("requires a host dispatcher"));
        } finally {
            executor.shutdownNow();
        }
    }

    private ToolExecutionContext baseContext() {
        return new ToolExecutionContext(
                "call_1",
                "demo",
                testTool(),
                Collections.<String, Object>emptyMap(),
                new AbortHandle(),
                result -> {
                });
    }

    private AgentTool testTool() {
        return new AgentTool() {
            @Override
            public String getName() {
                return "demo";
            }

            @Override
            public String getDescription() {
                return "demo";
            }

            @Override
            public String getLabel() {
                return "demo";
            }

            @Override
            public JsonObject getParameters() {
                return new JsonObject();
            }

            @Override
            public AgentToolResult execute(
                    String toolCallId,
                    Map<String, Object> params,
                    AbortHandle abortHandle,
                    ToolUpdateCallback onUpdate) {
                return AgentToolResult.text("ok");
            }
        };
    }
}
