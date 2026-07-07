package com.pi4j.ai.provider.register;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pi4j.ai.provider.ApiRegistry;
import com.pi4j.ai.provider.anthropic.AnthropicProvider;
import com.pi4j.ai.provider.google.GoogleProvider;
import com.pi4j.ai.provider.google.GoogleVertexProvider;
import com.pi4j.ai.provider.openai.OpenAICompletionsProvider;
import com.pi4j.ai.provider.openai.OpenAIResponsesProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BuiltinProviderRegistryTest {

    @AfterEach
    void cleanup() {
        ApiRegistry.clear();
    }

    @Test
    void registerBuiltinsRegistersDefaultProviders() {
        BuiltinProviderRegistry.registerBuiltins();

        assertTrue(ApiRegistry.getProvider("anthropic-messages") instanceof AnthropicProvider);
        assertTrue(ApiRegistry.getProvider("openai-completions") instanceof OpenAICompletionsProvider);
        assertTrue(ApiRegistry.getProvider("openai-responses") instanceof OpenAIResponsesProvider);
        assertTrue(ApiRegistry.getProvider("google-generative-ai") instanceof GoogleProvider);
        assertTrue(ApiRegistry.getProvider("google-vertex") instanceof GoogleVertexProvider);
    }

    @Test
    void registerBuiltinsDoesNotClearCustomRegistrations() {
        OpenAICompletionsProvider custom = new OpenAICompletionsProvider();
        ApiRegistry.register("openai-completions", "my-custom", custom);

        BuiltinProviderRegistry.registerBuiltins();

        assertSame(custom, ApiRegistry.getProvider("openai-completions", "my-custom"));
    }

    @Test
    void unknownProviderNameFallsBackToBaseProvider() {
        BuiltinProviderRegistry.registerBuiltins();

        assertNotNull(ApiRegistry.getProvider("openai-completions", "unknown-name"));
        assertTrue(ApiRegistry.getProvider("openai-completions", "unknown-name") instanceof OpenAICompletionsProvider);
    }
}