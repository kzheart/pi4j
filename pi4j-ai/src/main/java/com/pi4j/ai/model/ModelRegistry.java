package com.pi4j.ai.model;

import com.google.gson.reflect.TypeToken;
import com.pi4j.ai.types.Model;
import com.pi4j.ai.util.JsonUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 内置模型注册表，数据来源于 classpath 上的 {@code models.json}。
 *
 * <p>查找规则（{@link #find(String)}，大小写不敏感）：
 * <ul>
 *   <li>{@code provider:id} — 提供商加模型 ID</li>
 *   <li>裸 {@code id} — 模型 ID</li>
 *   <li>裸别名 — {@code aliases} 中的任一条目</li>
 *   <li>{@code provider:alias} — 提供商加别名</li>
 * </ul>
 * 索引键统一为小写；返回的 {@link Model} 保留 JSON 中的原始大小写。
 */
public final class ModelRegistry {
    private static final List<Model> ALL_MODELS;
    private static final Map<String, Model> INDEX;

    static {
        List<ModelEntry> entries = loadEntries();
        List<Model> models = new ArrayList<Model>(entries.size());
        Map<String, Model> index = new LinkedHashMap<String, Model>();
        for (ModelEntry entry : entries) {
            Model model = entry.toModel();
            models.add(model);
            register(index, model.getProvider() + ":" + model.getId(), model);
            register(index, model.getId(), model);
            if (entry.aliases != null) {
                for (String alias : entry.aliases) {
                    register(index, alias, model);
                    register(index, model.getProvider() + ":" + alias, model);
                }
            }
        }
        ALL_MODELS = Collections.unmodifiableList(models);
        INDEX = Collections.unmodifiableMap(index);
    }

    private ModelRegistry() {
    }

    /**
     * 按 spec 查找内置模型。spec 为 null、空白或未命中时返回 null。
     */
    public static Model find(String spec) {
        if (spec == null || spec.trim().isEmpty()) {
            return null;
        }
        return INDEX.get(spec.trim().toLowerCase(Locale.ROOT));
    }

    /** 返回全部内置模型（不可变列表）。 */
    public static List<Model> all() {
        return ALL_MODELS;
    }

    private static void register(Map<String, Model> index, String key, Model model) {
        String normalizedKey = key.toLowerCase(Locale.ROOT);
        index.putIfAbsent(normalizedKey, model);
    }

    private static List<ModelEntry> loadEntries() {
        InputStream stream = ModelRegistry.class.getResourceAsStream("models.json");
        if (stream == null) {
            throw new IllegalStateException("Built-in models resource missing: models.json");
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<ModelEntry>>() {
            }.getType();
            List<ModelEntry> entries = JsonUtil.gson().fromJson(reader, listType);
            if (entries == null || entries.isEmpty()) {
                throw new IllegalStateException("Built-in models resource is empty: models.json");
            }
            return entries;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load built-in models from models.json", ex);
        }
    }

    private static final class ModelEntry {
        private String id;
        private String name;
        private String api;
        private String provider;
        private String baseUrl;
        private boolean reasoning;
        private List<String> input;
        private int contextWindow;
        private int maxTokens;
        private Map<String, String> headers;
        private List<String> aliases;

        private Model toModel() {
            Map<String, String> resolvedHeaders = headers == null
                    ? Collections.<String, String>emptyMap()
                    : headers;
            List<String> resolvedInput = input == null
                    ? Collections.<String>emptyList()
                    : input;
            return new Model(
                    id,
                    name,
                    api,
                    provider,
                    baseUrl,
                    reasoning,
                    resolvedInput,
                    null,
                    contextWindow,
                    maxTokens,
                    resolvedHeaders);
        }
    }
}
