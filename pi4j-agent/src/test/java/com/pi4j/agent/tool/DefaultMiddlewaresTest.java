package com.pi4j.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.pi4j.ai.provider.AbortHandle;
import com.pi4j.ai.types.TextContent;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DefaultMiddlewaresTest {

    @Test
    void confirmationRejectsWhenRequiredButNotConfirmed() {
        ToolMiddleware middleware = DefaultMiddlewares.confirmation();
        ToolExecutionContext context = baseContext().requireConfirmation(true);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> middleware.handle(context, c -> AgentToolResult.text("ok")));
        assertTrue(exception.getMessage().contains("confirmation rejected"));
    }

    @Test
    void confirmationPassesWhenConfirmedAttributeIsTrue() throws Exception {
        ToolMiddleware middleware = DefaultMiddlewares.confirmation();
        ToolExecutionContext context = baseContext()
                .requireConfirmation(true)
                .withAttribute(DefaultMiddlewares.CONFIRMED_ATTRIBUTE, Boolean.TRUE);

        AgentToolResult result = middleware.handle(context, c -> AgentToolResult.text("ok"));
        assertEquals("ok", ((TextContent) result.getContent().get(0)).getText());
    }

    @Test
    void timeoutThrowsWhenExecutionExceedsLimit() {
        ToolMiddleware middleware = DefaultMiddlewares.timeout();
        ToolExecutionContext context = baseContext().withTimeoutMillis(20L);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> middleware.handle(context, c -> {
            Thread.sleep(100L);
            return AgentToolResult.text("slow");
        }));
        assertTrue(exception.getMessage().contains("timed out"));
    }

    @Test
    void errorMiddlewareMapsExceptionToErrorResult() throws Exception {
        ToolMiddleware middleware = DefaultMiddlewares.error();
        ToolExecutionContext context = baseContext();

        AgentToolResult result = middleware.handle(context, c -> {
            throw new IllegalArgumentException("bad-input");
        });
        assertEquals("bad-input", ((TextContent) result.getContent().get(0)).getText());
    }

    @Test
    void gateConfirmationApprovesAndProceeds() throws Exception {
        AtomicBoolean gateCalled = new AtomicBoolean();
        ConfirmationGate gate = context -> {
            gateCalled.set(true);
            return CompletableFuture.completedFuture(ConfirmationGate.Decision.APPROVED);
        };
        ToolMiddleware middleware = DefaultMiddlewares.confirmation(gate, 0L);
        ToolExecutionContext context = baseContext().requireConfirmation(true);

        AgentToolResult result = middleware.handle(context, c -> AgentToolResult.text("ok"));
        assertTrue(gateCalled.get());
        assertEquals("ok", ((TextContent) result.getContent().get(0)).getText());
    }

    @Test
    void gateConfirmationSkipsGateWhenNotRequired() throws Exception {
        AtomicBoolean gateCalled = new AtomicBoolean();
        ConfirmationGate gate = context -> {
            gateCalled.set(true);
            return CompletableFuture.completedFuture(ConfirmationGate.Decision.DENIED);
        };
        ToolMiddleware middleware = DefaultMiddlewares.confirmation(gate, 0L);
        ToolExecutionContext context = baseContext();

        AgentToolResult result = middleware.handle(context, c -> AgentToolResult.text("ok"));
        assertFalse(gateCalled.get());
        assertEquals("ok", ((TextContent) result.getContent().get(0)).getText());
    }

    @Test
    void gateConfirmationDeniedReturnsErrorResult() throws Exception {
        ConfirmationGate gate = context -> CompletableFuture.completedFuture(ConfirmationGate.Decision.DENIED);
        ToolMiddleware middleware = DefaultMiddlewares.confirmation(gate, 0L);
        ToolExecutionContext context = baseContext().requireConfirmation(true);
        AtomicInteger invocations = new AtomicInteger();

        AgentToolResult result = middleware.handle(context, c -> {
            invocations.incrementAndGet();
            return AgentToolResult.text("ok");
        });
        assertEquals(0, invocations.get());
        assertTrue(result.isError());
        assertTrue(((TextContent) result.getContent().get(0)).getText().contains("denied"));
    }

    @Test
    void gateConfirmationTimeoutReturnsErrorResult() throws Exception {
        ConfirmationGate gate = context -> new CompletableFuture<ConfirmationGate.Decision>();
        ToolMiddleware middleware = DefaultMiddlewares.confirmation(gate, 200L);
        ToolExecutionContext context = baseContext().requireConfirmation(true);

        AgentToolResult result = middleware.handle(context, c -> AgentToolResult.text("ok"));
        assertTrue(result.isError());
        assertTrue(((TextContent) result.getContent().get(0)).getText().contains("confirmation timed out"));
    }

    @Test
    void gateConfirmationAbortCompletesAsDenied() throws Exception {
        ConfirmationGate gate = context -> new CompletableFuture<ConfirmationGate.Decision>();
        ToolMiddleware middleware = DefaultMiddlewares.confirmation(gate, 0L);
        AbortHandle abortHandle = new AbortHandle();
        abortHandle.abort();
        ToolExecutionContext context = new ToolExecutionContext(
                "call_1",
                "demo",
                testTool(),
                Collections.<String, Object>emptyMap(),
                abortHandle,
                result -> {
                }).requireConfirmation(true);

        AgentToolResult result = middleware.handle(context, c -> AgentToolResult.text("ok"));
        assertTrue(result.isError());
        assertTrue(((TextContent) result.getContent().get(0)).getText().contains("denied"));
    }

    @Test
    void retryMiddlewareRetriesUntilSuccess() throws Exception {
        ToolMiddleware middleware = DefaultMiddlewares.retry(2);
        ToolExecutionContext context = baseContext();
        AtomicInteger attempts = new AtomicInteger();

        AgentToolResult result = middleware.handle(context, c -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("try-again");
            }
            return AgentToolResult.text("ok");
        });

        assertEquals(3, attempts.get());
        assertEquals("ok", ((TextContent) result.getContent().get(0)).getText());
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
