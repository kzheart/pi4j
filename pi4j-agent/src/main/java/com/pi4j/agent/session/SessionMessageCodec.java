package com.pi4j.agent.session;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pi4j.agent.AgentMessage;
import com.pi4j.agent.LlmAgentMessage;
import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.ImageContent;
import com.pi4j.ai.types.Message;
import com.pi4j.ai.types.StopReason;
import com.pi4j.ai.types.TextContent;
import com.pi4j.ai.types.ThinkingContent;
import com.pi4j.ai.types.ToolCallContent;
import com.pi4j.ai.types.ToolResultMessage;
import com.pi4j.ai.types.Usage;
import com.pi4j.ai.types.UserMessage;
import com.pi4j.ai.util.JsonUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 会话消息与 JSON 的编解码，供框架内与消费方复用。 */
public final class SessionMessageCodec {
    private SessionMessageCodec() {
    }

    public static JsonObject encode(AgentMessage message) {
        if (!(message instanceof LlmAgentMessage)) {
            throw new IllegalArgumentException("Only LlmAgentMessage is supported for session persistence");
        }

        Message llm = ((LlmAgentMessage) message).getMessage();
        JsonObject root = new JsonObject();
        root.addProperty("kind", "llm");
        root.addProperty("timestamp", llm.getTimestamp());
        root.addProperty("role", llm.getRole());

        if (llm instanceof UserMessage) {
            root.add("content", encodeContent(((UserMessage) llm).getContent()));
        } else if (llm instanceof AssistantMessage) {
            AssistantMessage assistant = (AssistantMessage) llm;
            root.add("content", encodeContent(assistant.getContent()));
            if (assistant.getApi() != null) {
                root.addProperty("api", assistant.getApi());
            }
            if (assistant.getProvider() != null) {
                root.addProperty("provider", assistant.getProvider());
            }
            if (assistant.getModel() != null) {
                root.addProperty("model", assistant.getModel());
            }
            if (assistant.getStopReason() != null) {
                root.addProperty("stopReason", assistant.getStopReason().name());
            }
            if (assistant.getErrorMessage() != null) {
                root.addProperty("errorMessage", assistant.getErrorMessage());
            }
            if (assistant.getUsage() != null) {
                root.add("usage", encodeUsage(assistant.getUsage()));
            }
        } else if (llm instanceof ToolResultMessage) {
            ToolResultMessage toolResult = (ToolResultMessage) llm;
            root.addProperty("toolCallId", toolResult.getToolCallId());
            root.addProperty("toolName", toolResult.getToolName());
            root.addProperty("isError", toolResult.isError());
            root.add("content", encodeContent(toolResult.getContent()));
            if (toolResult.getDetails() != null) {
                root.add("details", JsonUtil.gson().toJsonTree(toolResult.getDetails()));
            }
        } else {
            throw new IllegalArgumentException("Unsupported LLM message type: " + llm.getClass().getName());
        }

        return root;
    }

    public static AgentMessage decode(JsonObject json) {
        String kind = getString(json, "kind");
        if (!"llm".equals(kind)) {
            throw new IllegalArgumentException("Unsupported message kind: " + kind);
        }

        String role = getString(json, "role");
        long timestamp = json.has("timestamp") ? json.get("timestamp").getAsLong() : System.currentTimeMillis();

        if ("user".equals(role)) {
            UserMessage user = new UserMessage(decodeContent(json.getAsJsonArray("content")), timestamp);
            return new LlmAgentMessage(user);
        }
        if ("assistant".equals(role)) {
            AssistantMessage assistant = new AssistantMessage(
                    decodeContent(json.getAsJsonArray("content")),
                    nullableString(json, "api"),
                    nullableString(json, "provider"),
                    nullableString(json, "model"),
                    decodeUsage(json.getAsJsonObject("usage")),
                    decodeStopReason(nullableString(json, "stopReason")),
                    nullableString(json, "errorMessage"),
                    timestamp);
            return new LlmAgentMessage(assistant);
        }
        if ("toolResult".equals(role)) {
            ToolResultMessage toolResult = new ToolResultMessage(
                    getString(json, "toolCallId"),
                    getString(json, "toolName"),
                    decodeContent(json.getAsJsonArray("content")),
                    json.has("details") ? JsonUtil.gson().fromJson(json.get("details"), Object.class) : null,
                    json.has("isError") && json.get("isError").getAsBoolean(),
                    timestamp);
            return new LlmAgentMessage(toolResult);
        }
        throw new IllegalArgumentException("Unsupported role: " + role);
    }

    private static JsonArray encodeContent(List<ContentBlock> blocks) {
        JsonArray list = new JsonArray();
        for (ContentBlock block : blocks) {
            JsonObject item = new JsonObject();
            item.addProperty("type", block.getType());
            if (block instanceof TextContent) {
                TextContent text = (TextContent) block;
                item.addProperty("text", text.getText());
                if (text.getTextSignature() != null) {
                    item.addProperty("textSignature", text.getTextSignature());
                }
            } else if (block instanceof ThinkingContent) {
                ThinkingContent thinking = (ThinkingContent) block;
                item.addProperty("thinking", thinking.getThinking());
                if (thinking.getThinkingSignature() != null) {
                    item.addProperty("thinkingSignature", thinking.getThinkingSignature());
                }
            } else if (block instanceof ToolCallContent) {
                ToolCallContent toolCall = (ToolCallContent) block;
                item.addProperty("id", toolCall.getId());
                item.addProperty("name", toolCall.getName());
                item.add("arguments", JsonUtil.gson().toJsonTree(toolCall.getArguments()));
                if (toolCall.getThoughtSignature() != null) {
                    item.addProperty("thoughtSignature", toolCall.getThoughtSignature());
                }
            } else if (block instanceof ImageContent) {
                ImageContent image = (ImageContent) block;
                item.addProperty("data", image.getData());
                item.addProperty("mimeType", image.getMimeType());
            }
            list.add(item);
        }
        return list;
    }

    private static List<ContentBlock> decodeContent(JsonArray content) {
        List<ContentBlock> blocks = new ArrayList<ContentBlock>();
        if (content == null) {
            return blocks;
        }
        for (JsonElement element : content) {
            JsonObject item = element.getAsJsonObject();
            String type = getString(item, "type");
            if ("text".equals(type)) {
                blocks.add(new TextContent(getString(item, "text"), nullableString(item, "textSignature")));
            } else if ("thinking".equals(type)) {
                blocks.add(new ThinkingContent(getString(item, "thinking"), nullableString(item, "thinkingSignature")));
            } else if ("toolCall".equals(type)) {
                Map<String, Object> arguments = new LinkedHashMap<String, Object>();
                if (item.has("arguments")) {
                    JsonObject args = item.getAsJsonObject("arguments");
                    for (Map.Entry<String, JsonElement> entry : args.entrySet()) {
                        arguments.put(entry.getKey(), JsonUtil.gson().fromJson(entry.getValue(), Object.class));
                    }
                }
                blocks.add(new ToolCallContent(
                        getString(item, "id"),
                        getString(item, "name"),
                        arguments,
                        nullableString(item, "thoughtSignature")));
            } else if ("image".equals(type)) {
                blocks.add(new ImageContent(getString(item, "data"), getString(item, "mimeType")));
            }
        }
        return blocks;
    }

    private static JsonObject encodeUsage(Usage usage) {
        JsonObject json = new JsonObject();
        json.addProperty("input", usage.getInput());
        json.addProperty("output", usage.getOutput());
        json.addProperty("cacheRead", usage.getCacheRead());
        json.addProperty("cacheWrite", usage.getCacheWrite());
        json.addProperty("totalTokens", usage.getTotalTokens());
        if (usage.getCost() != null) {
            Usage.Cost cost = usage.getCost();
            JsonObject costJson = new JsonObject();
            costJson.addProperty("input", cost.getInput());
            costJson.addProperty("output", cost.getOutput());
            costJson.addProperty("cacheRead", cost.getCacheRead());
            costJson.addProperty("cacheWrite", cost.getCacheWrite());
            costJson.addProperty("total", cost.getTotal());
            json.add("cost", costJson);
        }
        return json;
    }

    private static Usage decodeUsage(JsonObject json) {
        if (json == null) {
            return null;
        }
        Usage.Cost cost = null;
        if (json.has("cost") && json.get("cost").isJsonObject()) {
            JsonObject costJson = json.getAsJsonObject("cost");
            cost = new Usage.Cost(
                    costJson.has("input") ? costJson.get("input").getAsDouble() : 0d,
                    costJson.has("output") ? costJson.get("output").getAsDouble() : 0d,
                    costJson.has("cacheRead") ? costJson.get("cacheRead").getAsDouble() : 0d,
                    costJson.has("cacheWrite") ? costJson.get("cacheWrite").getAsDouble() : 0d,
                    costJson.has("total") ? costJson.get("total").getAsDouble() : 0d);
        }
        return new Usage(
                json.has("input") ? json.get("input").getAsInt() : 0,
                json.has("output") ? json.get("output").getAsInt() : 0,
                json.has("cacheRead") ? json.get("cacheRead").getAsInt() : 0,
                json.has("cacheWrite") ? json.get("cacheWrite").getAsInt() : 0,
                json.has("totalTokens") ? json.get("totalTokens").getAsInt() : 0,
                cost);
    }

    private static StopReason decodeStopReason(String reason) {
        if (reason == null || reason.isEmpty()) {
            return StopReason.STOP;
        }
        return StopReason.valueOf(reason);
    }

    private static String getString(JsonObject json, String field) {
        if (!json.has(field) || json.get(field).isJsonNull()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return json.get(field).getAsString();
    }

    private static String nullableString(JsonObject json, String field) {
        if (!json.has(field) || json.get(field).isJsonNull()) {
            return null;
        }
        return json.get(field).getAsString();
    }
}
