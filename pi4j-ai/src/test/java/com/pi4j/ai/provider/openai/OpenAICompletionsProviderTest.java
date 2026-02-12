package com.pi4j.ai.provider.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pi4j.ai.provider.AbortHandle;
import com.pi4j.ai.provider.StreamOptions;
import com.pi4j.ai.stream.AssistantMessageEventStream;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.Context;
import com.pi4j.ai.types.Message;
import com.pi4j.ai.types.Model;
import com.pi4j.ai.types.TextContent;
import com.pi4j.ai.types.UserMessage;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import okhttp3.OkHttpClient;
import okhttp3.Request;
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

        Request request = provider.buildRequest(model, context, StreamOptions.builder().apiKey("sk-test").build());

        assertEquals("https://api.deepseek.com/v1/chat/completions", request.url().toString());
        assertEquals("Bearer sk-test", request.header("authorization"));
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
}
