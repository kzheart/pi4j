package com.pi4j.ai.provider.openai;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pi4j.ai.provider.AbortHandle;
import com.pi4j.ai.provider.AbstractHttpSseProvider;
import com.pi4j.ai.provider.ProviderCompat;
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

public class OpenAICompletionsProvider extends AbstractHttpSseProvider {
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");
    private static final String DEFAULT_BASE_URL = "https://api.openai.com";
    private static final String API_PATH = "/v1/chat/completions";

    public OpenAICompletionsProvider() {
        this(new OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build());
    }

    public OpenAICompletionsProvider(OkHttpClient client) {
        super(client);
    }

    @Override
    public String getApi() {
        return "openai-completions";
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
        ProviderCompat.OpenAiCompletionsCompat compat = ProviderCompat.detectOpenAiCompletionsCompat(model);
        payload.addProperty("model", model.getId());
        payload.addProperty("stream", true);
        if (options.getMaxTokens() != null) {
            payload.addProperty(compat.getMaxTokensField(), options.getMaxTokens());
        }
        if (options.getTemperature() != null) {
            payload.addProperty("temperature", options.getTemperature());
        }
        if (options.getResponseFormat() != null && compat.getResponseFormatLevel() != ProviderCompat.ResponseFormatLevel.NONE) {
            JsonObject responseFormat = options.getResponseFormat();
            String formatType = responseFormat.has("type") ? responseFormat.get("type").getAsString() : null;
            if ("json_schema".equals(formatType) && compat.getResponseFormatLevel() == ProviderCompat.ResponseFormatLevel.JSON_OBJECT_ONLY) {
                JsonObject fallback = new JsonObject();
                fallback.addProperty("type", "json_object");
                payload.add("response_format", fallback);
            } else {
                payload.add("response_format", responseFormat);
            }
        }
        if (options.getThinkingEffort() != null && !options.getThinkingEffort().isEmpty() && compat.isSupportsReasoningEffort()) {
            if ("zai".equals(compat.getThinkingFormat())) {
                payload.addProperty("reasoning", options.getThinkingEffort());
            } else if ("bailian".equals(compat.getThinkingFormat())) {
                payload.addProperty("enable_thinking", true);
            } else {
                payload.addProperty("reasoning_effort", normalizeReasoningEffort(options.getThinkingEffort()));
            }
        }
        if ("bailian".equals(compat.getThinkingFormat())
                && (options.getThinkingEffort() == null || options.getThinkingEffort().isEmpty())) {
            payload.addProperty("enable_thinking", false);
        }
        if ("deepseek".equals(compat.getThinkingFormat())) {
            JsonObject thinking = new JsonObject();
            thinking.addProperty(
                    "type",
                    options.getThinkingEffort() == null || options.getThinkingEffort().isEmpty()
                            ? "disabled"
                            : "enabled");
            payload.add("thinking", thinking);
        }
        payload.add("messages", buildMessages(context.getSystemPrompt(), context.getMessages(), compat));
        if (!context.getTools().isEmpty()) {
            payload.add("tools", buildTools(context.getTools()));
        }

        if (options.getToolChoice() != null) {
            if ("auto".equals(options.getToolChoice())
                    || "none".equals(options.getToolChoice())
                    || "required".equals(options.getToolChoice())) {
                payload.addProperty("tool_choice", options.getToolChoice());
            } else {
                JsonObject toolChoice = new JsonObject();
                toolChoice.addProperty("type", "function");
                JsonObject function = new JsonObject();
                function.addProperty("name", options.getToolChoice());
                toolChoice.add("function", function);
                payload.add("tool_choice", toolChoice);
            }
        }

        RequestBody body = RequestBody.create(JSON_MEDIA_TYPE, JsonUtil.gson().toJson(payload));
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

    private JsonArray buildMessages(String systemPrompt, List<Message> messages, ProviderCompat.OpenAiCompletionsCompat compat) {
        JsonArray list = new JsonArray();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject system = new JsonObject();
            system.addProperty("role", "system");
            system.addProperty("content", systemPrompt);
            list.add(system);
        }
        for (Message message : messages) {
            if (message instanceof UserMessage) {
                UserMessage user = (UserMessage) message;
                JsonObject item = new JsonObject();
                item.addProperty("role", "user");
                item.add("content", toOpenAiContent(user.getContent()));
                list.add(item);
            } else if (message instanceof AssistantMessage) {
                AssistantMessage assistant = (AssistantMessage) message;
                JsonObject item = new JsonObject();
                item.addProperty("role", "assistant");

                JsonArray toolCalls = new JsonArray();
                StringBuilder text = new StringBuilder();
                for (ContentBlock block : assistant.getContent()) {
                    if (block instanceof ToolCallContent) {
                        ToolCallContent toolCall = (ToolCallContent) block;
                        JsonObject toolCallJson = new JsonObject();
                        toolCallJson.addProperty("id", toolCall.getId());
                        toolCallJson.addProperty("type", "function");
                        JsonObject fn = new JsonObject();
                        fn.addProperty("name", toolCall.getName());
                        fn.addProperty("arguments", JsonUtil.gson().toJson(toolCall.getArguments()));
                        toolCallJson.add("function", fn);
                        toolCalls.add(toolCallJson);
                    } else if (block instanceof TextContent) {
                        text.append(((TextContent) block).getText());
                    } else if (block instanceof ThinkingContent) {
                        text.append(((ThinkingContent) block).getThinking());
                    }
                }

                if (toolCalls.size() > 0) {
                    item.add("tool_calls", toolCalls);
                    item.addProperty("content", text.length() == 0 ? "" : text.toString());
                } else {
                    item.addProperty("content", text.toString());
                }
                list.add(item);
            } else if (message instanceof ToolResultMessage) {
                ToolResultMessage tool = (ToolResultMessage) message;
                JsonObject item = new JsonObject();
                item.addProperty("role", "tool");
                item.addProperty("tool_call_id", tool.getToolCallId());
                if (compat.isRequiresToolResultName()) {
                    item.addProperty("name", tool.getToolName());
                }
                item.addProperty("content", flattenText(tool.getContent()));
                list.add(item);
            }
        }
        return list;
    }

    private JsonElement toOpenAiContent(List<ContentBlock> blocks) {
        boolean hasImage = false;
        for (ContentBlock block : blocks) {
            if (block instanceof ImageContent) {
                hasImage = true;
                break;
            }
        }

        if (!hasImage) {
            return JsonUtil.gson().toJsonTree(flattenText(blocks));
        }

        JsonArray content = new JsonArray();
        for (ContentBlock block : blocks) {
            if (block instanceof TextContent) {
                JsonObject text = new JsonObject();
                text.addProperty("type", "text");
                text.addProperty("text", ((TextContent) block).getText());
                content.add(text);
            } else if (block instanceof ImageContent) {
                ImageContent image = (ImageContent) block;
                JsonObject imageUrl = new JsonObject();
                imageUrl.addProperty("url", "data:" + image.getMimeType() + ";base64," + image.getData());
                JsonObject imagePart = new JsonObject();
                imagePart.addProperty("type", "image_url");
                imagePart.add("image_url", imageUrl);
                content.add(imagePart);
            }
        }
        return content;
    }

    private JsonArray buildTools(List<Tool> tools) {
        JsonArray list = new JsonArray();
        for (Tool tool : tools) {
            JsonObject item = new JsonObject();
            item.addProperty("type", "function");
            JsonObject function = new JsonObject();
            function.addProperty("name", tool.getName());
            function.addProperty("description", tool.getDescription());
            function.add("parameters", tool.getParameters());
            item.add("function", function);
            list.add(item);
        }
        return list;
    }

    @Override
    protected void parseSse(Reader reader, AssistantMessageEventStream stream, Model model, AbortHandle abortHandle) throws IOException {
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
                String value = line.substring("data:".length()).trim();
                if (data.length() > 0) {
                    data.append('\n');
                }
                data.append(value);
            }
        }

        if (data.length() > 0) {
            dispatchEvent(stream, model, data.toString(), state);
        }

        finishAndComplete(stream, model, state);
    }

    private void dispatchEvent(AssistantMessageEventStream stream, Model model, String payload, ParseState state) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        if ("[DONE]".equals(payload)) {
            finishAndComplete(stream, model, state);
            return;
        }

        JsonObject root = JsonUtil.gson().fromJson(payload, JsonObject.class);

        if (root.has("usage") && root.get("usage").isJsonObject()) {
            JsonObject usage = root.getAsJsonObject("usage");
            int cachedTokens = 0;
            if (usage.has("prompt_tokens_details") && usage.get("prompt_tokens_details").isJsonObject()) {
                JsonObject details = usage.getAsJsonObject("prompt_tokens_details");
                cachedTokens = optInt(details, "cached_tokens");
            }
            int input = optInt(usage, "prompt_tokens") - cachedTokens;
            int output = optInt(usage, "completion_tokens");
            state.usage = new Usage(input, output, cachedTokens, 0, input + output + cachedTokens, null);
        }

        if (!root.has("choices") || !root.get("choices").isJsonArray() || root.getAsJsonArray("choices").size() == 0) {
            return;
        }

        JsonObject choice = root.getAsJsonArray("choices").get(0).getAsJsonObject();
        if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()) {
            state.stopReason = mapStopReason(choice.get("finish_reason").getAsString());
        }

        if (!choice.has("delta") || !choice.get("delta").isJsonObject()) {
            return;
        }

        JsonObject delta = choice.getAsJsonObject("delta");
        if (delta.has("content") && !delta.get("content").isJsonNull()) {
            if (!state.textStarted) {
                state.textStarted = true;
                stream.push(new TextStartEvent(state.nextContentIndex()));
            }
            String piece = delta.get("content").getAsString();
            state.text.append(piece);
            stream.push(new TextDeltaEvent(state.currentTextIndex(), piece, state.partial(getApi(), model.getProvider(), model.getId())));
        }

        String thinkingDelta = firstNonEmpty(delta, "reasoning_content", "reasoning", "reasoning_text");
        if (thinkingDelta != null) {
            if (!state.thinkingStarted) {
                state.thinkingStarted = true;
                stream.push(new ThinkingStartEvent(state.nextContentIndex()));
            }
            state.thinking.append(thinkingDelta);
            stream.push(new ThinkingDeltaEvent(
                    state.currentThinkingIndex(),
                    thinkingDelta,
                    state.partial(getApi(), model.getProvider(), model.getId())));
        }

        if (delta.has("tool_calls") && delta.get("tool_calls").isJsonArray()) {
            JsonArray toolCalls = delta.getAsJsonArray("tool_calls");
            for (JsonElement element : toolCalls) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject call = element.getAsJsonObject();
                int index = call.has("index") ? call.get("index").getAsInt() : 0;
                ToolBuilder builder = state.toolBuilders.get(index);
                if (builder == null) {
                    builder = new ToolBuilder();
                    state.toolBuilders.put(index, builder);
                    builder.eventIndex = state.nextContentIndex();
                    stream.push(new ToolCallStartEvent(builder.eventIndex));
                }
                if (call.has("id") && !call.get("id").isJsonNull()) {
                    builder.id = call.get("id").getAsString();
                }
                if (call.has("function") && call.get("function").isJsonObject()) {
                    JsonObject function = call.getAsJsonObject("function");
                    if (function.has("name") && !function.get("name").isJsonNull()) {
                        builder.name = function.get("name").getAsString();
                    }
                    if (function.has("arguments") && !function.get("arguments").isJsonNull()) {
                        String argDelta = function.get("arguments").getAsString();
                        builder.rawArguments.append(argDelta);
                        stream.push(new ToolCallDeltaEvent(
                                builder.eventIndex,
                                argDelta,
                                state.partial(getApi(), model.getProvider(), model.getId())));
                    }
                }
            }
        }
    }

    private void finishAndComplete(AssistantMessageEventStream stream, Model model, ParseState state) {
        if (stream.isDone()) {
            return;
        }

        if (state.textStarted) {
            state.blocks.add(new TextContent(state.text.toString()));
            stream.push(new TextEndEvent(state.currentTextIndex()));
            state.textStarted = false;
        }
        if (state.thinkingStarted) {
            state.blocks.add(new ThinkingContent(state.thinking.toString()));
            stream.push(new ThinkingEndEvent(state.currentThinkingIndex()));
            state.thinkingStarted = false;
        }

        for (ToolBuilder builder : state.toolBuilders.values()) {
            if (builder.id == null || builder.id.isEmpty()) {
                builder.id = "tool_" + builder.eventIndex;
            }
            if (builder.name == null || builder.name.isEmpty()) {
                builder.name = "tool";
            }
            Map<String, Object> args = parseArgs(builder.rawArguments.toString());
            ToolCallContent toolCall = new ToolCallContent(builder.id, builder.name, args);
            state.blocks.add(toolCall);
            stream.push(new ToolCallEndEvent(builder.eventIndex, toolCall, state.partial(getApi(), model.getProvider(), model.getId())));
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

    private StopReason mapStopReason(String value) {
        if ("length".equals(value)) {
            return StopReason.LENGTH;
        }
        if ("tool_calls".equals(value) || "function_call".equals(value)) {
            return StopReason.TOOL_USE;
        }
        if ("content_filter".equals(value)) {
            return StopReason.ERROR;
        }
        return StopReason.STOP;
    }

    private int optInt(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsInt() : 0;
    }

    private String firstNonEmpty(JsonObject object, String... fields) {
        for (String field : fields) {
            if (object.has(field) && !object.get(field).isJsonNull()) {
                String value = object.get(field).getAsString();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    private String trimTrailingSlash(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private String normalizeReasoningEffort(String effort) {
        return "basic".equalsIgnoreCase(effort) ? "low" : effort;
    }

    private static final class ParseState {
        private final List<ContentBlock> blocks = new ArrayList<ContentBlock>();
        private final StringBuilder text = new StringBuilder();
        private final StringBuilder thinking = new StringBuilder();
        private final Map<Integer, ToolBuilder> toolBuilders = new LinkedHashMap<Integer, ToolBuilder>();
        private Usage usage;
        private StopReason stopReason;
        private boolean textStarted;
        private boolean thinkingStarted;
        private int textIndex = -1;
        private int thinkingIndex = -1;
        private int nextIndex;

        private int nextContentIndex() {
            return nextIndex++;
        }

        private int currentTextIndex() {
            if (textIndex == -1) {
                textIndex = nextIndex - 1;
            }
            return textIndex;
        }

        private int currentThinkingIndex() {
            if (thinkingIndex == -1) {
                thinkingIndex = nextIndex - 1;
            }
            return thinkingIndex;
        }

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
        private int eventIndex;
        private StringBuilder rawArguments = new StringBuilder();
    }
}
