package com.pi4j.ai.provider.anthropic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pi4j.ai.provider.AbortHandle;
import com.pi4j.ai.provider.StreamOptions;
import com.pi4j.ai.stream.AssistantMessageEvent;
import com.pi4j.ai.stream.AssistantMessageEventStream;
import com.pi4j.ai.types.Context;
import com.pi4j.ai.types.Message;
import com.pi4j.ai.types.Model;
import com.pi4j.ai.types.TextContent;
import com.pi4j.ai.types.UserMessage;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.Test;

class AnthropicProviderTest {

    @Test
    void buildRequestUsesDeepSeekCompatBaseUrl() {
        AnthropicProvider provider = new AnthropicProvider(new OkHttpClient());
        Model model = new Model(
                "deepseek-chat",
                "DeepSeek",
                "anthropic-messages",
                "deepseek",
                "https://api.deepseek.com/anthropic",
                false,
                Arrays.asList("text"),
                null,
                64000,
                2048,
                Collections.<String, String>emptyMap());

        Context context = new Context(
                null,
                Collections.<Message>singletonList(new UserMessage(Collections.singletonList(new TextContent("hi")))),
                Collections.emptyList());

        StreamOptions options = StreamOptions.builder().apiKey("sk-test").build();
        Request request = provider.buildRequest(model, context, options);

        assertEquals("https://api.deepseek.com/anthropic/v1/messages", request.url().toString());
        assertEquals("sk-test", request.header("x-api-key"));
    }

    @Test
    void parseSseBuildsAssistantMessage() throws Exception {
        AnthropicProvider provider = new AnthropicProvider(new OkHttpClient());
        Model model = new Model(
                "demo-model",
                "Demo",
                "anthropic-messages",
                "anthropic",
                "https://api.anthropic.com",
                false,
                Arrays.asList("text"),
                null,
                64000,
                2048,
                new LinkedHashMap<String, String>());

        String sse = "event: message_start\n"
                + "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":10,\"output_tokens\":2}}}\n\n"
                + "event: content_block_start\n"
                + "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\"}}\n\n"
                + "event: content_block_delta\n"
                + "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"hello\"}}\n\n"
                + "event: content_block_stop\n"
                + "data: {\"type\":\"content_block_stop\",\"index\":0}\n\n"
                + "event: message_delta\n"
                + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}\n\n"
                + "event: message_stop\n"
                + "data: {\"type\":\"message_stop\"}\n\n";

        AssistantMessageEventStream stream = new AssistantMessageEventStream();
        List<AssistantMessageEvent> events = new ArrayList<AssistantMessageEvent>();
        stream.subscribe(events::add);

        provider.parseSse(new StringReader(sse), stream, model, new AbortHandle());

        assertTrue(events.size() >= 3);
        assertNotNull(stream.result().get());
        assertEquals(1, stream.result().get().getContent().size());
        TextContent text = (TextContent) stream.result().get().getContent().get(0);
        assertEquals("hello", text.getText());
    }
}
