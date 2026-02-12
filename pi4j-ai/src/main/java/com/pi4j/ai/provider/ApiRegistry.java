package com.pi4j.ai.provider;

import com.pi4j.ai.stream.AssistantMessageEventStream;
import com.pi4j.ai.types.Context;
import com.pi4j.ai.types.Model;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ApiRegistry {
    private static final Map<String, ApiProvider> REGISTRY = new ConcurrentHashMap<String, ApiProvider>();

    private ApiRegistry() {
    }

    public static void register(ApiProvider provider) {
        REGISTRY.put(provider.getApi(), provider);
    }

    public static ApiProvider getProvider(String api) {
        ApiProvider provider = REGISTRY.get(api);
        if (provider == null) {
            throw new IllegalStateException("No provider registered for api: " + api);
        }
        return provider;
    }

    public static AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
        return getProvider(model.getApi()).stream(model, context, options);
    }

    public static void clear() {
        REGISTRY.clear();
    }
}
