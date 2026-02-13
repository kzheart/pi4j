package com.pi4j.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pi4j.agent.tool.AgentToolResult;
import com.pi4j.ai.provider.AbortHandle;
import com.pi4j.ai.types.TextContent;
import com.pi4j.tools.bash.BashResult;
import com.pi4j.tools.bash.BashTool;
import com.pi4j.tools.bash.DefaultBashOperations;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BashToolTest {

    @Test
    void bashToolUsesInjectedOperations() {
        BashTool tool = new BashTool(Paths.get("."), (command, cwd, timeoutSeconds, abortHandle) ->
                new BashResult(0, "ok", "", false));

        AgentToolResult result = tool.execute("1", map("command", "echo hi"), new AbortHandle(), partial -> {
        });
        assertEquals("ok", textOf(result));
    }

    @Test
    void bashToolReturnsTimeoutError() {
        BashTool tool = new BashTool(Paths.get("."), (command, cwd, timeoutSeconds, abortHandle) ->
                new BashResult(-1, "", "", true));

        AgentToolResult result = tool.execute("1", map("command", "sleep 10", "timeout", 1), new AbortHandle(), partial -> {
        });
        assertTrue(textOf(result).contains("timed out"));
    }

    @Test
    void defaultBashOperationsExecutesShellCommand() throws Exception {
        DefaultBashOperations operations = new DefaultBashOperations();
        Path cwd = Paths.get(".").toAbsolutePath().normalize();

        BashResult result = operations.exec("echo hello", cwd, 5, new AbortHandle());

        assertEquals(0, result.getExitCode());
        assertTrue(result.getStdout().contains("hello"));
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> map = new HashMap<String, Object>();
        for (int i = 0; i < values.length; i += 2) {
            map.put((String) values[i], values[i + 1]);
        }
        return map;
    }

    private String textOf(AgentToolResult result) {
        return ((TextContent) result.getContent().get(0)).getText();
    }
}
