package com.pi4j.tools;

import com.pi4j.agent.tool.AgentTool;
import com.pi4j.tools.bash.BashTool;
import com.pi4j.tools.edit.EditTool;
import com.pi4j.tools.find.FindTool;
import com.pi4j.tools.grep.GrepTool;
import com.pi4j.tools.ls.LsTool;
import com.pi4j.tools.read.ReadTool;
import com.pi4j.tools.write.WriteTool;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BuiltinTools {
    private BuiltinTools() {
    }

    public static ReadTool readTool(Path workDir) {
        return new ReadTool(workDir);
    }

    public static WriteTool writeTool(Path workDir) {
        return new WriteTool(workDir);
    }

    public static EditTool editTool(Path workDir) {
        return new EditTool(workDir);
    }

    public static BashTool bashTool(Path workDir) {
        return new BashTool(workDir);
    }

    public static GrepTool grepTool(Path workDir) {
        return new GrepTool(workDir);
    }

    public static FindTool findTool(Path workDir) {
        return new FindTool(workDir);
    }

    public static LsTool lsTool(Path workDir) {
        return new LsTool(workDir);
    }

    public static List<AgentTool> all(Path workDir) {
        List<AgentTool> tools = new ArrayList<AgentTool>();
        tools.add(readTool(workDir));
        tools.add(writeTool(workDir));
        tools.add(editTool(workDir));
        tools.add(bashTool(workDir));
        tools.add(grepTool(workDir));
        tools.add(findTool(workDir));
        tools.add(lsTool(workDir));
        return Collections.unmodifiableList(tools);
    }

    public static List<AgentTool> select(Path workDir, String... names) {
        if (names == null || names.length == 0) {
            return Collections.emptyList();
        }

        Map<String, AgentTool> lookup = new LinkedHashMap<String, AgentTool>();
        for (AgentTool tool : all(workDir)) {
            lookup.put(tool.getName().toLowerCase(Locale.ROOT), tool);
        }

        List<AgentTool> selected = new ArrayList<AgentTool>();
        for (String name : Arrays.asList(names)) {
            if (name == null) {
                continue;
            }
            AgentTool tool = lookup.get(name.toLowerCase(Locale.ROOT));
            if (tool != null) {
                selected.add(tool);
            }
        }
        return Collections.unmodifiableList(selected);
    }
}
