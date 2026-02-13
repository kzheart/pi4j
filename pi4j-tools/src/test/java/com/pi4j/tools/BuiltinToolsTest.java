package com.pi4j.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.pi4j.agent.tool.AgentTool;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BuiltinToolsTest {

    @Test
    void allReturnsExpectedBuiltins() {
        List<String> names = BuiltinTools.all(Paths.get("."))
                .stream()
                .map(AgentTool::getName)
                .collect(Collectors.toList());

        assertEquals(Arrays.asList("read", "write", "edit", "bash", "grep", "find", "ls"), names);
    }

    @Test
    void selectReturnsSubsetInRequestedOrder() {
        List<String> names = BuiltinTools.select(Paths.get("."), "ls", "read", "missing")
                .stream()
                .map(AgentTool::getName)
                .collect(Collectors.toList());

        assertEquals(Arrays.asList("ls", "read"), names);
    }
}
