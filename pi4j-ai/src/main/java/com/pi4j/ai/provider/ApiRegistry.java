package com.pi4j.ai.provider;

import com.pi4j.ai.stream.AssistantMessageEventStream;
import com.pi4j.ai.types.Context;
import com.pi4j.ai.types.Model;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ApiRegistry {
    private static final Map<String, ApiProvider> REGISTRY = new ConcurrentHashMap<String, ApiProvider>();
    private static final Map<String, ApiProvider> REGISTRY_BY_API_AND_PROVIDER =
            new ConcurrentHashMap<String, ApiProvider>();

    private ApiRegistry() {
    }

    public static void register(ApiProvider provider) {
        REGISTRY.put(provider.getApi(), provider);
    }

    public static void register(String api, String provider, ApiProvider apiProvider) {
        REGISTRY_BY_API_AND_PROVIDER.put(key(api, provider), apiProvider);
    }

    public static ApiProvider getProvider(String api) {
        ApiProvider provider = REGISTRY.get(api);
        if (provider == null) {
            throw new IllegalStateException("No provider registered for api: " + api);
        }
        return provider;
    }

    public static ApiProvider getProvider(String api, String providerName) {
        ApiProvider provider = REGISTRY_BY_API_AND_PROVIDER.get(key(api, providerName));
        if (provider != null) {
            return provider;
        }
        return getProvider(api);
    }

    public static AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
        return getProvider(model.getApi(), model.getProvider()).stream(model, context, options);
    }

    public static void clear() {
        REGISTRY.clear();
        REGISTRY_BY_API_AND_PROVIDER.clear();
    }

    private static String key(String api, String provider) {
        return api + "::" + provider;
    }
}
