package com.pi4j.ai.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Model {
    private final String id;
    private final String name;
    private final String api;
    private final String provider;
    private final String baseUrl;
    private final boolean reasoning;
    private final List<String> input;
    private final ModelCost cost;
    private final int contextWindow;
    private final int maxTokens;
    private final Map<String, String> headers;

    public Model(
            String id,
            String name,
            String api,
            String provider,
            String baseUrl,
            boolean reasoning,
            List<String> input,
            ModelCost cost,
            int contextWindow,
            int maxTokens,
            Map<String, String> headers) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.api = Objects.requireNonNull(api, "api");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.baseUrl = baseUrl;
        this.reasoning = reasoning;
        this.input = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(input, "input")));
        this.cost = cost;
        this.contextWindow = contextWindow;
        this.maxTokens = maxTokens;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(headers, "headers")));
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getApi() {
        return api;
    }

    public String getProvider() {
        return provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean isReasoning() {
        return reasoning;
    }

    public List<String> getInput() {
        return input;
    }

    public ModelCost getCost() {
        return cost;
    }

    public int getContextWindow() {
        return contextWindow;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public static final class ModelCost {
        private final double input;
        private final double output;
        private final double cacheRead;
        private final double cacheWrite;

        public ModelCost(double input, double output, double cacheRead, double cacheWrite) {
            this.input = input;
            this.output = output;
            this.cacheRead = cacheRead;
            this.cacheWrite = cacheWrite;
        }

        public double getInput() {
            return input;
        }

        public double getOutput() {
            return output;
        }

        public double getCacheRead() {
            return cacheRead;
        }

        public double getCacheWrite() {
            return cacheWrite;
        }
    }
}
