package com.pi4j.agent.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToolRegistry {
    private final Map<String, AgentTool> tools = new LinkedHashMap<String, AgentTool>();

    public void register(AgentTool tool) {
        tools.put(tool.getName(), tool);
    }

    public void unregister(String toolName) {
        tools.remove(toolName);
    }

    public void replace(String toolName, AgentTool newTool) {
        tools.put(toolName, newTool);
    }

    public AgentTool get(String name) {
        return tools.get(name);
    }

    public List<AgentTool> getAll() {
        return new ArrayList<AgentTool>(tools.values());
    }

    public boolean has(String name) {
        return tools.containsKey(name);
    }
}
