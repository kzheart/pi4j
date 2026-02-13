package com.pi4j.tools.bash;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pi4j.agent.tool.AgentTool;
import com.pi4j.agent.tool.AgentToolResult;
import com.pi4j.agent.tool.ToolUpdateCallback;
import com.pi4j.ai.provider.AbortHandle;
import com.pi4j.tools.ToolParamUtils;
import com.pi4j.tools.TruncationResult;
import com.pi4j.tools.Truncator;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public class BashTool implements AgentTool {
    private final Path workDir;
    private final BashOperations operations;

    public BashTool(Path workDir) {
        this(workDir, new DefaultBashOperations());
    }

    public BashTool(Path workDir, BashOperations operations) {
        this.workDir = workDir;
        this.operations = operations;
    }

    @Override
    public String getName() {
        return "bash";
    }

    @Override
    public String getDescription() {
        return "Execute bash commands in working directory";
    }

    @Override
    public String getLabel() {
        return "Bash";
    }

    @Override
    public JsonObject getParameters() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject command = new JsonObject();
        command.addProperty("type", "string");
        props.add("command", command);

        JsonObject timeout = new JsonObject();
        timeout.addProperty("type", "integer");
        props.add("timeout", timeout);

        schema.add("properties", props);

        JsonArray required = new JsonArray();
        required.add("command");
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
            String command = ToolParamUtils.requiredString(params, "command");
            int configuredTimeout = ToolParamUtils.optionalInt(params, "timeout", 15);
            int timeout = configuredTimeout <= 0 ? 15 : configuredTimeout;

            BashResult result = operations.exec(command, workDir.toAbsolutePath().normalize(), timeout, abortHandle);
            if (result.isTimedOut()) {
                return AgentToolResult.error("command timed out after " + timeout + " seconds");
            }

            String output = mergeOutput(result.getStdout(), result.getStderr(), result.getExitCode());
            TruncationResult truncation = Truncator.truncateTail(output, Truncator.DEFAULT_MAX_LINES, Truncator.DEFAULT_MAX_BYTES);
            if (truncation.isTruncated()) {
                output = truncation.getContent() + "\n\n[truncated by " + truncation.getTruncatedBy() + "]";
            } else {
                output = truncation.getContent();
            }

            if (result.getExitCode() != 0) {
                return AgentToolResult.error(output);
            }
            return AgentToolResult.text(output);
        } catch (IllegalArgumentException illegalArgumentException) {
            return AgentToolResult.error(illegalArgumentException.getMessage());
        } catch (IOException ioException) {
            return AgentToolResult.error("bash failed: " + ioException.getMessage());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return AgentToolResult.error("bash interrupted");
        }
    }

    private String mergeOutput(String stdout, String stderr, int exitCode) {
        StringBuilder builder = new StringBuilder();
        if (stdout != null && !stdout.isEmpty()) {
            builder.append(stdout.trim());
        }
        if (stderr != null && !stderr.isEmpty()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(stderr.trim());
        }
        if (builder.length() == 0) {
            builder.append("exit code: ").append(exitCode);
        }
        return builder.toString();
    }
}
