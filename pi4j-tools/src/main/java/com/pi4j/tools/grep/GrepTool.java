package com.pi4j.tools.grep;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class GrepTool implements AgentTool {
    private final Path workDir;

    public GrepTool(Path workDir) {
        this.workDir = workDir;
    }

    @Override
    public String getName() {
        return "grep";
    }

    @Override
    public String getDescription() {
        return "Search file content by pattern";
    }

    @Override
    public String getLabel() {
        return "Grep";
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

        JsonObject ignoreCase = new JsonObject();
        ignoreCase.addProperty("type", "boolean");
        props.add("ignoreCase", ignoreCase);

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
            String pattern = ToolParamUtils.requiredString(params, "pattern");
            String pathParam = ToolParamUtils.optionalString(params, "path", ".");
            boolean ignoreCase = ToolParamUtils.optionalBoolean(params, "ignoreCase", false);
            int configuredLimit = ToolParamUtils.optionalInt(params, "limit", 200);
            final int limit = configuredLimit <= 0 ? 200 : configuredLimit;

            Path target = PathUtils.resolveWithin(workDir, pathParam);
            if (!Files.exists(target)) {
                return AgentToolResult.error("path not found: " + pathParam);
            }

            String expected = ignoreCase ? pattern.toLowerCase() : pattern;
            List<String> matches = new ArrayList<String>();

            try (Stream<Path> walk = Files.walk(target)) {
                walk.filter(Files::isRegularFile).forEach(path -> {
                    if (abortHandle != null && abortHandle.isAborted()) {
                        return;
                    }
                    collectMatches(path, expected, ignoreCase, matches, limit);
                });
            }

            TruncationResult truncation = Truncator.truncateHead(
                    String.join("\n", matches),
                    limit,
                    Truncator.DEFAULT_MAX_BYTES);
            if (truncation.isTruncated()) {
                return AgentToolResult.text(truncation.getContent() + "\n\n[truncated by " + truncation.getTruncatedBy() + "]");
            }
            return AgentToolResult.text(truncation.getContent());
        } catch (IllegalArgumentException illegalArgumentException) {
            return AgentToolResult.error(illegalArgumentException.getMessage());
        } catch (IOException ioException) {
            return AgentToolResult.error("grep failed: " + ioException.getMessage());
        }
    }

    private void collectMatches(
            Path file,
            String expected,
            boolean ignoreCase,
            List<String> matches,
            int limit) {
        if (matches.size() >= limit) {
            return;
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return;
        }

        for (int i = 0; i < lines.size(); i++) {
            if (matches.size() >= limit) {
                return;
            }
            String line = lines.get(i);
            String value = ignoreCase ? line.toLowerCase() : line;
            if (value.contains(expected)) {
                String relative = workDir.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString();
                String snippet = Truncator.truncateLine(line, 200);
                matches.add(relative + ":" + (i + 1) + ":" + snippet);
            }
        }
    }
}
