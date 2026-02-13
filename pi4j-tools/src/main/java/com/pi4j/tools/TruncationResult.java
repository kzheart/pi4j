package com.pi4j.tools;

public final class TruncationResult {
    private final String content;
    private final boolean truncated;
    private final String truncatedBy;
    private final int totalLines;
    private final int totalBytes;
    private final int outputLines;
    private final int outputBytes;

    public TruncationResult(
            String content,
            boolean truncated,
            String truncatedBy,
            int totalLines,
            int totalBytes,
            int outputLines,
            int outputBytes) {
        this.content = content;
        this.truncated = truncated;
        this.truncatedBy = truncatedBy;
        this.totalLines = totalLines;
        this.totalBytes = totalBytes;
        this.outputLines = outputLines;
        this.outputBytes = outputBytes;
    }

    public String getContent() {
        return content;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public String getTruncatedBy() {
        return truncatedBy;
    }

    public int getTotalLines() {
        return totalLines;
    }

    public int getTotalBytes() {
        return totalBytes;
    }

    public int getOutputLines() {
        return outputLines;
    }

    public int getOutputBytes() {
        return outputBytes;
    }
}
