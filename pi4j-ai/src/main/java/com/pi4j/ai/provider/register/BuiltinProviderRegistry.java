package com.pi4j.ai.provider.register;

import com.pi4j.ai.provider.ApiRegistry;
import com.pi4j.ai.provider.anthropic.AnthropicProvider;
import com.pi4j.ai.provider.google.GoogleProvider;
import com.pi4j.ai.provider.google.GoogleVertexProvider;
import com.pi4j.ai.provider.openai.OpenAICompletionsProvider;
import com.pi4j.ai.provider.openai.OpenAIResponsesProvider;

public final class BuiltinProviderRegistry {
    private BuiltinProviderRegistry() {
    }

    /**
     * Registers built-in API providers. Idempotent: does not clear existing registrations and does not
     * affect custom providers registered by consumers; safe to call repeatedly.
     */
    public static void registerBuiltins() {
        ApiRegistry.register(new AnthropicProvider());
        ApiRegistry.register(new OpenAICompletionsProvider());
        ApiRegistry.register(new OpenAIResponsesProvider());
        ApiRegistry.register(new GoogleProvider());
        ApiRegistry.register(new GoogleVertexProvider());
    }
}
