package com.pi4j.ai.provider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StreamOptions {
    private final String apiKey;
    private final Double temperature;
    private final Integer maxTokens;
    private final String reasoning;
    private final Integer thinkingBudget;
    private final String toolChoice;
    private final String cacheRetention;
    private final String sessionId;
    private final Map<String, String> headers;
    private final AbortHandle abortHandle;

    private StreamOptions(Builder builder) {
        this.apiKey = builder.apiKey;
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.reasoning = builder.reasoning;
        this.thinkingBudget = builder.thinkingBudget;
        this.toolChoice = builder.toolChoice;
        this.cacheRetention = builder.cacheRetention;
        this.sessionId = builder.sessionId;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<String, String>(builder.headers));
        this.abortHandle = builder.abortHandle == null ? new AbortHandle() : builder.abortHandle;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getApiKey() {
        return apiKey;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public String getReasoning() {
        return reasoning;
    }

    public Integer getThinkingBudget() {
        return thinkingBudget;
    }

    public String getToolChoice() {
        return toolChoice;
    }

    public String getCacheRetention() {
        return cacheRetention;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public AbortHandle getAbortHandle() {
        return abortHandle;
    }

    public static final class Builder {
        private String apiKey;
        private Double temperature;
        private Integer maxTokens;
        private String reasoning;
        private Integer thinkingBudget;
        private String toolChoice;
        private String cacheRetention;
        private String sessionId;
        private Map<String, String> headers = new LinkedHashMap<String, String>();
        private AbortHandle abortHandle;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder reasoning(String reasoning) {
            this.reasoning = reasoning;
            return this;
        }

        public Builder thinkingBudget(Integer thinkingBudget) {
            this.thinkingBudget = thinkingBudget;
            return this;
        }

        public Builder toolChoice(String toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public Builder cacheRetention(String cacheRetention) {
            this.cacheRetention = cacheRetention;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = new LinkedHashMap<String, String>(headers);
            return this;
        }

        public Builder putHeader(String key, String value) {
            this.headers.put(key, value);
            return this;
        }

        public Builder abortHandle(AbortHandle abortHandle) {
            this.abortHandle = abortHandle;
            return this;
        }

        public StreamOptions build() {
            return new StreamOptions(this);
        }
    }
}
