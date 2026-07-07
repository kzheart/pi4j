package com.pi4j.ai.provider.google;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pi4j.ai.provider.AbortHandle;
import com.pi4j.ai.provider.ApiProvider;
import com.pi4j.ai.provider.StreamOptions;
import com.pi4j.ai.stream.AssistantMessageEventStream;
import com.pi4j.ai.stream.DoneEvent;
import com.pi4j.ai.stream.ErrorEvent;
import com.pi4j.ai.stream.StartEvent;
import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.Context;
import com.pi4j.ai.types.Model;
import com.pi4j.ai.types.StopReason;
import com.pi4j.ai.util.JsonUtil;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GoogleProvider implements ApiProvider {
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";

    private final OkHttpClient client;

    public GoogleProvider() {
        this(new OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build());
    }

    public GoogleProvider(OkHttpClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public String getApi() {
        return "google-generative-ai";
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
            Request request = buildRequest(model, context, options);
            Call call = client.newCall(request);
            Runnable cancelOnAbort = call::cancel;
            if (abortHandle != null) {
                abortHandle.addListener(cancelOnAbort);
                if (abortHandle.isAborted()) {
                    call.cancel();
                }
            }
            try (Response response = call.execute()) {
                if (!response.isSuccessful()) {
                    throw new IllegalStateException("Google request failed: " + response.code());
                }
                if (response.body() == null) {
                    throw new IllegalStateException("Google response body is empty");
                }
                parseSse(response.body().charStream(), stream, model, abortHandle);
            } finally {
                if (abortHandle != null) {
                    abortHandle.removeListener(cancelOnAbort);
                }
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

    Request buildRequest(Model model, Context context, StreamOptions options) {
        String apiKey = options.getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("apiKey is required");
        }

        String baseUrl = model.getBaseUrl() == null || model.getBaseUrl().trim().isEmpty()
                ? DEFAULT_BASE_URL
                : trimTrailingSlash(model.getBaseUrl());
        String url = buildStreamUrl(baseUrl, model.getId(), apiKey);

        JsonObject payload = new JsonObject();
        payload.add("contents", GoogleShared.buildContents(context.getMessages()));
        JsonArray tools = GoogleShared.buildTools(context.getTools());
        if (tools.size() > 0) {
            payload.add("tools", tools);
        }
        JsonObject generationConfig = new JsonObject();
        if (options.getMaxTokens() != null) {
            generationConfig.addProperty("maxOutputTokens", options.getMaxTokens());
        }
        if (options.getTemperature() != null) {
            generationConfig.addProperty("temperature", options.getTemperature());
        }
        if (generationConfig.size() > 0) {
            payload.add("generationConfig", generationConfig);
        }

        RequestBody body = RequestBody.create(JsonUtil.gson().toJson(payload), JSON_MEDIA_TYPE);

        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(body)
                .header("content-type", "application/json");
        for (Map.Entry<String, String> entry : model.getHeaders().entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : options.getHeaders().entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }

        return builder.build();
    }

    void parseSse(Reader reader, AssistantMessageEventStream stream, Model model, AbortHandle abortHandle) throws IOException {
        GoogleShared.ParseState state = new GoogleShared.ParseState();
        stream.push(new StartEvent());

        BufferedReader buffered = new BufferedReader(reader);
        String line;
        StringBuilder data = new StringBuilder();

        while ((line = buffered.readLine()) != null) {
            abortHandle.throwIfAborted();
            if (line.isEmpty()) {
                dispatchChunk(stream, model, state, data.toString());
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
            dispatchChunk(stream, model, state, data.toString());
        }

        GoogleShared.finishParse(getApi(), model, state, stream);
        AssistantMessage finalMessage = state.finalMessage(getApi(), model);
        StopReason reason = finalMessage.getStopReason() == null ? StopReason.STOP : finalMessage.getStopReason();
        stream.push(new DoneEvent(reason, finalMessage));
        stream.end(finalMessage);
    }

    protected String buildStreamUrl(String baseUrl, String modelId, String apiKey) {
        if (baseUrl.contains(":streamGenerateContent")) {
            return baseUrl + (baseUrl.contains("?") ? "&" : "?") + "alt=sse&key=" + apiKey;
        }
        return baseUrl + "/v1beta/models/" + modelId + ":streamGenerateContent?alt=sse&key=" + apiKey;
    }

    private void dispatchChunk(
            AssistantMessageEventStream stream,
            Model model,
            GoogleShared.ParseState state,
            String payload) {
        if (payload == null || payload.isEmpty() || "[DONE]".equals(payload)) {
            return;
        }
        JsonObject root = JsonUtil.gson().fromJson(payload, JsonObject.class);
        GoogleShared.handleChunk(root, getApi(), model, state, stream);
    }

    protected String trimTrailingSlash(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
