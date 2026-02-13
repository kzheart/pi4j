package com.pi4j.agent.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Objects;

public final class ToolSpec {
    private final String name;
    private final String description;
    private final String label;
    private final JsonObject parameters;
    private final ToolHandler handler;

    private ToolSpec(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.label = builder.label == null ? builder.name : builder.label;
        this.parameters = builder.buildParameters();
        this.handler = builder.handler;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getLabel() {
        return label;
    }

    public JsonObject getParameters() {
        return parameters;
    }

    public AgentTool toAgentTool() {
        return new AgentTool() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public String getLabel() {
                return label;
            }

            @Override
            public JsonObject getParameters() {
                return parameters;
            }

            @Override
            public AgentToolResult execute(
                    String toolCallId,
                    java.util.Map<String, Object> params,
                    com.pi4j.ai.provider.AbortHandle abortHandle,
                    ToolUpdateCallback onUpdate) {
                return handler.handle(toolCallId, new ToolArgs(params), abortHandle, onUpdate);
            }
        };
    }

    public static final class Builder {
        private final String name;
        private String description = "";
        private String label;
        private final JsonObject properties = new JsonObject();
        private final JsonArray required = new JsonArray();
        private ToolHandler handler;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        public Builder description(String description) {
            this.description = description == null ? "" : description;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder stringParam(String name, boolean required, String description) {
            addParam(name, "string", required, description);
            return this;
        }

        public Builder numberParam(String name, boolean required, String description) {
            addParam(name, "number", required, description);
            return this;
        }

        public Builder integerParam(String name, boolean required, String description) {
            addParam(name, "integer", required, description);
            return this;
        }

        public Builder booleanParam(String name, boolean required, String description) {
            addParam(name, "boolean", required, description);
            return this;
        }

        public Builder handler(ToolHandler handler) {
            this.handler = handler;
            return this;
        }

        public ToolSpec build() {
            if (handler == null) {
                throw new IllegalStateException("ToolSpec handler is required.");
            }
            return new ToolSpec(this);
        }

        private void addParam(String name, String type, boolean required, String description) {
            JsonObject property = new JsonObject();
            property.addProperty("type", type);
            if (description != null && !description.trim().isEmpty()) {
                property.addProperty("description", description);
            }
            properties.add(name, property);
            if (required) {
                this.required.add(name);
            }
        }

        private JsonObject buildParameters() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            if (required.size() > 0) {
                schema.add("required", required);
            }
            return schema;
        }
    }
}
