package com.pi4j.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.pi4j.agent.func.ApiKeyResolver;
import com.pi4j.agent.func.ContextTransformer;
import com.pi4j.agent.func.MessageConverter;
import com.pi4j.agent.tool.AgentTool;
import com.pi4j.agent.tool.AgentToolResult;
import com.pi4j.agent.tool.ToolDispatcher;
import com.pi4j.agent.tool.ToolMiddleware;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.Message;
import com.pi4j.ai.types.Model;
import com.pi4j.ai.types.TextContent;
import com.pi4j.ai.types.UserMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentOptionsTest {

    @Test
    void builderUsesExpectedDefaults() {
        AgentOptions options = AgentOptions.builder().build();
        assertEquals("off", options.getThinkingLevel());
        assertEquals("all", options.getSteeringMode());
        assertEquals("all", options.getFollowUpMode());
        assertNotNull(options.getConvertToLlm());
        assertNotNull(options.getTransformContext());
        assertNotNull(options.getGetApiKey());
        assertNotNull(options.getToolDispatcher());
        assertTrue(options.getTools().isEmpty());
        assertTrue(options.getInitialMessages().isEmpty());
        assertTrue(options.getToolMiddlewares().isEmpty());
        assertNull(options.getResponseFormat());

        List<AgentMessage> source = new ArrayList<AgentMessage>();
        UserMessage userMessage = new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("hello")));
        source.add(new LlmAgentMessage(userMessage));
        source.add(new AgentMessage("custom", 1L) {
        });

        List<Message> converted = options.getConvertToLlm().convert(source);
        assertEquals(1, converted.size());
        assertSame(userMessage, converted.get(0));
    }

    @Test
    void builderAppliesExplicitOverrides() {
        Model model = new Model(
                "demo",
                "Demo",
                "openai-completions",
                "openai",
                "https://api.openai.com",
                false,
                Arrays.asList("text"),
                null,
                64000,
                4096,
                Collections.<String, String>emptyMap());
        AgentTool tool = new AgentTool() {
            @Override
            public String getName() {
                return "demo-tool";
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
            public com.google.gson.JsonObject getParameters() {
                return new com.google.gson.JsonObject();
            }

            @Override
            public com.pi4j.agent.tool.AgentToolResult execute(
                    String toolCallId,
                    java.util.Map<String, Object> params,
                    com.pi4j.ai.provider.AbortHandle abortHandle,
                    com.pi4j.agent.tool.ToolUpdateCallback onUpdate) {
                return com.pi4j.agent.tool.AgentToolResult.text("ok");
            }
        };
        AgentMessage initialMessage = new AgentMessage("seed", 2L) {
        };

        MessageConverter converter = messages -> Collections.<Message>emptyList();
        ContextTransformer transformer = (messages, abortHandle) -> Collections.<AgentMessage>emptyList();
        ApiKeyResolver resolver = provider -> "k-" + provider;
        ToolDispatcher dispatcher = (context, invocation) -> AgentToolResult.text("dispatched");
        ToolMiddleware middleware = (context, chain) -> chain.proceed(context);

        AgentOptions options = AgentOptions.builder()
                .systemPrompt("sys")
                .model(model)
                .thinkingLevel("high")
                .tools(Collections.singletonList(tool))
                .initialMessages(Collections.singletonList(initialMessage))
                .convertToLlm(converter)
                .transformContext(transformer)
                .getApiKey(resolver)
                .temperature(0.2)
                .maxTokens(321)
                .thinkingBudget(123)
                .thinkingEffort("medium")
                .responseFormat("json_object")
                .toolChoice("required")
                .cacheRetention("long")
                .sessionId("s-1")
                .steeringMode("one-at-a-time")
                .followUpMode("none")
                .toolDispatcher(dispatcher)
                .toolMiddlewares(Collections.singletonList(middleware))
                .build();

        assertEquals("sys", options.getSystemPrompt());
        assertSame(model, options.getModel());
        assertEquals("high", options.getThinkingLevel());
        assertEquals(1, options.getTools().size());
        assertSame(tool, options.getTools().get(0));
        assertEquals(1, options.getInitialMessages().size());
        assertSame(initialMessage, options.getInitialMessages().get(0));
        assertSame(converter, options.getConvertToLlm());
        assertSame(transformer, options.getTransformContext());
        assertSame(resolver, options.getGetApiKey());
        assertEquals(Double.valueOf(0.2), options.getTemperature());
        assertEquals(Integer.valueOf(321), options.getMaxTokens());
        assertEquals(Integer.valueOf(123), options.getThinkingBudget());
        assertEquals("medium", options.getThinkingEffort());
        assertEquals("json_object", options.getResponseFormat().get("type").getAsString());
        assertEquals("required", options.getToolChoice());
        assertEquals("long", options.getCacheRetention());
        assertEquals("s-1", options.getSessionId());
        assertEquals("one-at-a-time", options.getSteeringMode());
        assertEquals("none", options.getFollowUpMode());
        assertSame(dispatcher, options.getToolDispatcher());
        assertEquals(1, options.getToolMiddlewares().size());
        assertSame(middleware, options.getToolMiddlewares().get(0));
    }

    @Test
    void builderAcceptsJsonObjectResponseFormat() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "json_schema");
        JsonObject jsonSchema = new JsonObject();
        jsonSchema.addProperty("name", "my_schema");
        schema.add("json_schema", jsonSchema);

        AgentOptions options = AgentOptions.builder()
                .responseFormat(schema)
                .build();

        assertSame(schema, options.getResponseFormat());
    }

    @Test
    void builderRejectsNullToolDispatcher() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AgentOptions.builder().toolDispatcher(null));
        assertEquals("toolDispatcher is required.", exception.getMessage());
    }
}
