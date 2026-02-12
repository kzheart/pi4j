package com.pi4j.ai.model;

import com.pi4j.ai.types.Model;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ModelRegistry {
    private static final Map<String, Model> MODELS = new ConcurrentHashMap<String, Model>();

    private ModelRegistry() {
    }

    public static void register(Model model) {
        MODELS.put(model.getId(), model);
    }

    public static Model get(String modelId) {
        Model model = MODELS.get(modelId);
        if (model == null) {
            throw new IllegalStateException("Unknown model id: " + modelId);
        }
        return model;
    }
}
