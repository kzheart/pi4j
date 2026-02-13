package com.pi4j.ai.provider;

import com.pi4j.ai.types.Model;
import java.util.Locale;

public final class ProviderCompat {
    private ProviderCompat() {
    }

    public static OpenAiCompletionsCompat detectOpenAiCompletionsCompat(Model model) {
        String provider = safeLower(model.getProvider());
        String baseUrl = safeLower(model.getBaseUrl());

        boolean isZai = "zai".equals(provider) || baseUrl.contains("api.z.ai");
        boolean isMistral = "mistral".equals(provider) || baseUrl.contains("mistral.ai");
        boolean isGrok = "xai".equals(provider) || baseUrl.contains("api.x.ai");
        boolean isCerebras = "cerebras".equals(provider) || baseUrl.contains("cerebras.ai");
        boolean isDeepSeek = "deepseek".equals(provider) || baseUrl.contains("deepseek.com");
        boolean isChutes = baseUrl.contains("chutes.ai");
        boolean isOpenCode = "opencode".equals(provider) || baseUrl.contains("opencode.ai");

        boolean isNonStandard = isMistral || isGrok || isCerebras || isDeepSeek || isZai || isChutes || isOpenCode;
        String maxTokensField = (isMistral || isChutes) ? "max_tokens" : "max_completion_tokens";

        return new OpenAiCompletionsCompat(
                !isNonStandard,
                !isNonStandard,
                !isGrok && !isZai,
                maxTokensField,
                isMistral,
                isMistral,
                isMistral,
                isZai ? "zai" : "openai");
    }

    public static boolean isOpenAiCompatible(String provider) {
        String normalized = safeLower(provider);
        return "openai".equals(normalized)
                || "mistral".equals(normalized)
                || "groq".equals(normalized)
                || "xai".equals(normalized)
                || "openrouter".equals(normalized)
                || "ollama".equals(normalized)
                || "deepseek".equals(normalized)
                || "cerebras".equals(normalized)
                || "zai".equals(normalized);
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    public static final class OpenAiCompletionsCompat {
        private final boolean supportsStore;
        private final boolean supportsDeveloperRole;
        private final boolean supportsReasoningEffort;
        private final String maxTokensField;
        private final boolean requiresToolResultName;
        private final boolean requiresThinkingAsText;
        private final boolean requiresMistralToolIds;
        private final String thinkingFormat;

        public OpenAiCompletionsCompat(
                boolean supportsStore,
                boolean supportsDeveloperRole,
                boolean supportsReasoningEffort,
                String maxTokensField,
                boolean requiresToolResultName,
                boolean requiresThinkingAsText,
                boolean requiresMistralToolIds,
                String thinkingFormat) {
            this.supportsStore = supportsStore;
            this.supportsDeveloperRole = supportsDeveloperRole;
            this.supportsReasoningEffort = supportsReasoningEffort;
            this.maxTokensField = maxTokensField;
            this.requiresToolResultName = requiresToolResultName;
            this.requiresThinkingAsText = requiresThinkingAsText;
            this.requiresMistralToolIds = requiresMistralToolIds;
            this.thinkingFormat = thinkingFormat;
        }

        public boolean isSupportsStore() {
            return supportsStore;
        }

        public boolean isSupportsDeveloperRole() {
            return supportsDeveloperRole;
        }

        public boolean isSupportsReasoningEffort() {
            return supportsReasoningEffort;
        }

        public String getMaxTokensField() {
            return maxTokensField;
        }

        public boolean isRequiresToolResultName() {
            return requiresToolResultName;
        }

        public boolean isRequiresThinkingAsText() {
            return requiresThinkingAsText;
        }

        public boolean isRequiresMistralToolIds() {
            return requiresMistralToolIds;
        }

        public String getThinkingFormat() {
            return thinkingFormat;
        }
    }
}
