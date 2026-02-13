package com.pi4j.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pi4j.agent.tool.AgentToolResult;
import com.pi4j.ai.provider.AbortHandle;
import com.pi4j.ai.types.TextContent;
import com.pi4j.tools.edit.EditTool;
import com.pi4j.tools.grep.GrepTool;
import com.pi4j.tools.read.ReadTool;
import com.pi4j.tools.write.WriteTool;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolErrorCasesTest {

    @TempDir
    Path tempDir;

    @Test
    void readRejectsPathEscape() {
        ReadTool readTool = new ReadTool(tempDir);
        AgentToolResult result = readTool.execute("1", map("path", "../x.txt"), new AbortHandle(), partial -> {
        });

        assertTrue(textOf(result).contains("escapes workDir"));
    }

    @Test
    void writeAllowsEmptyContent() throws Exception {
        WriteTool writeTool = new WriteTool(tempDir);
        AgentToolResult result = writeTool.execute("2", map("path", "a.txt", "content", ""), new AbortHandle(), partial -> {
        });

        assertTrue(textOf(result).contains("wrote 0 bytes"));
        assertEquals("", new String(Files.readAllBytes(tempDir.resolve("a.txt")), StandardCharsets.UTF_8));
    }

    @Test
    void editReturnsErrorWhenTargetTextNotFound() throws Exception {
        Files.write(tempDir.resolve("b.txt"), "hello".getBytes(StandardCharsets.UTF_8));
        EditTool editTool = new EditTool(tempDir);

        AgentToolResult result = editTool.execute(
                "3",
                map("path", "b.txt", "oldText", "missing", "newText", "world"),
                new AbortHandle(),
                partial -> {
                });

        assertTrue(textOf(result).contains("oldText not found"));
    }

    @Test
    void grepSupportsIgnoreCase() throws Exception {
        Files.write(tempDir.resolve("c.txt"), "Hello PI4J".getBytes(StandardCharsets.UTF_8));
        GrepTool grepTool = new GrepTool(tempDir);

        AgentToolResult result = grepTool.execute(
                "4",
                map("path", ".", "pattern", "pi4j", "ignoreCase", true),
                new AbortHandle(),
                partial -> {
                });

        assertTrue(textOf(result).contains("c.txt:1:Hello PI4J"));
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
