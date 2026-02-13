package com.pi4j.examples;

import com.pi4j.agent.Agent;
import com.pi4j.agent.AgentOptions;
import com.pi4j.agent.tool.AgentTool;
import com.pi4j.ai.provider.ApiRegistry;
import com.pi4j.examples.support.ExampleModels;
import com.pi4j.examples.support.SimpleTextProvider;
import com.pi4j.tools.BuiltinTools;
import java.nio.file.Paths;
import java.util.List;

public final class BuiltinToolsExample {
    private static final String API = "mock-builtin-api";

    private BuiltinToolsExample() {
    }

    public static void main(String[] args) {
        ApiRegistry.clear();
        ApiRegistry.register(new SimpleTextProvider(API, "内置工具已挂载。"));

        List<AgentTool> tools = BuiltinTools.select(Paths.get("."), "read", "write", "ls", "bash");
        Agent agent = new Agent(AgentOptions.builder()
                .systemPrompt("你是工具挂载示例助手")
                .model(ExampleModels.mockModel("mock-builtin-model", API, "mock-builtin"))
                .tools(tools)
                .getApiKey(provider -> "example-key")
                .build());

        agent.prompt("告诉我当前挂载了哪些工具").join();

        for (AgentTool tool : tools) {
            System.out.println(tool.getName());
        }
    }
}
