package com.pi4j.ai.provider.openai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pi4j.ai.provider.AbortHandle;
import com.pi4j.ai.provider.ApiProvider;
import com.pi4j.ai.provider.StreamOptions;
import com.pi4j.ai.stream.AssistantMessageEventStream;
import com.pi4j.ai.stream.DoneEvent;
import com.pi4j.ai.stream.ErrorEvent;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OpenAIResponsesProvider implements ApiProvider {
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");
    private static final String DEFAULT_BASE_URL = "https://api.openai.com";
    private static final String API_PATH = "/v1/responses";

    private final OkHttpClient client;

    public OpenAIResponsesProvider() {
        this(new OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build());
    }

    public OpenAIResponsesProvider(OkHttpClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public String getApi() {
        return "openai-responses";
    }

    @Override
    public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
        AssistantMessageEventStream stream = new AssistantMessageEventStream();
        CompletableFuture.runAsync(() -> invokeStream(stream, model, context, options));
        return stream;
    }

    private void invokeStream(AssistantMessageEventStream stream, Model model, Context context, StreamOptions options) {
        AbortHandle abortHandle = options.getAbortHandle();
        try {
            IOException parseError = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                Request request = buildRequest(model, context, options);
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IllegalStateException(buildHttpErrorMessage("OpenAI responses request failed", response));
                    }
                    if (response.body() == null) {
                        throw new IllegalStateException("OpenAI responses body is empty");
                    }
                    try {
                        parseSse(response.body().charStream(), stream, model, abortHandle);
                        return;
                    } catch (IOException ioException) {
                        parseError = ioException;
                        if (attempt == 1 || abortHandle.isAborted()) {
                            throw ioException;
                        }
                    }
                }
            }
            if (parseError != null) {
                throw parseError;
            }
        } catch (Exception ex) {
            AssistantMessage errorMessage = new AssistantMessage(
                    Collections.<ContentBlock>emptyList(),
                    getApi(),
                    model.getProvider(),
                    model.getId(),
                    null,
                    abortHandle.isAborted() ? StopReason.ABORTED : StopReason.ERROR,
                    ex.getMessage());
            stream.push(new ErrorEvent(errorMessage.getStopReason(), errorMessage));
            stream.error(ex);
        }
    }

    String buildHttpErrorMessage(String prefix, Response response) throws IOException {
        String body = response.body() == null ? "" : response.body().string();
        if (body.length() > 512) {
            body = body.substring(0, 512);
        }
        if (body.isEmpty()) {
            body = "(no body)";
        }
        return prefix + ": " + response.code() + " " + body;
    }

    Request buildRequest(Model model, Context context, StreamOptions options) {
        String apiKey = options.getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("apiKey is required");
        }

        String baseUrl = model.getBaseUrl() == null || model.getBaseUrl().trim().isEmpty()
                ? DEFAULT_BASE_URL
                : trimTrailingSlash(model.getBaseUrl());
        String url = baseUrl + API_PATH;
        boolean supportPromptCacheKey = supportsPromptCacheKey(baseUrl);

        JsonObject payload = new JsonObject();
        payload.addProperty("model", model.getId());
        payload.addProperty("stream", true);
        payload.addProperty("store", false);
        if (supportPromptCacheKey
                && options.getSessionId() != null
                && !options.getSessionId().trim().isEmpty()
                && !"none".equals(options.getCacheRetention())) {
            payload.addProperty("prompt_cache_key", options.getSessionId());
        }
        if (options.getMaxTokens() != null) {
            payload.addProperty("max_output_tokens", options.getMaxTokens());
        }
        if (options.getTemperature() != null) {
            payload.addProperty("temperature", options.getTemperature());
        }
        if (model.isReasoning()) {
            addReasoning(payload, options);
        }

        payload.add("input", buildInput(context.getMessages()));
        if (!context.getTools().isEmpty()) {
            payload.add("tools", buildTools(context.getTools()));
        }

        byte[] payloadBytes = JsonUtil.gson().toJson(payload).getBytes(StandardCharsets.UTF_8);
        RequestBody body = RequestBody.create(payloadBytes, JSON_MEDIA_TYPE);

        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(body)
                .header("content-type", "application/json")
                .header("authorization", "Bearer " + apiKey);

        for (Map.Entry<String, String> entry : model.getHeaders().entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : options.getHeaders().entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }

        return builder.build();
    }

    private void addReasoning(JsonObject payload, StreamOptions options) {
        String effort = normalizeReasoningEffort(options.getThinkingEffort());
        if (effort == null) {
            return;
        }
        JsonObject reasoning = new JsonObject();
        reasoning.addProperty("effort", effort);
        payload.add("reasoning", reasoning);
    }

    private String normalizeReasoningEffort(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || "off".equals(value) || "none".equals(value)) {
            return null;
        }
        switch (value) {
            case "minimal":
            case "low":
            case "medium":
            case "high":
            case "xhigh":
                return value;
            default:
                return null;
        }
    }

    private boolean supportsPromptCacheKey(String baseUrl) {
        return baseUrl.toLowerCase(Locale.ROOT).contains("api.openai.com");
    }

    private JsonArray buildInput(List<Message> messages) {
        JsonArray list = new JsonArray();
        for (Message message : messages) {
            if (message instanceof UserMessage) {
                UserMessage user = (UserMessage) message;
                JsonObject entry = new JsonObject();
                entry.addProperty("role", "user");
                entry.add("content", toResponseContent(user.getContent()));
                list.add(entry);
            } else if (message instanceof AssistantMessage) {
                AssistantMessage assistant = (AssistantMessage) message;
                JsonObject entry = new JsonObject();
                entry.addProperty("role", "assistant");
                JsonArray content = new JsonArray();
                for (ContentBlock block : assistant.getContent()) {
                    if (block instanceof TextContent) {
                        JsonObject text = new JsonObject();
                        text.addProperty("type", "output_text");
                        text.addProperty("text", ((TextContent) block).getText());
                        content.add(text);
                    } else if (block instanceof ToolCallContent) {
                        ToolCallContent toolCall = (ToolCallContent) block;
                        JsonObject function = new JsonObject();
                        function.addProperty("type", "function_call");
                        function.addProperty("id", toolCall.getId());
                        function.addProperty("call_id", toolCall.getId());
                        function.addProperty("name", toolCall.getName());
                        function.addProperty("arguments", JsonUtil.gson().toJson(toolCall.getArguments()));
                        content.add(function);
                    }
                }
                entry.add("content", content);
                list.add(entry);
            } else if (message instanceof ToolResultMessage) {
                ToolResultMessage toolResult = (ToolResultMessage) message;
                JsonObject entry = new JsonObject();
                entry.addProperty("type", "function_call_output");
                entry.addProperty("call_id", toolResult.getToolCallId());
                entry.addProperty("output", flattenText(toolResult.getContent()));
                list.add(entry);
            }
        }
        return list;
    }

    private JsonArray toResponseContent(List<ContentBlock> blocks) {
        JsonArray content = new JsonArray();
        for (ContentBlock block : blocks) {
            if (block instanceof TextContent) {
                JsonObject text = new JsonObject();
                text.addProperty("type", "input_text");
                text.addProperty("text", ((TextContent) block).getText());
                content.add(text);
            } else if (block instanceof ImageContent) {
                ImageContent image = (ImageContent) block;
                JsonObject imagePart = new JsonObject();
                imagePart.addProperty("type", "input_image");
                imagePart.addProperty("image_url", "data:" + image.getMimeType() + ";base64," + image.getData());
                content.add(imagePart);
            }
        }
        return content;
    }

    private JsonArray buildTools(List<Tool> tools) {
        JsonArray list = new JsonArray();
        for (Tool tool : tools) {
            JsonObject function = new JsonObject();
            function.addProperty("type", "function");
            function.addProperty("name", tool.getName());
            function.addProperty("description", tool.getDescription());
            function.add("parameters", tool.getParameters());
            list.add(function);
        }
        return list;
    }

    void parseSse(Reader reader, AssistantMessageEventStream stream, Model model, AbortHandle abortHandle) throws IOException {
        BufferedReader buffered = new BufferedReader(reader);
        String line;
        StringBuilder data = new StringBuilder();
        ParseState state = new ParseState();
        stream.push(new StartEvent());

        while ((line = buffered.readLine()) != null) {
            abortHandle.throwIfAborted();
            if (line.isEmpty()) {
                dispatchEvent(stream, model, data.toString(), state);
                data.setLength(0);
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
            dispatchEvent(stream, model, data.toString(), state);
        }

        finishAndComplete(stream, model, state);
    }

    private void dispatchEvent(AssistantMessageEventStream stream, Model model, String payload, ParseState state) {
        if (payload == null || payload.isEmpty() || "[DONE]".equals(payload)) {
            return;
        }

        JsonObject event = JsonUtil.gson().fromJson(payload, JsonObject.class);
        String type = event.has("type") ? event.get("type").getAsString() : "";

        if ("response.output_text.delta".equals(type)) {
            String delta = event.has("delta") ? event.get("delta").getAsString() : "";
            if (!state.textStarted) {
                state.textStarted = true;
                state.textIndex = state.nextIndex++;
                stream.push(new TextStartEvent(state.textIndex));
            }
            state.text.append(delta);
            stream.push(new TextDeltaEvent(state.textIndex, delta, state.partial(getApi(), model.getProvider(), model.getId())));
            return;
        }

        if ("response.reasoning_summary_text.delta".equals(type) || "response.reasoning.delta".equals(type)) {
            String delta = event.has("delta") ? event.get("delta").getAsString() : "";
            if (!state.thinkingStarted) {
                state.thinkingStarted = true;
                state.thinkingIndex = state.nextIndex++;
                stream.push(new ThinkingStartEvent(state.thinkingIndex));
            }
            state.thinking.append(delta);
            stream.push(new ThinkingDeltaEvent(
                    state.thinkingIndex,
                    delta,
                    state.partial(getApi(), model.getProvider(), model.getId())));
            return;
        }

        if ("response.output_item.added".equals(type) && event.has("item") && event.get("item").isJsonObject()) {
            JsonObject item = event.getAsJsonObject("item");
            if ("function_call".equals(getAsString(item, "type"))) {
                String itemId = getAsString(item, "id");
                String callId = getAsString(item, "call_id");
                String key = callId.isEmpty() ? itemId : callId;
                ToolBuilder builder = state.tools.get(key);
                if (builder == null) {
                    builder = new ToolBuilder();
                    builder.id = key.isEmpty() ? UUID.randomUUID().toString() : key;
                    builder.name = getAsString(item, "name");
                    builder.index = state.nextIndex++;
                    if (item.has("arguments") && !item.get("arguments").isJsonNull()) {
                        builder.arguments.append(item.get("arguments").getAsString());
                    }
                    state.tools.put(key, builder);
                    stream.push(new ToolCallStartEvent(builder.index));
                }
            }
            return;
        }

        if ("response.function_call_arguments.delta".equals(type)) {
            String key = getAsString(event, "call_id");
            if (key.isEmpty()) {
                key = getAsString(event, "item_id");
            }
            ToolBuilder builder = state.tools.get(key);
            if (builder != null) {
                String delta = getAsString(event, "delta");
                builder.arguments.append(delta);
                stream.push(new ToolCallDeltaEvent(
                        builder.index,
                        delta,
                        state.partial(getApi(), model.getProvider(), model.getId())));
            }
            return;
        }

        if ("response.completed".equals(type) && event.has("response") && event.get("response").isJsonObject()) {
            JsonObject response = event.getAsJsonObject("response");
            if (response.has("usage") && response.get("usage").isJsonObject()) {
                JsonObject usage = response.getAsJsonObject("usage");
                int input = optInt(usage, "input_tokens");
                int output = optInt(usage, "output_tokens");
                int cacheRead = optInt(usage, "input_tokens_details", "cached_tokens");
                int cacheWrite = optInt(usage, "input_tokens_details", "cached_tokens");
                int total = optInt(usage, "total_tokens");
                state.usage = new Usage(input, output, cacheRead, cacheWrite, total, null);
            }
            state.stopReason = StopReason.STOP;
            return;
        }

        if ("response.failed".equals(type)) {
            state.stopReason = StopReason.ERROR;
        }
    }

    private void finishAndComplete(AssistantMessageEventStream stream, Model model, ParseState state) {
        if (stream.isDone()) {
            return;
        }

        if (state.thinkingStarted) {
            state.blocks.add(new ThinkingContent(state.thinking.toString()));
            stream.push(new ThinkingEndEvent(state.thinkingIndex));
            state.thinkingStarted = false;
        }
        if (state.textStarted) {
            state.blocks.add(new TextContent(state.text.toString()));
            stream.push(new TextEndEvent(state.textIndex));
            state.textStarted = false;
        }

        for (ToolBuilder builder : state.tools.values()) {
            Map<String, Object> args = parseArgs(builder.arguments.toString());
            ToolCallContent toolCall = new ToolCallContent(builder.id, builder.name == null ? "tool" : builder.name, args);
            state.blocks.add(toolCall);
            stream.push(new ToolCallEndEvent(builder.index, toolCall, state.partial(getApi(), model.getProvider(), model.getId())));
        }

        AssistantMessage finalMessage = state.finalMessage(getApi(), model.getProvider(), model.getId());
        StopReason reason = state.stopReason == null ? StopReason.STOP : state.stopReason;
        stream.push(new DoneEvent(reason, finalMessage));
        stream.end(finalMessage);
    }

    private Map<String, Object> parseArgs(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        JsonObject object = JsonUtil.gson().fromJson(rawJson, JsonObject.class);
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            map.put(entry.getKey(), JsonUtil.gson().fromJson(entry.getValue(), Object.class));
        }
        return map;
    }

    private String flattenText(List<ContentBlock> content) {
        StringBuilder builder = new StringBuilder();
        for (ContentBlock block : content) {
            if (block instanceof TextContent) {
                builder.append(((TextContent) block).getText());
            } else if (block instanceof ThinkingContent) {
                builder.append(((ThinkingContent) block).getThinking());
            }
        }
        return builder.toString();
    }

    private String getAsString(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : "";
    }

    private int optInt(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsInt() : 0;
    }

    private int optInt(JsonObject object, String field, String nested) {
        if (!object.has(field) || !object.get(field).isJsonObject()) {
            return 0;
        }
        JsonObject inner = object.getAsJsonObject(field);
        return inner.has(nested) && !inner.get(nested).isJsonNull() ? inner.get(nested).getAsInt() : 0;
    }

    private String trimTrailingSlash(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private static final class ParseState {
        private final List<ContentBlock> blocks = new ArrayList<ContentBlock>();
        private final StringBuilder text = new StringBuilder();
        private final StringBuilder thinking = new StringBuilder();
        private final Map<String, ToolBuilder> tools = new LinkedHashMap<String, ToolBuilder>();
        private int nextIndex;
        private int textIndex = -1;
        private int thinkingIndex = -1;
        private boolean textStarted;
        private boolean thinkingStarted;
        private StopReason stopReason;
        private Usage usage;

        private AssistantMessage partial(String api, String provider, String model) {
            return new AssistantMessage(new ArrayList<ContentBlock>(blocks), api, provider, model, usage, stopReason, null);
        }

        private AssistantMessage finalMessage(String api, String provider, String model) {
            return new AssistantMessage(new ArrayList<ContentBlock>(blocks), api, provider, model, usage, stopReason, null);
        }
    }

    private static final class ToolBuilder {
        private String id;
        private String name;
        private int index;
        private StringBuilder arguments = new StringBuilder();
    }
}
