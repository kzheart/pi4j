package com.pi4j.agent.session;

import com.google.gson.JsonObject;

public final class SessionEntry {
    private final String type;
    private final String id;
    private final String parentId;
    private final long timestamp;
    private final JsonObject data;

    public SessionEntry(String type, String id, String parentId, long timestamp, JsonObject data) {
        this.type = type;
        this.id = id;
        this.parentId = parentId;
        this.timestamp = timestamp;
        this.data = data;
    }

    public String getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public String getParentId() {
        return parentId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public JsonObject getData() {
        return data;
    }
}
