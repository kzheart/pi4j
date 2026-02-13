package com.pi4j.ai.provider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.pi4j.ai.provider.anthropic.AnthropicProvider;
import com.pi4j.ai.provider.openai.OpenAICompletionsProvider;
import com.pi4j.ai.stream.AssistantMessageEventStream;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.Context;
import com.pi4j.ai.types.Message;
import com.pi4j.ai.types.Model;
import com.pi4j.ai.types.TextContent;
import com.pi4j.ai.types.UserMessage;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class DeepSeekIntegrationTest {
    private static final String DEEPSEEK_API_KEY_ENV = "DEEPSEEK_API_KEY";
    private static final String DEEPSEEK_OPENAI_BASE_URL = "https://api.deepseek.com";
    private static final String DEEPSEEK_ANTHROPIC_BASE_URL = "https://api.deepseek.com/anthropic";

    @Test
    void anthropicCompatWorksWithDeepSeekApiKey() throws Exception {
        String apiKey = requireApiKey();

        AnthropicProvider provider = new AnthropicProvider();
        Model model = new Model(
                "deepseek-chat",
                "DeepSeek Chat",
                "anthropic-messages",
                "deepseek",
                DEEPSEEK_ANTHROPIC_BASE_URL,
                false,
                Arrays.asList("text"),
                null,
                64000,
                512,
                Collections.<String, String>emptyMap());

        Context context = new Context(
                "You are a concise assistant.",
                Collections.<Message>singletonList(
                        new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("Reply exactly with OK.")))),
                Collections.emptyList());

        AssistantMessageEventStream stream = provider.stream(
                model,
                context,
                StreamOptions.builder().apiKey(apiKey).maxTokens(64).temperature(0.0).build());

        com.pi4j.ai.types.AssistantMessage message = stream.result().get(90, TimeUnit.SECONDS);
        assertNotNull(message);
        assertFalse(message.getContent().isEmpty());
    }

    @Test
    void openAiCompatWorksWithDeepSeekApiKey() throws Exception {
        String apiKey = requireApiKey();

        OpenAICompletionsProvider provider = new OpenAICompletionsProvider();
        Model model = new Model(
                "deepseek-chat",
                "DeepSeek Chat",
                "openai-completions",
                "deepseek",
                DEEPSEEK_OPENAI_BASE_URL,
                false,
                Arrays.asList("text"),
                null,
                64000,
                512,
                Collections.<String, String>emptyMap());

        Context context = new Context(
                "You are a concise assistant.",
                Collections.<Message>singletonList(
                        new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("Reply exactly with OK.")))),
                Collections.emptyList());

        AssistantMessageEventStream stream = provider.stream(
                model,
                context,
                StreamOptions.builder().apiKey(apiKey).maxTokens(64).temperature(0.0).build());

        com.pi4j.ai.types.AssistantMessage message = stream.result().get(90, TimeUnit.SECONDS);
        assertNotNull(message);
        assertFalse(message.getContent().isEmpty());
    }

    private String requireApiKey() {
        String apiKey = System.getenv(DEEPSEEK_API_KEY_ENV);
        Assumptions.assumeTrue(apiKey != null && !apiKey.trim().isEmpty(), DEEPSEEK_API_KEY_ENV + " is not set");
        return apiKey;
    }
}
