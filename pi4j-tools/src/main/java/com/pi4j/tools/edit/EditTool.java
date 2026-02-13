package com.pi4j.tools.edit;

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

public class EditTool implements AgentTool {
    private final Path workDir;

    public EditTool(Path workDir) {
        this.workDir = workDir;
    }

    @Override
    public String getName() {
        return "edit";
    }

    @Override
    public String getDescription() {
        return "Edit file by replacing text";
    }

    @Override
    public String getLabel() {
        return "Edit File";
    }

    @Override
    public JsonObject getParameters() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject path = new JsonObject();
        path.addProperty("type", "string");
        props.add("path", path);

        JsonObject oldText = new JsonObject();
        oldText.addProperty("type", "string");
        props.add("oldText", oldText);

        JsonObject newText = new JsonObject();
        newText.addProperty("type", "string");
        props.add("newText", newText);

        JsonObject replaceAll = new JsonObject();
        replaceAll.addProperty("type", "boolean");
        props.add("replaceAll", replaceAll);

        schema.add("properties", props);

        JsonArray required = new JsonArray();
        required.add("path");
        required.add("oldText");
        required.add("newText");
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
            String oldText = ToolParamUtils.requiredString(params, "oldText");
            String newText = ToolParamUtils.requiredValue(params, "newText");
            boolean replaceAll = ToolParamUtils.optionalBoolean(params, "replaceAll", false);

            Path target = PathUtils.resolveWithin(workDir, pathParam);
            if (!Files.exists(target)) {
                return AgentToolResult.error("file not found: " + pathParam);
            }
            if (!Files.isRegularFile(target)) {
                return AgentToolResult.error("path is not a regular file: " + pathParam);
            }

            String content = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
            if (!content.contains(oldText)) {
                return AgentToolResult.error("oldText not found: " + oldText);
            }

            String updated;
            if (replaceAll) {
                updated = content.replace(oldText, newText);
            } else {
                updated = replaceFirst(content, oldText, newText);
            }

            Files.write(target, updated.getBytes(StandardCharsets.UTF_8));
            return AgentToolResult.text("updated file: " + pathParam);
        } catch (IllegalArgumentException illegalArgumentException) {
            return AgentToolResult.error(illegalArgumentException.getMessage());
        } catch (IOException ioException) {
            return AgentToolResult.error("edit failed: " + ioException.getMessage());
        }
    }

    private String replaceFirst(String source, String oldText, String newText) {
        int index = source.indexOf(oldText);
        if (index < 0) {
            return source;
        }
        return source.substring(0, index) + newText + source.substring(index + oldText.length());
    }
}
