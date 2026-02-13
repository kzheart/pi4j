package com.pi4j.tools.write;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pi4j.agent.tool.AgentTool;
import com.pi4j.agent.tool.AgentToolResult;
import com.pi4j.agent.tool.ToolUpdateCallback;
import com.pi4j.ai.provider.AbortHandle;
import com.pi4j.tools.PathUtils;
import com.pi4j.tools.ToolParamUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class WriteTool implements AgentTool {
    private final Path workDir;

    public WriteTool(Path workDir) {
        this.workDir = workDir;
    }

    @Override
    public String getName() {
        return "write";
    }

    @Override
    public String getDescription() {
        return "Write file content to working directory";
    }

    @Override
    public String getLabel() {
        return "Write File";
    }

    @Override
    public JsonObject getParameters() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject path = new JsonObject();
        path.addProperty("type", "string");
        path.addProperty("description", "Target file path");
        props.add("path", path);

        JsonObject content = new JsonObject();
        content.addProperty("type", "string");
        content.addProperty("description", "Text content to write");
        props.add("content", content);

        schema.add("properties", props);

        JsonArray required = new JsonArray();
        required.add("path");
        required.add("content");
        schema.add("required", required);

        return schema;
    }

    @Override
    public AgentToolResult execute(
            String toolCallId,
            Map<String, Object> params,
            AbortHandle abortHandle,
            ToolUpdateCallback onUpdate) {
        try {
            String pathParam = ToolParamUtils.requiredString(params, "path");
            String content = ToolParamUtils.requiredValue(params, "content");

            Path target = PathUtils.resolveWithin(workDir, pathParam);
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(target, content.getBytes(StandardCharsets.UTF_8));

            return AgentToolResult.text("wrote " + content.getBytes(StandardCharsets.UTF_8).length + " bytes to " + pathParam);
        } catch (IllegalArgumentException illegalArgumentException) {
            return AgentToolResult.error(illegalArgumentException.getMessage());
        } catch (IOException ioException) {
            return AgentToolResult.error("write failed: " + ioException.getMessage());
        }
    }
}
