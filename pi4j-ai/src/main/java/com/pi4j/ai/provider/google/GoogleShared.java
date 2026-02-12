package com.pi4j.ai.provider.google;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pi4j.ai.stream.AssistantMessageEventStream;
import com.pi4j.ai.stream.TextDeltaEvent;
import com.pi4j.ai.stream.TextEndEvent;
import com.pi4j.ai.stream.TextStartEvent;
import com.pi4j.ai.stream.ToolCallDeltaEvent;
import com.pi4j.ai.stream.ToolCallEndEvent;
import com.pi4j.ai.stream.ToolCallStartEvent;
import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.ImageContent;
import com.pi4j.ai.types.Message;
import com.pi4j.ai.types.Model;
import com.pi4j.ai.types.StopReason;
import com.pi4j.ai.types.TextContent;
import com.pi4j.ai.types.Tool;
import com.pi4j.ai.types.ToolCallContent;
import com.pi4j.ai.types.ToolResultMessage;
import com.pi4j.ai.types.Usage;
import com.pi4j.ai.types.UserMessage;
import com.pi4j.ai.util.JsonUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class GoogleShared {
    private GoogleShared() {
    }

    static JsonArray buildContents(List<Message> messages) {
        JsonArray list = new JsonArray();
        for (Message message : messages) {
            JsonObject item = new JsonObject();
            JsonArray parts = new JsonArray();

            if (message instanceof UserMessage) {
                item.addProperty("role", "user");
                for (ContentBlock block : ((UserMessage) message).getContent()) {
                    if (block instanceof TextContent) {
                        JsonObject part = new JsonObject();
                        part.addProperty("text", ((TextContent) block).getText());
                        parts.add(part);
                    } else if (block instanceof ImageContent) {
                        ImageContent image = (ImageContent) block;
                        JsonObject part = new JsonObject();
                        JsonObject inline = new JsonObject();
                        inline.addProperty("mimeType", image.getMimeType());
                        inline.addProperty("data", image.getData());
                        part.add("inlineData", inline);
                        parts.add(part);
                    }
                }
            } else if (message instanceof AssistantMessage) {
                item.addProperty("role", "model");
                for (ContentBlock block : ((AssistantMessage) message).getContent()) {
                    if (block instanceof TextContent) {
                        JsonObject part = new JsonObject();
                        part.addProperty("text", ((TextContent) block).getText());
                        parts.add(part);
                    } else if (block instanceof ToolCallContent) {
                        ToolCallContent toolCall = (ToolCallContent) block;
                        JsonObject part = new JsonObject();
                        JsonObject fn = new JsonObject();
                        fn.addProperty("name", toolCall.getName());
                        fn.add("args", JsonUtil.gson().toJsonTree(toolCall.getArguments()));
                        part.add("functionCall", fn);
                        parts.add(part);
                    }
                }
            } else if (message instanceof ToolResultMessage) {
                ToolResultMessage toolResult = (ToolResultMessage) message;
                item.addProperty("role", "user");
                JsonObject part = new JsonObject();
                JsonObject response = new JsonObject();
                response.addProperty("name", toolResult.getToolName());
                JsonObject payload = new JsonObject();
                payload.addProperty("content", flattenText(toolResult.getContent()));
                response.add("response", payload);
                part.add("functionResponse", response);
                parts.add(part);
            } else {
                continue;
            }

            item.add("parts", parts);
            list.add(item);
        }
        return list;
    }

    static JsonArray buildTools(List<Tool> tools) {
        JsonArray result = new JsonArray();
        if (tools.isEmpty()) {
            return result;
        }

        JsonObject tool = new JsonObject();
        JsonArray declarations = new JsonArray();
        for (Tool definition : tools) {
            JsonObject function = new JsonObject();
            function.addProperty("name", definition.getName());
            function.addProperty("description", definition.getDescription());
            function.add("parameters", definition.getParameters());
            declarations.add(function);
        }
        tool.add("functionDeclarations", declarations);
        result.add(tool);
        return result;
    }

    static void handleChunk(
            JsonObject root,
            String api,
            Model model,
            ParseState state,
            AssistantMessageEventStream stream) {
        if (!root.has("candidates") || !root.get("candidates").isJsonArray() || root.getAsJsonArray("candidates").size() == 0) {
            if (root.has("usageMetadata") && root.get("usageMetadata").isJsonObject()) {
                parseUsage(root.getAsJsonObject("usageMetadata"), state);
            }
            return;
        }

        JsonObject candidate = root.getAsJsonArray("candidates").get(0).getAsJsonObject();
        if (candidate.has("content") && candidate.get("content").isJsonObject()) {
            JsonObject content = candidate.getAsJsonObject("content");
            if (content.has("parts") && content.get("parts").isJsonArray()) {
                for (JsonElement element : content.getAsJsonArray("parts")) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject part = element.getAsJsonObject();

                    if (part.has("text")) {
                        if (!state.textStarted) {
                            state.textStarted = true;
                            state.textIndex = state.nextIndex++;
                            stream.push(new TextStartEvent(state.textIndex));
                        }
                        String delta = part.get("text").getAsString();
                        state.text.append(delta);
                        stream.push(new TextDeltaEvent(state.textIndex, delta, state.partial(api, model)));
                    }

                    if (part.has("functionCall") && part.get("functionCall").isJsonObject()) {
                        JsonObject functionCall = part.getAsJsonObject("functionCall");
                        String name = functionCall.has("name") ? functionCall.get("name").getAsString() : "tool";
                        String id = functionCall.has("id") && !functionCall.get("id").isJsonNull()
                                ? functionCall.get("id").getAsString()
                                : UUID.randomUUID().toString();
                        ToolCallContent toolCall = new ToolCallContent(
                                id,
                                name,
                                functionCall.has("args") && functionCall.get("args").isJsonObject()
                                        ? toMap(functionCall.getAsJsonObject("args"))
                                        : new LinkedHashMap<String, Object>());
                        int index = state.nextIndex++;
                        stream.push(new ToolCallStartEvent(index));
                        stream.push(new ToolCallDeltaEvent(index, JsonUtil.gson().toJson(toolCall.getArguments()), state.partial(api, model)));
                        state.blocks.add(toolCall);
                        stream.push(new ToolCallEndEvent(index, toolCall, state.partial(api, model)));
                    }
                }
            }
        }

        if (candidate.has("finishReason") && !candidate.get("finishReason").isJsonNull()) {
            String finish = candidate.get("finishReason").getAsString();
            if ("MAX_TOKENS".equalsIgnoreCase(finish)) {
                state.stopReason = StopReason.LENGTH;
            } else if ("STOP".equalsIgnoreCase(finish)) {
                state.stopReason = StopReason.STOP;
            } else {
                state.stopReason = StopReason.STOP;
            }
        }

        if (root.has("usageMetadata") && root.get("usageMetadata").isJsonObject()) {
            parseUsage(root.getAsJsonObject("usageMetadata"), state);
        }
    }

    static void finishParse(String api, Model model, ParseState state, AssistantMessageEventStream stream) {
        if (state.textStarted) {
            state.blocks.add(new TextContent(state.text.toString()));
            stream.push(new TextEndEvent(state.textIndex));
            state.textStarted = false;
        }
    }

    static void parseUsage(JsonObject usageMetadata, ParseState state) {
        int input = optInt(usageMetadata, "promptTokenCount");
        int output = optInt(usageMetadata, "candidatesTokenCount") + optInt(usageMetadata, "thoughtsTokenCount");
        int cacheRead = optInt(usageMetadata, "cachedContentTokenCount");
        int total = optInt(usageMetadata, "totalTokenCount");
        state.usage = new Usage(input, output, cacheRead, 0, total, null);
    }

    static int optInt(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsInt() : 0;
    }

    static Map<String, Object> toMap(JsonObject object) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            map.put(entry.getKey(), JsonUtil.gson().fromJson(entry.getValue(), Object.class));
        }
        return map;
    }

    static String flattenText(List<ContentBlock> content) {
        StringBuilder builder = new StringBuilder();
        for (ContentBlock block : content) {
            if (block instanceof TextContent) {
                builder.append(((TextContent) block).getText());
            }
        }
        return builder.toString();
    }

    static final class ParseState {
        private final List<ContentBlock> blocks = new ArrayList<ContentBlock>();
        private final StringBuilder text = new StringBuilder();
        private int nextIndex;
        private int textIndex = -1;
        private boolean textStarted;
        private StopReason stopReason;
        private Usage usage;

        AssistantMessage partial(String api, Model model) {
            return new AssistantMessage(new ArrayList<ContentBlock>(blocks), api, model.getProvider(), model.getId(), usage, stopReason, null);
        }

        AssistantMessage finalMessage(String api, Model model) {
            return new AssistantMessage(new ArrayList<ContentBlock>(blocks), api, model.getProvider(), model.getId(), usage, stopReason, null);
        }
    }
}
