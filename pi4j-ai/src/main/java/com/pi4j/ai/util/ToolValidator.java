package com.pi4j.ai.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pi4j.ai.types.Tool;
import com.pi4j.ai.types.ToolCallContent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ToolValidator {
    private ToolValidator() {
    }

    public static Map<String, Object> validate(Tool tool, ToolCallContent toolCall) {
        JsonObject schema = tool.getParameters();
        Map<String, Object> args = new LinkedHashMap<String, Object>(toolCall.getArguments());

        JsonArray required = schema.has("required") ? schema.getAsJsonArray("required") : new JsonArray();
        for (JsonElement requiredEntry : required) {
            String key = requiredEntry.getAsString();
            if (!args.containsKey(key)) {
                throw new ToolValidationException("Missing required field: " + key);
            }
        }

        JsonObject properties = schema.has("properties") ? schema.getAsJsonObject("properties") : new JsonObject();
        for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
            String key = entry.getKey();
            if (!args.containsKey(key)) {
                continue;
            }
            JsonObject prop = entry.getValue().getAsJsonObject();
            String expectedType = prop.has("type") ? prop.get("type").getAsString() : null;
            if (expectedType == null) {
                continue;
            }
            args.put(key, coerceValue(key, args.get(key), expectedType));
        }

        return args;
    }

    private static Object coerceValue(String key, Object value, String expectedType) {
        if (value == null) {
            return null;
        }

        switch (expectedType) {
            case "string":
                return String.valueOf(value);
            case "number":
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                try {
                    return Double.parseDouble(String.valueOf(value));
                } catch (NumberFormatException ex) {
                    throw new ToolValidationException("Field '" + key + "' is not a number");
                }
            case "integer":
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
                try {
                    return Integer.parseInt(String.valueOf(value));
                } catch (NumberFormatException ex) {
                    throw new ToolValidationException("Field '" + key + "' is not an integer");
                }
            case "boolean":
                if (value instanceof Boolean) {
                    return value;
                }
                String boolText = String.valueOf(value).toLowerCase();
                if ("true".equals(boolText) || "false".equals(boolText)) {
                    return Boolean.parseBoolean(boolText);
                }
                throw new ToolValidationException("Field '" + key + "' is not a boolean");
            case "array":
                if (value instanceof List) {
                    return value;
                }
                throw new ToolValidationException("Field '" + key + "' is not an array");
            case "object":
                if (value instanceof Map) {
                    return value;
                }
                throw new ToolValidationException("Field '" + key + "' is not an object");
            default:
                throw new ToolValidationException("Unsupported schema type: " + expectedType);
        }
    }
}
