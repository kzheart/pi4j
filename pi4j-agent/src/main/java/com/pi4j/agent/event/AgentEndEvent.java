package com.pi4j.agent.event;

import com.pi4j.agent.AgentMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AgentEndEvent extends AgentEvent {
    private final List<AgentMessage> messages;

    public AgentEndEvent(List<AgentMessage> messages) {
        super("agent_end");
        this.messages = Collections.unmodifiableList(new ArrayList<AgentMessage>(messages));
    }

    public List<AgentMessage> getMessages() {
        return messages;
    }
}
