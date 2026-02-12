package com.pi4j.ai.provider;

public final class ProviderCompat {
    private ProviderCompat() {
    }

    public static boolean isOpenAiCompatible(String provider) {
        return "openai".equals(provider)
                || "mistral".equals(provider)
                || "groq".equals(provider)
                || "xai".equals(provider)
                || "openrouter".equals(provider)
                || "ollama".equals(provider);
    }
}
