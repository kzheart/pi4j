package com.pi4j.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pi4j.agent.tool.AgentToolResult;
import com.pi4j.ai.provider.AbortHandle;
import com.pi4j.ai.types.TextContent;
import com.pi4j.tools.edit.EditTool;
import com.pi4j.tools.find.FindTool;
import com.pi4j.tools.grep.GrepTool;
import com.pi4j.tools.ls.LsTool;
import com.pi4j.tools.read.ReadTool;
import com.pi4j.tools.write.WriteTool;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileToolsTest {

    @TempDir
    Path tempDir;

    @Test
    void writeThenReadReturnsExpectedContent() {
        WriteTool writeTool = new WriteTool(tempDir);
        ReadTool readTool = new ReadTool(tempDir);

        AgentToolResult writeResult = writeTool.execute(
                "1",
                map("path", "a.txt", "content", "line1\nline2"),
                new AbortHandle(),
                partial -> {
                });
        assertTrue(textOf(writeResult).contains("wrote"));

        AgentToolResult readResult = readTool.execute(
                "2",
                map("path", "a.txt"),
                new AbortHandle(),
                partial -> {
                });
        assertEquals("line1\nline2", textOf(readResult));
    }

    @Test
    void editReplacesContent() throws Exception {
        Path file = tempDir.resolve("edit.txt");
        Files.write(file, "hello world".getBytes(StandardCharsets.UTF_8));
        EditTool editTool = new EditTool(tempDir);

        AgentToolResult result = editTool.execute(
                "3",
                map("path", "edit.txt", "oldText", "world", "newText", "pi4j"),
                new AbortHandle(),
                partial -> {
                });

        assertTrue(textOf(result).contains("updated file"));
        assertEquals("hello pi4j", new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    @Test
    void lsFindAndGrepReturnMatches() throws Exception {
        Files.createDirectories(tempDir.resolve("dir"));
        Files.write(tempDir.resolve("dir").resolve("alpha.txt"), "target line".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("dir").resolve("beta.log"), "other".getBytes(StandardCharsets.UTF_8));

        LsTool lsTool = new LsTool(tempDir);
        FindTool findTool = new FindTool(tempDir);
        GrepTool grepTool = new GrepTool(tempDir);

        AgentToolResult lsResult = lsTool.execute(
                "4",
                map("path", "dir"),
                new AbortHandle(),
                partial -> {
                });
        assertTrue(textOf(lsResult).contains("alpha.txt"));

        AgentToolResult findResult = findTool.execute(
                "5",
                map("path", "dir", "pattern", "alpha"),
                new AbortHandle(),
                partial -> {
                });
        assertTrue(textOf(findResult).contains("dir/alpha.txt"));

        AgentToolResult grepResult = grepTool.execute(
                "6",
                map("path", "dir", "pattern", "target"),
                new AbortHandle(),
                partial -> {
                });
        assertTrue(textOf(grepResult).contains("alpha.txt:1:target line"));
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
