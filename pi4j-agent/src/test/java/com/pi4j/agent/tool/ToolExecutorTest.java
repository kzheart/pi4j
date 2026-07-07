package com.pi4j.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.pi4j.ai.provider.AbortHandle;
import com.pi4j.ai.types.TextContent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ToolExecutorTest {

    @Test
    void middlewareOrderIsPredictable() throws Exception {
        List<String> trace = new ArrayList<String>();
        ToolMiddleware first = (context, chain) -> {
            trace.add("before-first");
            AgentToolResult result = chain.proceed(context);
            trace.add("after-first");
            return result;
        };
        ToolMiddleware second = (context, chain) -> {
            trace.add("before-second");
            AgentToolResult result = chain.proceed(context);
            trace.add("after-second");
            return result;
        };

        ToolDispatcher dispatcher = (context, invocation) -> {
            trace.add("dispatcher");
            return invocation.invoke();
        };
        AgentTool tool = tool(() -> {
            trace.add("tool");
            return AgentToolResult.text("ok");
        });
        ToolExecutor executor = new ToolExecutor(dispatcher, Arrays.asList(first, second));

        executor.execute(context(tool));
        assertEquals(
                Arrays.asList("before-first", "before-second", "dispatcher", "tool", "after-second", "after-first"),
                trace);
    }

    @Test
    void confirmationMiddlewareBlocksUnconfirmedTool() {
        ToolExecutor executor = new ToolExecutor(new DefaultToolDispatcher(), Collections.singletonList(DefaultMiddlewares.confirmation()));
        AgentTool tool = tool(() -> AgentToolResult.text("ok"));
        ToolExecutionContext context = context(tool).requireConfirmation(true);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> executor.execute(context));
        assertTrue(exception.getMessage().contains("confirmation rejected"));
    }

    @Test
    void errorAndTimeoutMiddlewaresMapFailureToResult() throws Exception {
        ToolExecutor executor = new ToolExecutor(
                new DefaultToolDispatcher(),
                Arrays.asList(DefaultMiddlewares.error(), DefaultMiddlewares.timeout(10L)));
        AgentTool tool = tool(() -> {
            try {
                Thread.sleep(80L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
            return AgentToolResult.text("late");
        });

        AgentToolResult result = executor.execute(context(tool));
        assertTrue(((TextContent) result.getContent().get(0)).getText().contains("timed out"));
    }

    @Test
    void agentToolResultErrorFlag() {
        assertTrue(AgentToolResult.error("x").isError());
        assertFalse(AgentToolResult.text("x").isError());
    }

    @Test
    void executionPolicyAppliesConfirmationRequired() throws Exception {
        AtomicBoolean gateCalled = new AtomicBoolean();
        ConfirmationGate gate = context -> {
            gateCalled.set(true);
            return CompletableFuture.completedFuture(ConfirmationGate.Decision.APPROVED);
        };
        ToolExecutor executor = new ToolExecutor(
                new DefaultToolDispatcher(),
                Collections.singletonList(DefaultMiddlewares.confirmation(gate, 0L)));
        AtomicBoolean toolExecuted = new AtomicBoolean();
        AgentTool confirmingTool = toolWithPolicy(
                ToolExecutionPolicy.builder().confirmationRequired(true).build(),
                () -> {
                    toolExecuted.set(true);
                    return AgentToolResult.text("ok");
                });

        AgentToolResult result = executor.execute(context(confirmingTool));
        assertTrue(gateCalled.get());
        assertTrue(toolExecuted.get());
        assertEquals("ok", ((TextContent) result.getContent().get(0)).getText());
    }

    @Test
    void defaultPolicyToolDoesNotInvokeGate() throws Exception {
        AtomicBoolean gateCalled = new AtomicBoolean();
        ConfirmationGate gate = context -> {
            gateCalled.set(true);
            return CompletableFuture.completedFuture(ConfirmationGate.Decision.APPROVED);
        };
        ToolExecutor executor = new ToolExecutor(
                new DefaultToolDispatcher(),
                Collections.singletonList(DefaultMiddlewares.confirmation(gate, 0L)));
        AgentTool defaultTool = tool(() -> AgentToolResult.text("ok"));

        executor.execute(context(defaultTool));
        assertFalse(gateCalled.get());
    }

    @Test
    void gateDeniedReturnsErrorWithoutExecutingTool() throws Exception {
        ConfirmationGate gate = context -> CompletableFuture.completedFuture(ConfirmationGate.Decision.DENIED);
        ToolExecutor executor = new ToolExecutor(
                new DefaultToolDispatcher(),
                Collections.singletonList(DefaultMiddlewares.confirmation(gate, 0L)));
        AtomicBoolean toolExecuted = new AtomicBoolean();
        AgentTool confirmingTool = toolWithPolicy(
                ToolExecutionPolicy.builder().confirmationRequired(true).build(),
                () -> {
                    toolExecuted.set(true);
                    return AgentToolResult.text("ok");
                });

        AgentToolResult result = executor.execute(context(confirmingTool));
        assertFalse(toolExecuted.get());
        assertTrue(result.isError());
        assertTrue(((TextContent) result.getContent().get(0)).getText().contains("denied"));
    }

    @Test
    void gateTimeoutReturnsErrorResult() throws Exception {
        ConfirmationGate gate = context -> new CompletableFuture<ConfirmationGate.Decision>();
        ToolExecutor executor = new ToolExecutor(
                new DefaultToolDispatcher(),
                Collections.singletonList(DefaultMiddlewares.confirmation(gate, 200L)));
        AtomicBoolean toolExecuted = new AtomicBoolean();
        AgentTool confirmingTool = toolWithPolicy(
                ToolExecutionPolicy.builder().confirmationRequired(true).build(),
                () -> {
                    toolExecuted.set(true);
                    return AgentToolResult.text("ok");
                });

        AgentToolResult result = executor.execute(context(confirmingTool));
        assertFalse(toolExecuted.get());
        assertTrue(result.isError());
        assertTrue(((TextContent) result.getContent().get(0)).getText().contains("confirmation timed out"));
    }

    @Test
    void gateAbortBeforeExecutionReturnsDeniedError() throws Exception {
        ConfirmationGate gate = context -> new CompletableFuture<ConfirmationGate.Decision>();
        ToolExecutor executor = new ToolExecutor(
                new DefaultToolDispatcher(),
                Collections.singletonList(DefaultMiddlewares.confirmation(gate, 0L)));
        AtomicBoolean toolExecuted = new AtomicBoolean();
        AgentTool confirmingTool = toolWithPolicy(
                ToolExecutionPolicy.builder().confirmationRequired(true).build(),
                () -> {
                    toolExecuted.set(true);
                    return AgentToolResult.text("ok");
                });
        AbortHandle abortHandle = new AbortHandle();
        abortHandle.abort();

        AgentToolResult result = executor.execute(context(confirmingTool, abortHandle));
        assertFalse(toolExecuted.get());
        assertTrue(result.isError());
        assertTrue(((TextContent) result.getContent().get(0)).getText().contains("denied"));
    }

    @Test
    void attributeKeyTypedAccessInteroperatesWithStringKeys() {
        AttributeKey<String> key = AttributeKey.of("player-id");
        ToolExecutionContext context = context(tool(() -> AgentToolResult.text("ok")))
                .withAttribute(key, "player-42");

        assertEquals("player-42", context.getAttribute(key));
        assertEquals("player-42", context.getAttribute("player-id"));
    }

    @Test
    void blockingDispatchModeUsesWorkerThread() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            ToolExecutor executor = new ToolExecutor(new DefaultToolDispatcher(executorService, null), Collections.<ToolMiddleware>emptyList());
            long callerThread = Thread.currentThread().getId();
            AtomicReference<Long> toolThread = new AtomicReference<Long>();
            AgentTool tool = toolWithPolicy(
                    ToolExecutionPolicy.builder().dispatchMode(ToolDispatchMode.BLOCKING).build(),
                    () -> {
                        toolThread.set(Thread.currentThread().getId());
                        return AgentToolResult.text("ok");
                    });

            ToolExecutionContext context = context(tool);
            executor.execute(context);
            assertNotEquals(callerThread, toolThread.get().longValue());
        } finally {
            executorService.shutdownNow();
        }
    }

    private ToolExecutionContext context(AgentTool tool) {
        return context(tool, new AbortHandle());
    }

    private ToolExecutionContext context(AgentTool tool, AbortHandle abortHandle) {
        return new ToolExecutionContext(
                "call_1",
                "demo",
                tool,
                Collections.<String, Object>emptyMap(),
                abortHandle,
                update -> {
                });
    }

    private AgentTool toolWithPolicy(final ToolExecutionPolicy policy, final ToolAction action) {
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
            public ToolExecutionPolicy getExecutionPolicy() {
                return policy;
            }

            @Override
            public AgentToolResult execute(
                    String toolCallId,
                    Map<String, Object> params,
                    AbortHandle abortHandle,
                    ToolUpdateCallback onUpdate) {
                return action.run();
            }
        };
    }

    private AgentTool tool(ToolAction action) {
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
                return action.run();
            }
        };
    }

    private interface ToolAction {
        AgentToolResult run();
    }
}
