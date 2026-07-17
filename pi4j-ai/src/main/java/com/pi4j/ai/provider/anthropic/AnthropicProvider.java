package com.pi4j.ai.provider.anthropic;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.pi4j.ai.provider.AbortHandle;
import com.pi4j.ai.provider.AbstractHttpSseProvider;
import com.pi4j.ai.provider.StreamOptions;
import com.pi4j.ai.stream.AssistantMessageEventStream;
import com.pi4j.ai.stream.DoneEvent;
import com.pi4j.ai.stream.StartEvent;
import com.pi4j.ai.stream.TextDeltaEvent;
import com.pi4j.ai.stream.TextEndEvent;
import com.pi4j.ai.stream.TextStartEvent;
import com.pi4j.ai.stream.ThinkingDeltaEvent;
import com.pi4j.ai.stream.ThinkingEndEvent;
import com.pi4j.ai.stream.ThinkingStartEvent;
import com.pi4j.ai.stream.ToolCallDeltaEvent;
import com.pi4j.ai.stream.ToolCallEndEvent;
import com.pi4j.ai.stream.ToolCallStartEvent;
import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.Context;
import com.pi4j.ai.types.ImageContent;
import com.pi4j.ai.types.Message;
import com.pi4j.ai.types.Model;
import com.pi4j.ai.types.StopReason;
import com.pi4j.ai.types.TextContent;
import com.pi4j.ai.types.ThinkingContent;
import com.pi4j.ai.types.Tool;
import com.pi4j.ai.types.ToolCallContent;
import com.pi4j.ai.types.ToolResultMessage;
import com.pi4j.ai.types.Usage;
import com.pi4j.ai.types.UserMessage;
import com.pi4j.ai.util.JsonUtil;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class AnthropicProvider extends AbstractHttpSseProvider {
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    private static final String API_PATH = "/v1/messages";

    public AnthropicProvider() {
        this(new OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build());
    }

    public AnthropicProvider(OkHttpClient client) {
        super(client);
    }

    @Override
    public String getApi() {
        return "anthropic-messages";
    }

    @Override
    protected Request buildRequest(Model model, Context context, StreamOptions options) {
        String apiKey = options.getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("apiKey is required");
        }

        String baseUrl = model.getBaseUrl() == null || model.getBaseUrl().trim().isEmpty()
                ? DEFAULT_BASE_URL
                : trimTrailingSlash(model.getBaseUrl());
        String url = baseUrl + API_PATH;

        JsonObject payload = new JsonObject();
        payload.addProperty("model", model.getId());
        payload.addProperty("stream", true);
        payload.addProperty("max_tokens", options.getMaxTokens() != null ? options.getMaxTokens() : model.getMaxTokens());
        if (options.getTemperature() != null) {
            payload.addProperty("temperature", options.getTemperature());
        }
        JsonObject cacheControl = buildCacheControl(options.getCacheRetention());
        if (context.getSystemPrompt() != null && !context.getSystemPrompt().isEmpty()) {
            if (cacheControl != null) {
                JsonArray systemBlocks = new JsonArray();
                JsonObject systemText = new JsonObject();
                systemText.addProperty("type", "text");
                systemText.addProperty("text", context.getSystemPrompt());
                systemText.add("cache_control", cacheControl);
                systemBlocks.add(systemText);
                payload.add("system", systemBlocks);
            } else {
                payload.addProperty("system", context.getSystemPrompt());
            }
        }

        payload.add("messages", buildMessages(context.getMessages(), cacheControl));
        payload.add("tools", buildTools(context.getTools()));

        RequestBody body = RequestBody.create(JSON_MEDIA_TYPE, JsonUtil.gson().toJson(payload));

        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(body)
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01");

        for (Map.Entry<String, String> entry : model.getHeaders().entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : options.getHeaders().entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }

        return builder.build();
    }

    private JsonArray buildMessages(List<Message> messages, JsonObject cacheControl) {
        JsonArray list = new JsonArray();
        int lastUserMessageIndex = findLastUserMessageIndex(messages);
        for (int index = 0; index < messages.size(); index++) {
            Message message = messages.get(index);
            if (message instanceof UserMessage) {
                UserMessage user = (UserMessage) message;
                JsonObject item = new JsonObject();
                item.addProperty("role", "user");
                boolean applyCacheControl = cacheControl != null && index == lastUserMessageIndex;
                item.add("content", toAnthropicContent(user.getContent(), cacheControl, applyCacheControl));
                list.add(item);
            } else if (message instanceof AssistantMessage) {
                AssistantMessage assistant = (AssistantMessage) message;
                JsonObject item = new JsonObject();
                item.addProperty("role", "assistant");
                item.add("content", toAnthropicContent(assistant.getContent(), null, false));
                list.add(item);
            } else if (message instanceof ToolResultMessage) {
                ToolResultMessage tool = (ToolResultMessage) message;
                JsonObject item = new JsonObject();
                item.addProperty("role", "user");
                JsonArray content = new JsonArray();
                JsonObject toolResult = new JsonObject();
                toolResult.addProperty("type", "tool_result");
                toolResult.addProperty("tool_use_id", tool.getToolCallId());
                toolResult.addProperty("is_error", tool.isError());
                toolResult.add("content", toAnthropicContent(tool.getContent(), null, false));
                content.add(toolResult);
                item.add("content", content);
                list.add(item);
            }
        }
        return list;
    }

    private JsonArray toAnthropicContent(List<ContentBlock> content, JsonObject cacheControl, boolean applyCacheControl) {
        JsonArray list = new JsonArray();
        JsonObject lastText = null;
        for (ContentBlock block : content) {
            JsonObject item = new JsonObject();
            if (block instanceof TextContent) {
                TextContent text = (TextContent) block;
                item.addProperty("type", "text");
                item.addProperty("text", text.getText());
                lastText = item;
            } else if (block instanceof ThinkingContent) {
                ThinkingContent thinking = (ThinkingContent) block;
                item.addProperty("type", "thinking");
                item.addProperty("thinking", thinking.getThinking());
                if (thinking.getThinkingSignature() != null) {
                    item.addProperty("signature", thinking.getThinkingSignature());
                }
            } else if (block instanceof ToolCallContent) {
                ToolCallContent toolCall = (ToolCallContent) block;
                item.addProperty("type", "tool_use");
                item.addProperty("id", toolCall.getId());
                item.addProperty("name", toolCall.getName());
                item.add("input", JsonUtil.gson().toJsonTree(toolCall.getArguments()));
            } else if (block instanceof ImageContent) {
                ImageContent image = (ImageContent) block;
                item.addProperty("type", "image");
                JsonObject source = new JsonObject();
                source.addProperty("type", "base64");
                source.addProperty("media_type", image.getMimeType());
                source.addProperty("data", image.getData());
                item.add("source", source);
            }
            list.add(item);
        }
        if (applyCacheControl && cacheControl != null && lastText != null) {
            lastText.add("cache_control", cacheControl);
        }
        return list;
    }

    private int findLastUserMessageIndex(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage) {
                return i;
            }
        }
        return -1;
    }

    private JsonObject buildCacheControl(String cacheRetention) {
        if (cacheRetention == null || cacheRetention.trim().isEmpty() || "none".equals(cacheRetention)) {
            return null;
        }
        JsonObject cacheControl = new JsonObject();
        cacheControl.addProperty("type", "ephemeral");
        if ("long".equals(cacheRetention)) {
            cacheControl.addProperty("ttl", "1h");
        }
        return cacheControl;
    }

    private JsonArray buildTools(List<Tool> tools) {
        JsonArray list = new JsonArray();
        for (Tool tool : tools) {
            JsonObject item = new JsonObject();
            item.addProperty("name", tool.getName());
            item.addProperty("description", tool.getDescription());
            item.add("input_schema", tool.getParameters());
            list.add(item);
        }
        return list;
    }

    @Override
    protected void parseSse(Reader reader, AssistantMessageEventStream stream, Model model, AbortHandle abortHandle) throws IOException {
        BufferedReader buffered = new BufferedReader(reader);
        String line;
        String eventName = null;
        StringBuilder data = new StringBuilder();
        ParseState state = new ParseState();
        stream.push(new StartEvent());

        while ((line = buffered.readLine()) != null) {
            abortHandle.throwIfAborted();
            if (line.isEmpty()) {
                dispatchEvent(stream, model, eventName, data.toString(), state);
                eventName = null;
                data.setLength(0);
                continue;
            }
            if (line.startsWith("event:")) {
                eventName = line.substring("event:".length()).trim();
                continue;
            }
            if (line.startsWith("data:")) {
                if (data.length() > 0) {
                    data.append('\n');
                }
                data.append(line.substring("data:".length()).trim());
            }
        }

        if (data.length() > 0) {
            dispatchEvent(stream, model, eventName, data.toString(), state);
        }

        if (!stream.isDone()) {
            AssistantMessage finalMessage = state.buildFinalMessage(getApi(), model.getProvider(), model.getId());
            stream.push(new DoneEvent(state.stopReason == null ? StopReason.STOP : state.stopReason, finalMessage));
            stream.end(finalMessage);
        }
    }

    private void dispatchEvent(
            AssistantMessageEventStream stream,
            Model model,
            String eventName,
            String payload,
            ParseState state) {
        if (payload == null || payload.isEmpty() || "[DONE]".equals(payload)) {
            return;
        }

        JsonObject data = JsonUtil.gson().fromJson(payload, JsonObject.class);
        String type = eventName != null ? eventName : data.get("type").getAsString();

        if ("message_start".equals(type)) {
            JsonObject message = data.getAsJsonObject("message");
            if (message != null && message.has("usage")) {
                JsonObject usage = message.getAsJsonObject("usage");
                state.usage = new Usage(
                        optInt(usage, "input_tokens"),
                        optInt(usage, "output_tokens"),
                        optInt(usage, "cache_read_input_tokens"),
                        optInt(usage, "cache_creation_input_tokens"),
                        optInt(usage, "input_tokens") + optInt(usage, "output_tokens"),
                        null);
            }
            return;
        }

        if ("content_block_start".equals(type)) {
            int index = data.get("index").getAsInt();
            JsonObject block = data.getAsJsonObject("content_block");
            String blockType = block.get("type").getAsString();
            state.blockTypes.put(index, blockType);
            if ("text".equals(blockType)) {
                state.text.put(index, new StringBuilder());
                stream.push(new TextStartEvent(index));
            } else if ("thinking".equals(blockType)) {
                state.thinking.put(index, new StringBuilder());
                stream.push(new ThinkingStartEvent(index));
            } else if ("tool_use".equals(blockType)) {
                ToolCallBuilder builder = new ToolCallBuilder();
                builder.id = block.get("id").getAsString();
                builder.name = block.get("name").getAsString();
                if (block.has("input") && block.get("input").isJsonObject()) {
                    builder.input.putAll(JsonUtil.gson().fromJson(block.getAsJsonObject("input"), Map.class));
                }
                state.tools.put(index, builder);
                stream.push(new ToolCallStartEvent(index));
            }
            return;
        }

        if ("content_block_delta".equals(type)) {
            int index = data.get("index").getAsInt();
            JsonObject delta = data.getAsJsonObject("delta");
            String deltaType = delta.get("type").getAsString();
            if ("text_delta".equals(deltaType)) {
                String piece = delta.get("text").getAsString();
                state.text.get(index).append(piece);
                stream.push(new TextDeltaEvent(index, piece, state.partial(getApi(), model.getProvider(), model.getId())));
            } else if ("thinking_delta".equals(deltaType)) {
                String piece = delta.get("thinking").getAsString();
                state.thinking.get(index).append(piece);
                stream.push(new ThinkingDeltaEvent(index, piece, state.partial(getApi(), model.getProvider(), model.getId())));
            } else if ("input_json_delta".equals(deltaType)) {
                String partialJson = delta.get("partial_json").getAsString();
                ToolCallBuilder tool = state.tools.get(index);
                tool.rawJson.append(partialJson);
                stream.push(new ToolCallDeltaEvent(index, partialJson, state.partial(getApi(), model.getProvider(), model.getId())));
            }
            return;
        }

        if ("content_block_stop".equals(type)) {
            int index = data.get("index").getAsInt();
            String blockType = state.blockTypes.get(index);
            if ("text".equals(blockType)) {
                state.blocks.add(new TextContent(state.text.get(index).toString()));
                stream.push(new TextEndEvent(index));
            } else if ("thinking".equals(blockType)) {
                state.blocks.add(new ThinkingContent(state.thinking.get(index).toString()));
                stream.push(new ThinkingEndEvent(index));
            } else if ("tool_use".equals(blockType)) {
                ToolCallBuilder tool = state.tools.get(index);
                if (tool.rawJson.length() > 0) {
                    JsonObject parsed = JsonUtil.gson().fromJson(tool.rawJson.toString(), JsonObject.class);
                    if (parsed != null) {
                        for (Map.Entry<String, JsonElement> entry : parsed.entrySet()) {
                            tool.input.put(entry.getKey(), JsonUtil.gson().fromJson(entry.getValue(), Object.class));
                        }
                    }
                }
                ToolCallContent toolCall = new ToolCallContent(tool.id, tool.name, tool.input);
                state.blocks.add(toolCall);
                stream.push(new ToolCallEndEvent(index, toolCall, state.partial(getApi(), model.getProvider(), model.getId())));
            }
            return;
        }

        if ("message_delta".equals(type)) {
            JsonObject delta = data.getAsJsonObject("delta");
            if (delta != null && delta.has("stop_reason") && !(delta.get("stop_reason") instanceof JsonNull)) {
                String reason = delta.get("stop_reason").getAsString();
                state.stopReason = toStopReason(reason);
            }
            return;
        }

        if ("message_stop".equals(type)) {
            AssistantMessage finalMessage = state.buildFinalMessage(getApi(), model.getProvider(), model.getId());
            stream.push(new DoneEvent(state.stopReason == null ? StopReason.STOP : state.stopReason, finalMessage));
            stream.end(finalMessage);
        }
    }

    private int optInt(JsonObject json, String field) {
        return json.has(field) ? json.get(field).getAsInt() : 0;
    }

    private StopReason toStopReason(String reason) {
        if (reason == null) {
            return StopReason.STOP;
        }
        if ("max_tokens".equals(reason)) {
            return StopReason.LENGTH;
        }
        if ("tool_use".equals(reason)) {
            return StopReason.TOOL_USE;
        }
        return StopReason.STOP;
    }

    private String trimTrailingSlash(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private static final class ParseState {
        private final List<ContentBlock> blocks = new ArrayList<ContentBlock>();
        private final Map<Integer, StringBuilder> text = new LinkedHashMap<Integer, StringBuilder>();
        private final Map<Integer, StringBuilder> thinking = new LinkedHashMap<Integer, StringBuilder>();
        private final Map<Integer, ToolCallBuilder> tools = new LinkedHashMap<Integer, ToolCallBuilder>();
        private final Map<Integer, String> blockTypes = new LinkedHashMap<Integer, String>();
        private StopReason stopReason;
        private Usage usage;

        private AssistantMessage partial(String api, String provider, String model) {
            return new AssistantMessage(new ArrayList<ContentBlock>(blocks), api, provider, model, usage, stopReason, null);
        }

        private AssistantMessage buildFinalMessage(String api, String provider, String model) {
            return new AssistantMessage(new ArrayList<ContentBlock>(blocks), api, provider, model, usage, stopReason, null);
        }
    }

    private static final class ToolCallBuilder {
        private String id;
        private String name;
        private StringBuilder rawJson = new StringBuilder();
        private Map<String, Object> input = new LinkedHashMap<String, Object>();
    }
}
