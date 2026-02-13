package com.pi4j.tools.find;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class FindTool implements AgentTool {
    private final Path workDir;

    public FindTool(Path workDir) {
        this.workDir = workDir;
    }

    @Override
    public String getName() {
        return "find";
    }

    @Override
    public String getDescription() {
        return "Find files by name pattern";
    }

    @Override
    public String getLabel() {
        return "Find File";
    }

    @Override
    public JsonObject getParameters() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject pattern = new JsonObject();
        pattern.addProperty("type", "string");
        props.add("pattern", pattern);

        JsonObject path = new JsonObject();
        path.addProperty("type", "string");
        props.add("path", path);

        JsonObject limit = new JsonObject();
        limit.addProperty("type", "integer");
        props.add("limit", limit);

        schema.add("properties", props);

        JsonArray required = new JsonArray();
        required.add("pattern");
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
            String pattern = ToolParamUtils.requiredString(params, "pattern").toLowerCase();
            String pathParam = ToolParamUtils.optionalString(params, "path", ".");
            int limit = ToolParamUtils.optionalInt(params, "limit", 200);
            if (limit <= 0) {
                limit = 200;
            }

            Path target = PathUtils.resolveWithin(workDir, pathParam);
            if (!Files.exists(target)) {
                return AgentToolResult.error("path not found: " + pathParam);
            }

            List<String> matches = new ArrayList<String>();
            try (Stream<Path> walk = Files.walk(target)) {
                walk.forEach(path -> {
                    if (abortHandle != null && abortHandle.isAborted()) {
                        return;
                    }
                    Path fileName = path.getFileName();
                    if (fileName == null) {
                        return;
                    }
                    String fileNameText = fileName.toString().toLowerCase();
                    if (fileNameText.contains(pattern)) {
                        matches.add(workDir.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString());
                    }
                });
            }

            List<String> outputMatches = new ArrayList<String>(matches);
            Collections.sort(outputMatches);
            if (outputMatches.size() > limit) {
                outputMatches = outputMatches.subList(0, limit);
            }

            TruncationResult truncation = Truncator.truncateHead(
                    String.join("\n", outputMatches),
                    limit,
                    Truncator.DEFAULT_MAX_BYTES);
            if (truncation.isTruncated()) {
                return AgentToolResult.text(truncation.getContent() + "\n\n[truncated by " + truncation.getTruncatedBy() + "]");
            }
            return AgentToolResult.text(truncation.getContent());
        } catch (IllegalArgumentException illegalArgumentException) {
            return AgentToolResult.error(illegalArgumentException.getMessage());
        } catch (IOException ioException) {
            return AgentToolResult.error("find failed: " + ioException.getMessage());
        }
    }
}
