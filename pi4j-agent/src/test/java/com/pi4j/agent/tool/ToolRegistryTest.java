package com.pi4j.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.pi4j.ai.provider.AbortHandle;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

    @Test
    void registerGetHasAndUnregisterWorkAsExpected() {
        ToolRegistry registry = new ToolRegistry();
        AgentTool tool = tool("sum");

        registry.register(tool);
        assertTrue(registry.has("sum"));
        assertSame(tool, registry.get("sum"));

        registry.unregister("sum");
        assertFalse(registry.has("sum"));
        assertNull(registry.get("sum"));
    }

    @Test
    void replaceOverwritesExistingToolByNameKey() {
        ToolRegistry registry = new ToolRegistry();
        AgentTool oldTool = tool("calc-v1");
        AgentTool newTool = tool("calc-v2");

        registry.register(oldTool);
        registry.replace("calc", newTool);

        assertSame(newTool, registry.get("calc"));
        assertSame(oldTool, registry.get("calc-v1"));
    }

    @Test
    void getAllReturnsRegisteredOrderAndSnapshot() {
        ToolRegistry registry = new ToolRegistry();
        AgentTool first = tool("first");
        AgentTool second = tool("second");

        registry.register(first);
        registry.register(second);

        List<AgentTool> snapshot = registry.getAll();
        assertEquals(Arrays.asList(first, second), snapshot);

        snapshot.clear();
        assertEquals(Arrays.asList(first, second), registry.getAll());
    }

    private AgentTool tool(String name) {
        return new AgentTool() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return name;
            }

            @Override
            public String getLabel() {
                return name;
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
                return AgentToolResult.text(name);
            }
        };
    }
}
