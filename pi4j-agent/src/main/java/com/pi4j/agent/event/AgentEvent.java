package com.pi4j.agent.event;

import java.util.Objects;

public abstract class AgentEvent {
    private final String type;

    protected AgentEvent(String type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public String getType() {
        return type;
    }
}
