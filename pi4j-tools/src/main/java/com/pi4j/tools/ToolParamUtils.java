package com.pi4j.tools;

import java.util.Map;

public final class ToolParamUtils {
    private ToolParamUtils() {
    }

    public static String requiredString(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing required param: " + key);
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("param must not be empty: " + key);
        }
        return text;
    }

    public static String optionalString(Map<String, Object> params, String key, String defaultValue) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }

    public static int optionalInt(Map<String, Object> params, String key, int defaultValue) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException("param must be integer: " + key);
        }
    }

    public static boolean optionalBoolean(Map<String, Object> params, String key, boolean defaultValue) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public static String requiredValue(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing required param: " + key);
        }
        return String.valueOf(value);
    }
}
