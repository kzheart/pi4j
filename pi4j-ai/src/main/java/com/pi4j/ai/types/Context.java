package com.pi4j.ai.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Context {
    private final String systemPrompt;
    private final List<Message> messages;
    private final List<Tool> tools;

    public Context(String systemPrompt, List<Message> messages, List<Tool> tools) {
        this.systemPrompt = systemPrompt;
        this.messages = Collections.unmodifiableList(new ArrayList<>(messages));
        this.tools = Collections.unmodifiableList(new ArrayList<>(tools));
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public List<Tool> getTools() {
        return tools;
    }
}
