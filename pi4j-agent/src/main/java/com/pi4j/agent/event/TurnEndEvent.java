package com.pi4j.agent.event;

import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.ToolResultMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TurnEndEvent extends AgentEvent {
    private final AssistantMessage message;
    private final List<ToolResultMessage> toolResults;
    private final int turnIndex;

    public TurnEndEvent(AssistantMessage message, List<ToolResultMessage> toolResults, int turnIndex) {
        super("turn_end");
        this.message = message;
        this.toolResults = Collections.unmodifiableList(new ArrayList<ToolResultMessage>(toolResults));
        this.turnIndex = turnIndex;
    }

    public AssistantMessage getMessage() {
        return message;
    }

    public List<ToolResultMessage> getToolResults() {
        return toolResults;
    }

    /** 本次循环内从 0 递增的回合序号。 */
    public int getTurnIndex() {
        return turnIndex;
    }
}
