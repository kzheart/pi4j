package com.pi4j.ai.provider.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pi4j.ai.provider.AbortHandle;
import com.pi4j.ai.provider.StreamOptions;
import com.pi4j.ai.stream.AssistantMessageEventStream;
import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.Context;
import com.pi4j.ai.types.Message;
import com.pi4j.ai.types.Model;
import com.pi4j.ai.types.TextContent;
import com.pi4j.ai.types.ToolCallContent;
import com.pi4j.ai.types.ToolResultMessage;
import com.pi4j.ai.types.UserMessage;
import com.pi4j.ai.util.JsonUtil;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.Test;

class OpenAIResponsesProviderTest {

    @Test
    void buildRequestUsesResponseEndpoint() {
        OpenAIResponsesProvider provider = new OpenAIResponsesProvider(new OkHttpClient());
        Model model = new Model(
                "deepseek-chat",
                "DeepSeek Chat",
                "openai-responses",
                "deepseek",
                "https://api.deepseek.com",
                false,
                Arrays.asList("text"),
                null,
                64000,
                4096,
                Collections.<String, String>emptyMap());

        Context context = new Context(
                null,
                Collections.<Message>singletonList(new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("hi")))),
                Collections.emptyList());

        Request request = provider.buildRequest(model, context, StreamOptions.builder().apiKey("sk-test").build());

        assertEquals("https://api.deepseek.com/v1/responses", request.url().toString());
        assertEquals("Bearer sk-test", request.header("authorization"));
    }

    @Test
    void buildRequestUsesStrictJsonContentType() {
        OpenAIResponsesProvider provider = new OpenAIResponsesProvider(new OkHttpClient());
        Model model = new Model(
                "gpt-5.3-codex",
                "GPT-5.3 Codex",
                "openai-responses",
                "sub2api",
                "https://sub.kzheart.me",
                false,
                Arrays.asList("text"),
                null,
                128000,
                16384,
                Collections.<String, String>emptyMap());
        Context context = new Context(
                null,
                Collections.<Message>singletonList(new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("ACK")))),
                Collections.emptyList());

        Request request = provider.buildRequest(model, context, StreamOptions.builder().apiKey("sk-test").build());

        assertNotNull(request.body());
        assertEquals("application/json", request.body().contentType().toString());
        assertEquals("application/json", request.header("content-type"));
    }

    @Test
    void buildRequestAddsPromptCacheKeyWhenSessionEnabled() {
        OpenAIResponsesProvider provider = new OpenAIResponsesProvider(new OkHttpClient());
        Model model = new Model(
                "gpt-4.1",
                "GPT 4.1",
                "openai-responses",
                "openai",
                "https://api.openai.com",
                false,
                Arrays.asList("text"),
                null,
                64000,
                4096,
                Collections.<String, String>emptyMap());
        Context context = new Context(
                null,
                Collections.<Message>singletonList(new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("hi")))),
                Collections.emptyList());

        Request request = provider.buildRequest(model, context, StreamOptions.builder()
                .apiKey("sk-test")
                .cacheRetention("short")
                .sessionId("session-1")
                .build());
        JsonObject payload = readPayload(request);
        assertEquals("session-1", payload.get("prompt_cache_key").getAsString());
    }

    @Test
    void buildRequestOmitsPromptCacheKeyWhenCacheNone() {
        OpenAIResponsesProvider provider = new OpenAIResponsesProvider(new OkHttpClient());
        Model model = new Model(
                "gpt-4.1",
                "GPT 4.1",
                "openai-responses",
                "openai",
                "https://api.openai.com",
                false,
                Arrays.asList("text"),
                null,
                64000,
                4096,
                Collections.<String, String>emptyMap());
        Context context = new Context(
                null,
                Collections.<Message>singletonList(new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("hi")))),
                Collections.emptyList());

        Request request = provider.buildRequest(model, context, StreamOptions.builder()
                .apiKey("sk-test")
                .cacheRetention("none")
                .sessionId("session-1")
                .build());
        JsonObject payload = readPayload(request);
        assertTrue(!payload.has("prompt_cache_key"));
    }

    @Test
    void buildRequestOmitsPromptCacheKeyForCompatibleGateway() {
        OpenAIResponsesProvider provider = new OpenAIResponsesProvider(new OkHttpClient());
        Model model = new Model(
                "gpt-5.3-codex",
                "GPT-5.3 Codex",
                "openai-responses",
                "sub2api",
                "https://sub.kzheart.me",
                true,
                Arrays.asList("text"),
                null,
                128000,
                16384,
                Collections.<String, String>emptyMap());
        Context context = new Context(
                null,
                Collections.<Message>singletonList(new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("hi")))),
                Collections.emptyList());

        Request request = provider.buildRequest(model, context, StreamOptions.builder()
                .apiKey("sk-test")
                .cacheRetention("short")
                .sessionId("session-1")
                .build());
        JsonObject payload = readPayload(request);
        assertTrue(!payload.has("prompt_cache_key"));
    }

    @Test
    void buildRequestAddsReasoningEffortWhenProvided() {
        OpenAIResponsesProvider provider = new OpenAIResponsesProvider(new OkHttpClient());
        Model model = new Model(
                "gpt-5.3-codex",
                "GPT-5.3 Codex",
                "openai-responses",
                "sub2api",
                "https://sub.kzheart.me",
                true,
                Arrays.asList("text"),
                null,
                128000,
                16384,
                Collections.<String, String>emptyMap());
        Context context = new Context(
                null,
                Collections.<Message>singletonList(new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("hi")))),
                Collections.emptyList());

        Request request = provider.buildRequest(model, context, StreamOptions.builder()
                .apiKey("sk-test")
                .thinkingEffort("xhigh")
                .build());
        JsonObject payload = readPayload(request);
        assertEquals("xhigh", payload.getAsJsonObject("reasoning").get("effort").getAsString());
    }

    @Test
    void buildRequestPreservesToolCallAndItemIdentifiers() {
        OpenAIResponsesProvider provider = new OpenAIResponsesProvider(new OkHttpClient());
        Model model = new Model(
                "gpt-5.3-codex",
                "GPT-5.3 Codex",
                "openai-responses",
                "sub2api",
                "https://sub.kzheart.me",
                true,
                Arrays.asList("text"),
                null,
                128000,
                16384,
                Collections.<String, String>emptyMap());

        LinkedHashMap<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("category", "identity");
        AssistantMessage assistant = new AssistantMessage(
                Collections.<ContentBlock>singletonList(new ToolCallContent("call_123|fc_123", "memory_read", args)),
                "openai-responses",
                "sub2api",
                "gpt-5.3-codex",
                null,
                null,
                null);
        ToolResultMessage toolResult = new ToolResultMessage(
                "call_123|fc_123",
                "memory_read",
                Collections.<ContentBlock>singletonList(new TextContent("分类 identity 当前没有记录。")),
                null,
                false);
        Context context = new Context(
                null,
                Arrays.<Message>asList(
                        new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("你知道我叫什么吗"))),
                        assistant,
                        toolResult),
                Collections.emptyList());

        Request request = provider.buildRequest(model, context, StreamOptions.builder().apiKey("sk-test").build());
        JsonObject payload = readPayload(request);
        JsonArray input = payload.getAsJsonArray("input");
        assertNotNull(input);

        JsonObject functionCall = null;
        JsonObject functionOutput = null;
        for (JsonElement element : input) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            String type = item.has("type") && !item.get("type").isJsonNull()
                    ? item.get("type").getAsString()
                    : "";
            if ("function_call".equals(type)) {
                functionCall = item;
            } else if ("function_call_output".equals(type)) {
                functionOutput = item;
            }
        }

        assertNotNull(functionCall);
        assertNotNull(functionOutput);
        assertEquals("fc_123", functionCall.get("id").getAsString());
        assertEquals("call_123", functionCall.get("call_id").getAsString());
        assertEquals("call_123", functionOutput.get("call_id").getAsString());
    }

    @Test
    void parseSseParsesResponseEvents() throws Exception {
        OpenAIResponsesProvider provider = new OpenAIResponsesProvider(new OkHttpClient());
        Model model = new Model(
                "demo",
                "Demo",
                "openai-responses",
                "openai",
                "https://api.openai.com",
                false,
                Arrays.asList("text"),
                null,
                64000,
                4096,
                Collections.<String, String>emptyMap());

        String sse = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"hello\"}\n\n"
                + "data: {\"type\":\"response.output_item.added\",\"item\":{\"type\":\"function_call\",\"id\":\"item_1\",\"call_id\":\"call_1\",\"name\":\"sum\"}}\n\n"
                + "data: {\"type\":\"response.function_call_arguments.delta\",\"call_id\":\"call_1\",\"delta\":\"{\\\"a\\\":1,\\\"b\\\":2}\"}\n\n"
                + "data: {\"type\":\"response.completed\",\"response\":{\"usage\":{\"input_tokens\":10,\"output_tokens\":4,\"total_tokens\":14}}}\n\n";

        AssistantMessageEventStream stream = new AssistantMessageEventStream();
        provider.parseSse(new StringReader(sse), stream, model, new AbortHandle());

        assertNotNull(stream.result().get());
        assertTrue(stream.result().get().getContent().size() >= 1);
    }

    @Test
    void parseSseEncodesToolCallIdWithItemId() throws Exception {
        OpenAIResponsesProvider provider = new OpenAIResponsesProvider(new OkHttpClient());
        Model model = new Model(
                "demo",
                "Demo",
                "openai-responses",
                "openai",
                "https://api.openai.com",
                false,
                Arrays.asList("text"),
                null,
                64000,
                4096,
                Collections.<String, String>emptyMap());

        String sse = "data: {\"type\":\"response.output_item.added\",\"item\":{\"type\":\"function_call\",\"id\":\"fc_9\",\"call_id\":\"call_9\",\"name\":\"sum\"}}\n\n"
                + "data: {\"type\":\"response.function_call_arguments.delta\",\"call_id\":\"call_9\",\"delta\":\"{\\\"a\\\":1}\"}\n\n"
                + "data: {\"type\":\"response.completed\",\"response\":{\"usage\":{\"input_tokens\":2,\"output_tokens\":1,\"total_tokens\":3}}}\n\n";

        AssistantMessageEventStream stream = new AssistantMessageEventStream();
        provider.parseSse(new StringReader(sse), stream, model, new AbortHandle());

        AssistantMessage message = stream.result().get();
        ToolCallContent toolCall = null;
        for (ContentBlock block : message.getContent()) {
            if (block instanceof ToolCallContent) {
                toolCall = (ToolCallContent) block;
                break;
            }
        }
        assertNotNull(toolCall);
        assertEquals("call_9|fc_9", toolCall.getId());
    }

    @Test
    void buildHttpErrorMessageIncludesResponseBody() throws Exception {
        OpenAIResponsesProvider provider = new OpenAIResponsesProvider(new OkHttpClient());
        Request request = new Request.Builder().url("https://api.openai.com/v1/responses").build();
        Response response = new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(429)
                .message("Too Many Requests")
                .body(ResponseBody.create("{\"error\":\"rate limit\"}", okhttp3.MediaType.parse("application/json")))
                .build();

        String message = provider.buildHttpErrorMessage("OpenAI responses request failed", response);
        assertTrue(message.contains("429"));
        assertTrue(message.contains("rate limit"));
    }

    private JsonObject readPayload(Request request) {
        try {
            Buffer buffer = new Buffer();
            request.body().writeTo(buffer);
            return JsonUtil.gson().fromJson(buffer.readUtf8(), JsonObject.class);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
