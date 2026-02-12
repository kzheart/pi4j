package com.pi4j.agent;

import java.util.Objects;

public abstract class AgentMessage {
    private final String role;
    private final long timestamp;

    protected AgentMessage(String role, long timestamp) {
        this.role = Objects.requireNonNull(role, "role");
        this.timestamp = timestamp;
    }

    public String getRole() {
        return role;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
