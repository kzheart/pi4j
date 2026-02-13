package com.pi4j.ai.provider.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.pi4j.ai.provider.AbortHandle;
import com.pi4j.ai.provider.StreamOptions;
import com.pi4j.ai.stream.AssistantMessageEventStream;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.Context;
import com.pi4j.ai.types.Message;
import com.pi4j.ai.types.Model;
import com.pi4j.ai.types.TextContent;
import com.pi4j.ai.types.UserMessage;
import com.pi4j.ai.util.JsonUtil;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
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
