package com.pi4j.ai.provider;

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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class MessageTransformer {
    private static final Pattern ANTHROPIC_TOOL_CALL_ID_ALLOWED = Pattern.compile("[^a-zA-Z0-9_-]");
    private static final String SYNTHETIC_TOOL_RESULT_TEXT = "No result provided";

    private MessageTransformer() {
    }

    public static List<Message> transform(List<Message> messages, Model targetModel) {
        Map<String, String> toolCallIdMap = new LinkedHashMap<String, String>();
        List<Message> transformed = new ArrayList<Message>();
        for (Message message : messages) {
            if (message == null) {
                continue;
            }
            if (message instanceof ToolResultMessage) {
                ToolResultMessage toolResult = (ToolResultMessage) message;
                String normalizedId = toolCallIdMap.get(toolResult.getToolCallId());
                if (normalizedId != null && !normalizedId.equals(toolResult.getToolCallId())) {
                    transformed.add(new ToolResultMessage(
                            normalizedId,
                            toolResult.getToolName(),
                            toolResult.getContent(),
                            toolResult.getDetails(),
                            toolResult.isError(),
                            toolResult.getTimestamp()));
                } else {
                    transformed.add(message);
                }
                continue;
            }
            if (message instanceof ObservationMessage) {
                transformed.add(toProviderMessage((ObservationMessage) message));
                continue;
            }
            if (message instanceof AssistantMessage) {
                AssistantMessage assistant = (AssistantMessage) message;
                if (assistant.getStopReason() == StopReason.ERROR || assistant.getStopReason() == StopReason.ABORTED) {
                    continue;
                }
                transformed.add(transformAssistant(assistant, targetModel, toolCallIdMap));
                continue;
            }
            transformed.add(message);
        }

        return insertSyntheticToolResults(transformed);
    }

    private static UserMessage toProviderMessage(ObservationMessage observation) {
        List<ContentBlock> content = new ArrayList<ContentBlock>();
        content.add(new TextContent("[Observation source=" + observation.getSource() + "]"));
        content.addAll(observation.getContent());
        return new UserMessage(content, observation.getTimestamp());
    }

    private static AssistantMessage transformAssistant(
            AssistantMessage message,
            Model targetModel,
            Map<String, String> toolCallIdMap) {
        boolean sameModel = isSameModel(message, targetModel);
        List<ContentBlock> converted = new ArrayList<ContentBlock>();
        for (ContentBlock block : message.getContent()) {
            if (block instanceof ThinkingContent) {
                ThinkingContent thinking = (ThinkingContent) block;
                String thinkingText = thinking.getThinking();
                if (sameModel) {
                    if (thinking.getThinkingSignature() != null || !isBlank(thinkingText)) {
                        converted.add(block);
                    }
                } else if (!isBlank(thinkingText)) {
                    converted.add(new TextContent(thinkingText));
                }
                continue;
            }
            if (block instanceof TextContent) {
                TextContent textContent = (TextContent) block;
                if (!isBlank(textContent.getText())) {
                    if (sameModel) {
                        converted.add(textContent);
                    } else {
                        converted.add(new TextContent(textContent.getText()));
                    }
                }
                continue;
            }
            if (block instanceof ToolCallContent) {
                ToolCallContent toolCall = (ToolCallContent) block;
                ToolCallContent normalized = toolCall;
                if (!sameModel) {
                    String normalizedId = normalizeToolCallId(toolCall.getId(), targetModel);
                    if (!normalizedId.equals(toolCall.getId())) {
                        toolCallIdMap.put(toolCall.getId(), normalizedId);
                    }
                    normalized = new ToolCallContent(normalizedId, toolCall.getName(), toolCall.getArguments());
                }
                converted.add(normalized);
                continue;
            }
            if (!isEmptyBlock(block)) {
                converted.add(block);
            }
        }

        return new AssistantMessage(
                converted,
                message.getApi(),
                message.getProvider(),
                message.getModel(),
                message.getUsage(),
                message.getStopReason(),
                message.getErrorMessage(),
                message.getTimestamp());
    }

    private static List<Message> insertSyntheticToolResults(List<Message> transformed) {
        List<Message> result = new ArrayList<Message>();
        List<ToolCallContent> pendingToolCalls = new ArrayList<ToolCallContent>();
        Map<String, Boolean> existingToolResultIds = new LinkedHashMap<String, Boolean>();

        for (Message message : transformed) {
            if (message instanceof AssistantMessage) {
                flushPendingToolCalls(result, pendingToolCalls, existingToolResultIds);
                AssistantMessage assistantMessage = (AssistantMessage) message;
                pendingToolCalls = extractToolCalls(assistantMessage);
                existingToolResultIds = new LinkedHashMap<String, Boolean>();
                result.add(message);
                continue;
            }
            if (message instanceof ToolResultMessage) {
                ToolResultMessage toolResult = (ToolResultMessage) message;
                existingToolResultIds.put(toolResult.getToolCallId(), Boolean.TRUE);
                result.add(message);
                continue;
            }
            flushPendingToolCalls(result, pendingToolCalls, existingToolResultIds);
            result.add(message);
        }

        flushPendingToolCalls(result, pendingToolCalls, existingToolResultIds);
        return result;
    }

    private static void flushPendingToolCalls(
            List<Message> result,
            List<ToolCallContent> pendingToolCalls,
            Map<String, Boolean> existingToolResultIds) {
        if (pendingToolCalls == null || pendingToolCalls.isEmpty()) {
            return;
        }
        for (ToolCallContent toolCall : pendingToolCalls) {
            if (existingToolResultIds.containsKey(toolCall.getId())) {
                continue;
            }
            result.add(new ToolResultMessage(
                    toolCall.getId(),
                    toolCall.getName(),
                    Collections.<ContentBlock>singletonList(new TextContent(SYNTHETIC_TOOL_RESULT_TEXT)),
                    null,
                    true));
        }
        pendingToolCalls.clear();
        existingToolResultIds.clear();
    }

    private static List<ToolCallContent> extractToolCalls(AssistantMessage assistantMessage) {
        List<ToolCallContent> toolCalls = new ArrayList<ToolCallContent>();
        for (ContentBlock block : assistantMessage.getContent()) {
            if (block instanceof ToolCallContent) {
                toolCalls.add((ToolCallContent) block);
            }
        }
        return toolCalls;
    }

    private static boolean isSameModel(AssistantMessage assistant, Model targetModel) {
        return equalsNullable(assistant.getProvider(), targetModel.getProvider())
                && equalsNullable(assistant.getApi(), targetModel.getApi())
                && equalsNullable(assistant.getModel(), targetModel.getId());
    }

    private static boolean equalsNullable(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private static String normalizeToolCallId(String id, Model targetModel) {
        if (id == null || id.isEmpty()) {
            id = "tool_call";
        }
        if ("mistral".equals(targetModel.getProvider())) {
            return normalizeMistralId(id);
        }
        if ("anthropic".equals(targetModel.getProvider()) || "anthropic-messages".equals(targetModel.getApi())) {
            return normalizeAnthropicId(id);
        }
        return id;
    }

    private static String normalizeMistralId(String id) {
        String sanitized = normalizeAnthropicId(id);
        if (sanitized.length() >= 9) {
            return sanitized.substring(0, 9);
        }
        StringBuilder builder = new StringBuilder(sanitized);
        String suffix = Integer.toHexString(id.hashCode()).replace('-', 'a');
        int suffixIndex = 0;
        while (builder.length() < 9) {
            if (suffixIndex < suffix.length()) {
                builder.append(suffix.charAt(suffixIndex++));
            } else {
                builder.append('x');
            }
        }
        return builder.substring(0, 9);
    }

    private static String normalizeAnthropicId(String id) {
        String sanitized = ANTHROPIC_TOOL_CALL_ID_ALLOWED.matcher(id).replaceAll("_");
        if (sanitized.isEmpty()) {
            sanitized = "tool_call";
        }
        if (sanitized.length() > 64) {
            sanitized = sanitized.substring(0, 64);
        }
        return sanitized;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isEmptyBlock(ContentBlock block) {
        if (block instanceof TextContent) {
            return isBlank(((TextContent) block).getText());
        }
        if (block instanceof ThinkingContent) {
            ThinkingContent thinking = (ThinkingContent) block;
            return isBlank(thinking.getThinking()) && thinking.getThinkingSignature() == null;
        }
        return false;
    }
}
