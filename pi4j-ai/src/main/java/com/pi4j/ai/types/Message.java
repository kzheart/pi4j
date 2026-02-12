package com.pi4j.ai.types;

import java.util.Objects;

public abstract class Message {
    private final String role;
    private final long timestamp;

    protected Message(String role) {
        this(role, System.currentTimeMillis());
    }

    protected Message(String role, long timestamp) {
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
