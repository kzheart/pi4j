package com.pi4j.tools;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class Truncator {
    public static final int DEFAULT_MAX_LINES = 2000;
    public static final int DEFAULT_MAX_BYTES = 50 * 1024;

    private Truncator() {
    }

    public static TruncationResult truncateHead(String content, int maxLines, int maxBytes) {
        String source = content == null ? "" : content;
        int lineLimit = positiveOrDefault(maxLines, DEFAULT_MAX_LINES);
        int byteLimit = positiveOrDefault(maxBytes, DEFAULT_MAX_BYTES);

        int totalLines = countLines(source);
        int totalBytes = sizeOf(source);

        List<String> lines = splitLines(source);
        boolean truncated = false;
        String truncatedBy = null;
        String output = source;

        if (lines.size() > lineLimit) {
            output = join(lines.subList(0, lineLimit));
            truncated = true;
            truncatedBy = "lines";
        }

        if (sizeOf(output) > byteLimit) {
            output = truncateToBytesHead(output, byteLimit);
            if (!truncated) {
                truncated = true;
                truncatedBy = "bytes";
            }
        }

        return new TruncationResult(
                output,
                truncated,
                truncatedBy,
                totalLines,
                totalBytes,
                countLines(output),
                sizeOf(output));
    }

    public static TruncationResult truncateTail(String content, int maxLines, int maxBytes) {
        String source = content == null ? "" : content;
        int lineLimit = positiveOrDefault(maxLines, DEFAULT_MAX_LINES);
        int byteLimit = positiveOrDefault(maxBytes, DEFAULT_MAX_BYTES);

        int totalLines = countLines(source);
        int totalBytes = sizeOf(source);

        List<String> lines = splitLines(source);
        boolean truncated = false;
        String truncatedBy = null;
        String output = source;

        if (lines.size() > lineLimit) {
            output = join(lines.subList(lines.size() - lineLimit, lines.size()));
            truncated = true;
            truncatedBy = "lines";
        }

        if (sizeOf(output) > byteLimit) {
            output = truncateToBytesTail(output, byteLimit);
            if (!truncated) {
                truncated = true;
                truncatedBy = "bytes";
            }
        }

        return new TruncationResult(
                output,
                truncated,
                truncatedBy,
                totalLines,
                totalBytes,
                countLines(output),
                sizeOf(output));
    }

    public static String truncateLine(String line, int maxChars) {
        String source = line == null ? "" : line;
        if (maxChars <= 0) {
            return "";
        }
        if (source.length() <= maxChars) {
            return source;
        }
        return source.substring(0, maxChars);
    }

    private static int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private static int sizeOf(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static int countLines(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private static List<String> splitLines(String content) {
        List<String> lines = new ArrayList<String>();
        if (content == null || content.isEmpty()) {
            return lines;
        }

        int start = 0;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lines.add(content.substring(start, i));
                start = i + 1;
            }
        }
        lines.add(content.substring(start));
        return lines;
    }

    private static String join(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(lines.get(i));
        }
        return builder.toString();
    }

    private static String truncateToBytesHead(String content, int maxBytes) {
        if (maxBytes <= 0 || content.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            builder.append(content.charAt(i));
            if (sizeOf(builder.toString()) > maxBytes) {
                builder.deleteCharAt(builder.length() - 1);
                break;
            }
        }
        return builder.toString();
    }

    private static String truncateToBytesTail(String content, int maxBytes) {
        if (maxBytes <= 0 || content.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = content.length() - 1; i >= 0; i--) {
            builder.insert(0, content.charAt(i));
            if (sizeOf(builder.toString()) > maxBytes) {
                builder.deleteCharAt(0);
                break;
            }
        }
        return builder.toString();
    }
}
