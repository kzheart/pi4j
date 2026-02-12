package com.pi4j.ai.types;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ToolCallContent extends ContentBlock {
    private final String id;
    private final String name;
    private final Map<String, Object> arguments;
    private final String thoughtSignature;

    public ToolCallContent(String id, String name, Map<String, Object> arguments) {
        this(id, name, arguments, null);
    }

    public ToolCallContent(String id, String name, Map<String, Object> arguments, String thoughtSignature) {
        super("toolCall");
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.arguments = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(arguments, "arguments")));
        this.thoughtSignature = thoughtSignature;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public String getThoughtSignature() {
        return thoughtSignature;
    }
}
