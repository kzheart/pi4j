package com.pi4j.agent.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolArgs {
    private final Map<String, Object> values;

    public ToolArgs(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(values));
    }

    public Map<String, Object> asMap() {
        return values;
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public Object get(String key) {
        return values.get(key);
    }

    public String requireString(String key) {
        Object value = requireValue(key);
        return String.valueOf(value);
    }

    public int requireInt(String key) {
        Object value = requireValue(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException numberFormatException) {
            throw new IllegalArgumentException("Field '" + key + "' is not an integer");
        }
    }

    public double requireNumber(String key) {
        Object value = requireValue(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException numberFormatException) {
            throw new IllegalArgumentException("Field '" + key + "' is not a number");
        }
    }

    public boolean requireBoolean(String key) {
        Object value = requireValue(key);
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        String text = String.valueOf(value).toLowerCase();
        if ("true".equals(text) || "false".equals(text)) {
            return Boolean.parseBoolean(text);
        }
        throw new IllegalArgumentException("Field '" + key + "' is not a boolean");
    }

    private Object requireValue(String key) {
        if (!values.containsKey(key)) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return values.get(key);
    }
}
