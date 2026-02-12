package com.pi4j.ai.provider.register;

import com.pi4j.ai.provider.ApiRegistry;
import com.pi4j.ai.provider.anthropic.AnthropicProvider;
import com.pi4j.ai.provider.bedrock.BedrockProvider;
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

public final class BuiltinProviderRegistry {
    private BuiltinProviderRegistry() {
    }

    public static void registerBuiltins() {
        ApiRegistry.clear();
        ApiRegistry.register(new AnthropicProvider());
        ApiRegistry.register(new OpenAICompletionsProvider());
        ApiRegistry.register(new OpenAIResponsesProvider());
        ApiRegistry.register(new GoogleProvider());
        ApiRegistry.register(new GoogleVertexProvider());
        ApiRegistry.register(new BedrockProvider());

        // 以下兼容商Provider复用openai-completions协议。
        new MistralProvider();
        new GroqProvider();
        new XAIProvider();
        new OpenRouterProvider();
        new OllamaProvider();
        new CustomOpenAIProvider();
    }
}
