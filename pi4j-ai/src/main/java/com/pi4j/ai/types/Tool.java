package com.pi4j.ai.types;

import com.google.gson.JsonObject;
import java.util.Objects;

public final class Tool {
    private final String name;
    private final String description;
    private final JsonObject parameters;

    public Tool(String name, String description, JsonObject parameters) {
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.parameters = Objects.requireNonNull(parameters, "parameters").deepCopy();
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public JsonObject getParameters() {
        return parameters.deepCopy();
    }
}
