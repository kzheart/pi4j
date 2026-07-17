package com.pi4j.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.Message;
import com.pi4j.ai.types.Model;
import com.pi4j.ai.types.ObservationMessage;
import com.pi4j.ai.types.StopReason;
import com.pi4j.ai.types.TextContent;
import com.pi4j.ai.types.ThinkingContent;
import com.pi4j.ai.types.ToolCallContent;
import com.pi4j.ai.types.ToolResultMessage;
import com.pi4j.ai.types.UserMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class MessageTransformerTest {

    @Test
    void normalizesToolCallIdsForMistralAndAnthropic() {
        String originalId = "call|abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        ToolCallContent toolCall = new ToolCallContent(originalId, "sum", new LinkedHashMap<String, Object>());
        AssistantMessage assistant = new AssistantMessage(
                Collections.<ContentBlock>singletonList(toolCall),
                "openai-completions",
                "openai",
                "gpt-4.1",
                null,
                StopReason.TOOL_USE,
                null);
        ToolResultMessage toolResult = new ToolResultMessage(
                originalId,
                "sum",
                Collections.<ContentBlock>singletonList(new TextContent("3")),
                null,
                false);
        List<Message> source = Arrays.<Message>asList(assistant, toolResult);

        List<Message> mistral = MessageTransformer.transform(source, model("mistral-mini", "openai-completions", "mistral"));
        AssistantMessage mistralAssistant = (AssistantMessage) mistral.get(0);
        ToolCallContent mistralToolCall = (ToolCallContent) mistralAssistant.getContent().get(0);
        ToolResultMessage mistralToolResult = (ToolResultMessage) mistral.get(1);
        assertEquals(9, mistralToolCall.getId().length());
        assertEquals(mistralToolCall.getId(), mistralToolResult.getToolCallId());

        List<Message> anthropic = MessageTransformer.transform(source, model("claude", "anthropic-messages", "anthropic"));
        AssistantMessage anthropicAssistant = (AssistantMessage) anthropic.get(0);
        ToolCallContent anthropicToolCall = (ToolCallContent) anthropicAssistant.getContent().get(0);
        ToolResultMessage anthropicToolResult = (ToolResultMessage) anthropic.get(1);
        assertTrue(anthropicToolCall.getId().matches("^[a-zA-Z0-9_-]+$"));
        assertTrue(anthropicToolCall.getId().length() <= 64);
        assertEquals(anthropicToolCall.getId(), anthropicToolResult.getToolCallId());
    }

    @Test
    void insertsSyntheticToolResultForOrphanCallAndSkipsErroredAssistant() {
        List<ContentBlock> toolCallBlocks = new ArrayList<ContentBlock>();
        toolCallBlocks.add(new ToolCallContent("call_1", "sum", new LinkedHashMap<String, Object>()));
        AssistantMessage toolCallAssistant = new AssistantMessage(
                toolCallBlocks,
                "openai-completions",
                "openai",
                "gpt-4.1",
                null,
                StopReason.TOOL_USE,
                null);
        AssistantMessage erroredAssistant = new AssistantMessage(
                Collections.<ContentBlock>singletonList(new TextContent("partial")),
                "openai-completions",
                "openai",
                "gpt-4.1",
                null,
                StopReason.ERROR,
                "failed");
        UserMessage nextUser = new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("continue")));

        List<Message> transformed = MessageTransformer.transform(
                Arrays.<Message>asList(toolCallAssistant, nextUser, erroredAssistant),
                model("gpt-4.1", "openai-completions", "openai"));

        assertEquals(3, transformed.size());
        assertTrue(transformed.get(0) instanceof AssistantMessage);
        assertTrue(transformed.get(1) instanceof ToolResultMessage);
        assertTrue(transformed.get(2) instanceof UserMessage);

        ToolResultMessage synthetic = (ToolResultMessage) transformed.get(1);
        assertTrue(synthetic.isError());
        TextContent text = (TextContent) synthetic.getContent().get(0);
        assertEquals("No result provided", text.getText());
    }

    @Test
    void filtersEmptyTextAndThinkingBlocks() {
        List<ContentBlock> blocks = new ArrayList<ContentBlock>();
        blocks.add(new TextContent("   "));
        blocks.add(new ThinkingContent(""));
        blocks.add(new TextContent("ok"));
        blocks.add(new ThinkingContent("", "sig"));
        AssistantMessage assistant = new AssistantMessage(
                blocks,
                "openai-completions",
                "openai",
                "gpt-4.1",
                null,
                StopReason.STOP,
                null);

        List<Message> transformed = MessageTransformer.transform(
                Collections.<Message>singletonList(assistant),
                model("gpt-4.1", "openai-completions", "openai"));

        AssistantMessage normalized = (AssistantMessage) transformed.get(0);
        assertEquals(2, normalized.getContent().size());
        assertTrue(normalized.getContent().get(0) instanceof TextContent);
        assertTrue(normalized.getContent().get(1) instanceof ThinkingContent);
        assertFalse(((TextContent) normalized.getContent().get(0)).getText().trim().isEmpty());
    }

    @Test
    void convertsObservationToLabelledProviderUserMessage() {
        ObservationMessage observation = new ObservationMessage(
                "pet-task",
                Collections.<ContentBlock>singletonList(new TextContent("navigation completed")),
                1234L);

        List<Message> transformed = MessageTransformer.transform(
                Collections.<Message>singletonList(observation),
                model("deepseek-v4-flash", "openai-completions", "deepseek"));

        assertEquals(1, transformed.size());
        assertTrue(transformed.get(0) instanceof UserMessage);
        UserMessage user = (UserMessage) transformed.get(0);
        assertEquals(1234L, user.getTimestamp());
        assertEquals("[Observation source=pet-task]", ((TextContent) user.getContent().get(0)).getText());
        assertEquals("navigation completed", ((TextContent) user.getContent().get(1)).getText());
    }

    private Model model(String id, String api, String provider) {
        return new Model(
                id,
                id,
                api,
                provider,
                "https://example.com",
                false,
                Arrays.asList("text"),
                null,
                64000,
                4096,
                Collections.<String, String>emptyMap());
    }
}
