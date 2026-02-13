package com.pi4j.tools.read;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pi4j.agent.tool.AgentTool;
import com.pi4j.agent.tool.AgentToolResult;
import com.pi4j.agent.tool.ToolUpdateCallback;
import com.pi4j.ai.provider.AbortHandle;
import com.pi4j.tools.PathUtils;
import com.pi4j.tools.ToolParamUtils;
import com.pi4j.tools.TruncationResult;
import com.pi4j.tools.Truncator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ReadTool implements AgentTool {
    private final Path workDir;

    public ReadTool(Path workDir) {
        this.workDir = workDir;
    }

    @Override
    public String getName() {
        return "read";
    }

    @Override
    public String getDescription() {
        return "Read file content from working directory";
    }

    @Override
    public String getLabel() {
        return "Read File";
    }

    @Override
    public JsonObject getParameters() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject path = new JsonObject();
        path.addProperty("type", "string");
        path.addProperty("description", "Path to file");
        props.add("path", path);

        JsonObject offset = new JsonObject();
        offset.addProperty("type", "integer");
        offset.addProperty("description", "Line offset from start");
        props.add("offset", offset);

        JsonObject limit = new JsonObject();
        limit.addProperty("type", "integer");
        limit.addProperty("description", "Maximum lines to read");
        props.add("limit", limit);

        schema.add("properties", props);

        JsonArray required = new JsonArray();
        required.add("path");
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
            int offset = Math.max(0, ToolParamUtils.optionalInt(params, "offset", 0));
            int limit = ToolParamUtils.optionalInt(params, "limit", Truncator.DEFAULT_MAX_LINES);
            if (limit <= 0) {
                limit = Truncator.DEFAULT_MAX_LINES;
            }

            Path target = PathUtils.resolveWithin(workDir, pathParam);
            if (!Files.exists(target)) {
                return AgentToolResult.error("file not found: " + pathParam);
            }
            if (!Files.isRegularFile(target)) {
                return AgentToolResult.error("path is not a regular file: " + pathParam);
            }

            List<String> lines = Files.readAllLines(target, StandardCharsets.UTF_8);
            if (offset >= lines.size()) {
                return AgentToolResult.text("");
            }

            int end = Math.min(lines.size(), offset + limit);
            StringBuilder builder = new StringBuilder();
            for (int i = offset; i < end; i++) {
                if (i > offset) {
                    builder.append('\n');
                }
                builder.append(lines.get(i));
            }

            TruncationResult truncation = Truncator.truncateHead(
                    builder.toString(),
                    limit,
                    Truncator.DEFAULT_MAX_BYTES);

            String output = truncation.getContent();
            if (truncation.isTruncated()) {
                output += "\n\n[truncated by " + truncation.getTruncatedBy() + "]";
            }
            return AgentToolResult.text(output);
        } catch (IllegalArgumentException illegalArgumentException) {
            return AgentToolResult.error(illegalArgumentException.getMessage());
        } catch (IOException ioException) {
            return AgentToolResult.error("read failed: " + ioException.getMessage());
        }
    }
}
