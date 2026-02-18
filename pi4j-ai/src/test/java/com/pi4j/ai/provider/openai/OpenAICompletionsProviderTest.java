package com.pi4j.ai.provider.openai;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

class OpenAICompletionsProviderTest {

    @Test
    void buildRequestSupportsDeepSeekOpenAiCompatBaseUrl() {
        OpenAICompletionsProvider provider = new OpenAICompletionsProvider(new OkHttpClient());
        Model model = new Model(
                "deepseek-chat",
                "DeepSeek Chat",
                "openai-completions",
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

        Request request = provider.buildRequest(model, context, StreamOptions.builder()
                .apiKey("sk-test")
                .maxTokens(128)
                .build());
        JsonObject payload = readPayload(request);

        assertEquals("https://api.deepseek.com/v1/chat/completions", request.url().toString());
        assertEquals("Bearer sk-test", request.header("authorization"));
        assertTrue(payload.has("max_completion_tokens"));
    }

    @Test
    void buildRequestUsesMistralCompatFields() {
        OpenAICompletionsProvider provider = new OpenAICompletionsProvider(new OkHttpClient());
        Model model = new Model(
                "mistral-small",
                "Mistral Small",
                "openai-completions",
                "mistral",
                "https://api.mistral.ai",
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
                .maxTokens(256)
                .thinkingEffort("medium")
                .build());

        JsonObject payload = readPayload(request);
        assertTrue(payload.has("max_tokens"));
        assertFalse(payload.has("max_completion_tokens"));
        assertTrue(payload.has("reasoning_effort"));
    }

    @Test
    void buildRequestIncludesResponseFormatWhenRequested() {
        OpenAICompletionsProvider provider = new OpenAICompletionsProvider(new OkHttpClient());
        Model model = new Model(
                "deepseek-chat",
                "DeepSeek Chat",
                "openai-completions",
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

        Request request = provider.buildRequest(model, context, StreamOptions.builder()
                .apiKey("sk-test")
                .responseFormat("json_object")
                .build());

        JsonObject payload = readPayload(request);
        assertTrue(payload.has("response_format"));
        assertEquals("json_object", payload.getAsJsonObject("response_format").get("type").getAsString());
    }

    @Test
    void buildRequestIncludesJsonSchemaResponseFormat() {
        OpenAICompletionsProvider provider = new OpenAICompletionsProvider(new OkHttpClient());
        Model model = new Model(
                "qwen3.5-plus",
                "Qwen 3.5 Plus",
                "openai-completions",
                "bailian",
                "https://dashscope.aliyuncs.com/compatible-mode",
                false,
                Arrays.asList("text"),
                null,
                131072,
                8192,
                Collections.<String, String>emptyMap());

        Context context = new Context(
                null,
                Collections.<Message>singletonList(new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("hi")))),
                Collections.emptyList());

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "json_schema");
        JsonObject jsonSchema = new JsonObject();
        jsonSchema.addProperty("name", "my_schema");
        schema.add("json_schema", jsonSchema);

        Request request = provider.buildRequest(model, context, StreamOptions.builder()
                .apiKey("sk-test")
                .responseFormat(schema)
                .build());

        JsonObject payload = readPayload(request);
        assertTrue(payload.has("response_format"));
        assertEquals("json_schema", payload.getAsJsonObject("response_format").get("type").getAsString());
        assertTrue(payload.getAsJsonObject("response_format").has("json_schema"));
    }

    @Test
    void buildRequestFallsBackJsonSchemaToJsonObjectForDeepSeek() {
        OpenAICompletionsProvider provider = new OpenAICompletionsProvider(new OkHttpClient());
        Model model = new Model(
                "deepseek-chat",
                "DeepSeek Chat",
                "openai-completions",
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

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "json_schema");
        JsonObject jsonSchema = new JsonObject();
        jsonSchema.addProperty("name", "my_schema");
        schema.add("json_schema", jsonSchema);

        Request request = provider.buildRequest(model, context, StreamOptions.builder()
                .apiKey("sk-test")
                .responseFormat(schema)
                .build());

        JsonObject payload = readPayload(request);
        assertTrue(payload.has("response_format"));
        assertEquals("json_object", payload.getAsJsonObject("response_format").get("type").getAsString());
        assertFalse(payload.getAsJsonObject("response_format").has("json_schema"));
    }

    @Test
    void parseSseParsesTextAndToolCall() throws Exception {
        OpenAICompletionsProvider provider = new OpenAICompletionsProvider(new OkHttpClient());
        Model model = new Model(
                "demo",
                "Demo",
                "openai-completions",
                "openai",
                "https://api.openai.com",
                false,
                Arrays.asList("text"),
                null,
                64000,
                4096,
                Collections.<String, String>emptyMap());

        String sse = "data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"function\":{\"name\":\"sum\",\"arguments\":\"{\\\"a\\\":1\"}}]}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\",\\\"b\\\":2}\"}}]},\"finish_reason\":\"tool_calls\"}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":3}}\n\n"
                + "data: [DONE]\n\n";

        AssistantMessageEventStream stream = new AssistantMessageEventStream();
        provider.parseSse(new StringReader(sse), stream, model, new AbortHandle());

        assertNotNull(stream.result().get());
        assertTrue(stream.result().get().getContent().size() >= 2);
    }

    @Test
    void buildHttpErrorMessageIncludesResponseBody() throws Exception {
        OpenAICompletionsProvider provider = new OpenAICompletionsProvider(new OkHttpClient());
        Request request = new Request.Builder().url("https://api.openai.com/v1/chat/completions").build();
        Response response = new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(400)
                .message("Bad Request")
                .body(ResponseBody.create("{\"error\":\"bad request\"}", okhttp3.MediaType.parse("application/json")))
                .build();

        String message = provider.buildHttpErrorMessage("OpenAI request failed", response);
        assertTrue(message.contains("400"));
        assertTrue(message.contains("bad request"));
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
