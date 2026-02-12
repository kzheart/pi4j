package com.pi4j.agent.event;

import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.ToolResultMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TurnEndEvent extends AgentEvent {
    private final AssistantMessage message;
    private final List<ToolResultMessage> toolResults;

    public TurnEndEvent(AssistantMessage message, List<ToolResultMessage> toolResults) {
        super("turn_end");
        this.message = message;
        this.toolResults = Collections.unmodifiableList(new ArrayList<ToolResultMessage>(toolResults));
    }

    public AssistantMessage getMessage() {
        return message;
    }

    public List<ToolResultMessage> getToolResults() {
        return toolResults;
    }
}
