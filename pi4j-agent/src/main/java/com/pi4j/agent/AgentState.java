package com.pi4j.agent;

import com.pi4j.agent.tool.AgentTool;
import com.pi4j.ai.provider.ErrorKind;
import com.pi4j.ai.types.Model;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AgentState {
    private final String systemPrompt;
    private final Model model;
    private final String thinkingLevel;
    private final List<AgentTool> tools;
    private final List<AgentMessage> messages;
    private final boolean streaming;
    private final AgentMessage streamMessage;
    private final Set<String> pendingToolCalls;
    private final String error;
    private final ErrorKind errorKind;

    public AgentState(
            String systemPrompt,
            Model model,
            String thinkingLevel,
            List<AgentTool> tools,
            List<AgentMessage> messages,
            boolean streaming,
            AgentMessage streamMessage,
            Set<String> pendingToolCalls,
            String error,
            ErrorKind errorKind) {
        this.systemPrompt = systemPrompt;
        this.model = model;
        this.thinkingLevel = thinkingLevel;
        this.tools = Collections.unmodifiableList(new ArrayList<AgentTool>(tools));
        this.messages = Collections.unmodifiableList(new ArrayList<AgentMessage>(messages));
        this.streaming = streaming;
        this.streamMessage = streamMessage;
        this.pendingToolCalls = Collections.unmodifiableSet(new LinkedHashSet<String>(pendingToolCalls));
        this.error = error;
        this.errorKind = errorKind;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public Model getModel() {
        return model;
    }

    public String getThinkingLevel() {
        return thinkingLevel;
    }

    public List<AgentTool> getTools() {
        return tools;
    }

    public List<AgentMessage> getMessages() {
        return messages;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public AgentMessage getStreamMessage() {
        return streamMessage;
    }

    public Set<String> getPendingToolCalls() {
        return pendingToolCalls;
    }

    public String getError() {
        return error;
    }

    /** 最近一次失败的错误类别；无错误或无法归类时为 null / UNKNOWN。 */
    public ErrorKind getErrorKind() {
        return errorKind;
    }
}
