package com.pi4j.agent.session;

import com.google.gson.JsonObject;
import com.pi4j.agent.AgentMessage;
import com.pi4j.ai.util.JsonUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Session {
    private final String sessionId;
    private final Path path;

    Session(String sessionId, Path path) {
        this.sessionId = sessionId;
        this.path = path;
    }

    public synchronized void appendMessage(AgentMessage message) {
        JsonObject data = SessionMessageCodec.encode(message);
        SessionEntry entry = new SessionEntry(
                "message",
                UUID.randomUUID().toString(),
                null,
                System.currentTimeMillis(),
                data);
        appendEntry(entry);
    }

    public synchronized List<AgentMessage> getMessages() {
        List<AgentMessage> messages = new ArrayList<AgentMessage>();
        for (SessionEntry entry : readEntries()) {
            if (!"message".equals(entry.getType())) {
                continue;
            }
            messages.add(SessionMessageCodec.decode(entry.getData()));
        }
        return messages;
    }

    public SessionInfo getInfo() {
        try {
            long size = Files.exists(path) ? Files.size(path) : 0L;
            long lastModified = Files.exists(path)
                    ? Files.getLastModifiedTime(path).toMillis()
                    : System.currentTimeMillis();
            return new SessionInfo(sessionId, path, size, lastModified);
        } catch (IOException ioException) {
            throw new IllegalStateException("Failed to read session info: " + sessionId, ioException);
        }
    }

    public String getSessionId() {
        return sessionId;
    }

    public Path getPath() {
        return path;
    }

    private void appendEntry(SessionEntry entry) {
        JsonObject json = new JsonObject();
        json.addProperty("type", entry.getType());
        json.addProperty("id", entry.getId());
        if (entry.getParentId() != null) {
            json.addProperty("parentId", entry.getParentId());
        }
        json.addProperty("timestamp", entry.getTimestamp());
        json.add("data", entry.getData());
        String line = JsonUtil.gson().toJson(json) + "\n";
        try {
            Files.write(
                    path,
                    line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND);
        } catch (IOException ioException) {
            throw new IllegalStateException("Failed to append session entry: " + sessionId, ioException);
        }
    }

    private List<SessionEntry> readEntries() {
        List<SessionEntry> entries = new ArrayList<SessionEntry>();
        if (!Files.exists(path)) {
            return entries;
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }
                JsonObject json = JsonUtil.gson().fromJson(line, JsonObject.class);
                JsonObject data = json.has("data") && json.get("data").isJsonObject()
                        ? json.getAsJsonObject("data")
                        : new JsonObject();
                entries.add(new SessionEntry(
                        json.get("type").getAsString(),
                        json.get("id").getAsString(),
                        json.has("parentId") && !json.get("parentId").isJsonNull() ? json.get("parentId").getAsString() : null,
                        json.has("timestamp") ? json.get("timestamp").getAsLong() : System.currentTimeMillis(),
                        data));
            }
        } catch (IOException ioException) {
            throw new IllegalStateException("Failed to read session entries: " + sessionId, ioException);
        }
        return entries;
    }
}
