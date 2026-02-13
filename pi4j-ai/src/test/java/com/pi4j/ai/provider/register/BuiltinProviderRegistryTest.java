package com.pi4j.ai.provider.register;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pi4j.ai.provider.ApiRegistry;
import com.pi4j.ai.provider.anthropic.AnthropicProvider;
import com.pi4j.ai.provider.customopenai.CustomOpenAIProvider;
import com.pi4j.ai.provider.google.GoogleProvider;
import com.pi4j.ai.provider.google.GoogleVertexProvider;
import com.pi4j.ai.provider.groq.GroqProvider;
import com.pi4j.ai.provider.mistral.MistralProvider;
import com.pi4j.ai.provider.ollama.OllamaProvider;
import com.pi4j.ai.provider.openai.OpenAICompletionsProvider;
import com.pi4j.ai.provider.openai.OpenAIResponsesProvider;
import com.pi4j.ai.provider.openrouter.OpenRouterProvider;
import com.pi4j.ai.provider.xai.XAIProvider;
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
    void registerBuiltinsRegistersOpenAiCompletionsProviderOverrides() {
        BuiltinProviderRegistry.registerBuiltins();

        assertTrue(ApiRegistry.getProvider("openai-completions", "mistral") instanceof MistralProvider);
        assertTrue(ApiRegistry.getProvider("openai-completions", "groq") instanceof GroqProvider);
        assertTrue(ApiRegistry.getProvider("openai-completions", "xai") instanceof XAIProvider);
        assertTrue(ApiRegistry.getProvider("openai-completions", "openrouter") instanceof OpenRouterProvider);
        assertTrue(ApiRegistry.getProvider("openai-completions", "ollama") instanceof OllamaProvider);
        assertTrue(ApiRegistry.getProvider("openai-completions", "custom-openai") instanceof CustomOpenAIProvider);
        assertTrue(ApiRegistry.getProvider("openai-completions", "unknown") instanceof OpenAICompletionsProvider);
    }
}
