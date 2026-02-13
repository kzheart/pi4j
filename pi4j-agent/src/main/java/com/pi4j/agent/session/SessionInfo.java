package com.pi4j.agent.session;

import java.nio.file.Path;

public final class SessionInfo {
    private final String sessionId;
    private final Path path;
    private final long sizeBytes;
    private final long lastModifiedAt;

    public SessionInfo(String sessionId, Path path, long sizeBytes, long lastModifiedAt) {
        this.sessionId = sessionId;
        this.path = path;
        this.sizeBytes = sizeBytes;
        this.lastModifiedAt = lastModifiedAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Path getPath() {
        return path;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public long getLastModifiedAt() {
        return lastModifiedAt;
    }
}
