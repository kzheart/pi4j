package com.pi4j.tools.ls;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class LsTool implements AgentTool {
    private final Path workDir;

    public LsTool(Path workDir) {
        this.workDir = workDir;
    }

    @Override
    public String getName() {
        return "ls";
    }

    @Override
    public String getDescription() {
        return "List directory entries";
    }

    @Override
    public String getLabel() {
        return "List Directory";
    }

    @Override
    public JsonObject getParameters() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject path = new JsonObject();
        path.addProperty("type", "string");
        props.add("path", path);

        JsonObject limit = new JsonObject();
        limit.addProperty("type", "integer");
        props.add("limit", limit);

        schema.add("properties", props);
        return schema;
    }

    @Override
    public AgentToolResult execute(
            String toolCallId,
            Map<String, Object> params,
            AbortHandle abortHandle,
            ToolUpdateCallback onUpdate) {
        try {
            String pathParam = ToolParamUtils.optionalString(params, "path", ".");
            int limit = ToolParamUtils.optionalInt(params, "limit", 200);
            if (limit <= 0) {
                limit = 200;
            }

            Path target = PathUtils.resolveWithin(workDir, pathParam);
            if (!Files.exists(target)) {
                return AgentToolResult.error("path not found: " + pathParam);
            }
            if (!Files.isDirectory(target)) {
                return AgentToolResult.error("path is not directory: " + pathParam);
            }

            List<String> entries = new ArrayList<String>();
            try (Stream<Path> stream = Files.list(target)) {
                stream.forEach(path -> {
                    String name = target.relativize(path).toString();
                    if (Files.isDirectory(path)) {
                        entries.add(name + "/");
                    } else {
                        entries.add(name);
                    }
                });
            }

            List<String> outputEntries = new ArrayList<String>(entries);
            Collections.sort(outputEntries);
            if (outputEntries.size() > limit) {
                outputEntries = outputEntries.subList(0, limit);
            }

            String output = String.join("\n", outputEntries);
            TruncationResult truncation = Truncator.truncateHead(output, limit, Truncator.DEFAULT_MAX_BYTES);
            if (truncation.isTruncated()) {
                return AgentToolResult.text(truncation.getContent() + "\n\n[truncated by " + truncation.getTruncatedBy() + "]");
            }
            return AgentToolResult.text(truncation.getContent());
        } catch (IllegalArgumentException illegalArgumentException) {
            return AgentToolResult.error(illegalArgumentException.getMessage());
        } catch (IOException ioException) {
            return AgentToolResult.error("ls failed: " + ioException.getMessage());
        }
    }
}
