package com.pi4j.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pi4j.ai.types.Model;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class ProviderCompatTest {

    @Test
    void detectMistralCompat() {
        ProviderCompat.OpenAiCompletionsCompat compat = ProviderCompat.detectOpenAiCompletionsCompat(
                model("mistral", "https://api.mistral.ai"));
        assertEquals("max_tokens", compat.getMaxTokensField());
        assertTrue(compat.isRequiresToolResultName());
        assertTrue(compat.isRequiresMistralToolIds());
    }

    @Test
    void detectXaiCompat() {
        ProviderCompat.OpenAiCompletionsCompat compat = ProviderCompat.detectOpenAiCompletionsCompat(
                model("xai", "https://api.x.ai"));
        assertFalse(compat.isSupportsReasoningEffort());
    }

    @Test
    void detectOpenAiCompatDefaults() {
        ProviderCompat.OpenAiCompletionsCompat compat = ProviderCompat.detectOpenAiCompletionsCompat(
                model("openai", "https://api.openai.com"));
        assertEquals("max_completion_tokens", compat.getMaxTokensField());
        assertTrue(compat.isSupportsReasoningEffort());
    }

    @Test
    void detectDeepSeekCompat() {
        ProviderCompat.OpenAiCompletionsCompat compat = ProviderCompat.detectOpenAiCompletionsCompat(
                model("deepseek", "https://api.deepseek.com"));
        assertEquals("max_completion_tokens", compat.getMaxTokensField());
        assertFalse(compat.isSupportsStore());
        assertFalse(compat.isSupportsDeveloperRole());
        assertTrue(compat.isSupportsReasoningEffort());
        assertTrue(ProviderCompat.isOpenAiCompatible("deepseek"));
    }

    private Model model(String provider, String baseUrl) {
        return new Model(
                "demo",
                "demo",
                "openai-completions",
                provider,
                baseUrl,
                false,
                Arrays.asList("text"),
                null,
                64000,
                4096,
                Collections.<String, String>emptyMap());
    }
}
