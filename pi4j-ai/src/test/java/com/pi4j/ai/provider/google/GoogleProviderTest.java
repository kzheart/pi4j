package com.pi4j.ai.provider.google;

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

class GoogleProviderTest {

    @Test
    void buildRequestUsesGoogleSseEndpoint() {
        GoogleProvider provider = new GoogleProvider(new OkHttpClient());
        Model model = new Model(
                "gemini-2.5-flash",
                "Gemini",
                "google-generative-ai",
                "google",
                "https://generativelanguage.googleapis.com",
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

        Request request = provider.buildRequest(model, context, StreamOptions.builder().apiKey("google-key").build());
        assertEquals(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse&key=google-key",
                request.url().toString());
    }

    @Test
    void parseSseParsesTextCandidate() throws Exception {
        GoogleProvider provider = new GoogleProvider(new OkHttpClient());
        Model model = new Model(
                "gemini-2.5-flash",
                "Gemini",
                "google-generative-ai",
                "google",
                "https://generativelanguage.googleapis.com",
                false,
                Arrays.asList("text"),
                null,
                64000,
                4096,
                Collections.<String, String>emptyMap());

        String sse = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"hello\"}]},\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":4,\"candidatesTokenCount\":2,\"totalTokenCount\":6}}\n\n";

        AssistantMessageEventStream stream = new AssistantMessageEventStream();
        provider.parseSse(new StringReader(sse), stream, model, new AbortHandle());

        assertNotNull(stream.result().get());
        assertTrue(stream.result().get().getContent().size() >= 1);
    }
}
