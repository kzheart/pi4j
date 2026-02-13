package com.pi4j.agent.session;

import com.google.gson.JsonObject;
import com.pi4j.ai.util.JsonUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SessionManager {
    private final Path sessionDir;

    public SessionManager(Path sessionDir) {
        this.sessionDir = sessionDir;
        ensureSessionDir();
    }

    public Session create(String sessionId) {
        String normalized = normalizeSessionId(sessionId);
        Path path = pathFor(normalized);
        if (!Files.exists(path)) {
            writeSessionHeader(path, normalized);
        }
        return new Session(normalized, path);
    }

    public Session load(String sessionId) {
        String normalized = normalizeSessionId(sessionId);
        Path path = pathFor(normalized);
        if (!Files.exists(path)) {
            throw new IllegalStateException("Session not found: " + normalized);
        }
        return new Session(normalized, path);
    }

    public List<SessionInfo> list() {
        List<SessionInfo> sessions = new ArrayList<SessionInfo>();
        ensureSessionDir();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(sessionDir, "*.jsonl")) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                String sessionId = fileName.substring(0, fileName.length() - ".jsonl".length());
                sessions.add(new Session(sessionId, path).getInfo());
            }
        } catch (IOException ioException) {
            throw new IllegalStateException("Failed to list sessions", ioException);
        }
        return sessions;
    }

    public void delete(String sessionId) {
        String normalized = normalizeSessionId(sessionId);
        Path path = pathFor(normalized);
        try {
            Files.deleteIfExists(path);
        } catch (IOException ioException) {
            throw new IllegalStateException("Failed to delete session: " + normalized, ioException);
        }
    }

    private void writeSessionHeader(Path path, String sessionId) {
        JsonObject data = new JsonObject();
        data.addProperty("sessionId", sessionId);
        JsonObject header = new JsonObject();
        header.addProperty("type", "session");
        header.addProperty("id", UUID.randomUUID().toString());
        header.addProperty("timestamp", System.currentTimeMillis());
        header.add("data", data);
        String line = JsonUtil.gson().toJson(header) + "\n";
        try {
            Files.write(
                    path,
                    line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        } catch (IOException ioException) {
            throw new IllegalStateException("Failed to create session: " + sessionId, ioException);
        }
    }

    private Path pathFor(String sessionId) {
        ensureSessionDir();
        return sessionDir.resolve(sessionId + ".jsonl");
    }

    private void ensureSessionDir() {
        try {
            Files.createDirectories(sessionDir);
        } catch (IOException ioException) {
            throw new IllegalStateException("Failed to initialize session directory: " + sessionDir, ioException);
        }
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        String trimmed = sessionId.trim();
        String normalized = trimmed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Invalid sessionId: " + sessionId);
        }
        return normalized;
    }
}
