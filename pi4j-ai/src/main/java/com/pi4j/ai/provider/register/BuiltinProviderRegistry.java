package com.pi4j.ai.provider.register;

import com.pi4j.ai.provider.ApiRegistry;
import com.pi4j.ai.provider.anthropic.AnthropicProvider;
import com.pi4j.ai.provider.bailian.BailianProvider;
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

        // 兼容商共享 openai-completions api，按 provider 名称路由。
        ApiRegistry.register("openai-completions", "mistral", new MistralProvider());
        ApiRegistry.register("openai-completions", "groq", new GroqProvider());
        ApiRegistry.register("openai-completions", "xai", new XAIProvider());
        ApiRegistry.register("openai-completions", "openrouter", new OpenRouterProvider());
        ApiRegistry.register("openai-completions", "ollama", new OllamaProvider());
        ApiRegistry.register("openai-completions", "custom-openai", new CustomOpenAIProvider());
        ApiRegistry.register("openai-completions", "bailian", new BailianProvider());
    }
}
