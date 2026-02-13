package com.pi4j.tools;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class PathUtils {
    private PathUtils() {
    }

    public static Path resolveWithin(Path workDir, String inputPath) {
        if (workDir == null) {
            throw new IllegalArgumentException("workDir is required");
        }
        if (inputPath == null || inputPath.trim().isEmpty()) {
            throw new IllegalArgumentException("path is required");
        }

        Path base = workDir.toAbsolutePath().normalize();
        Path candidate = Paths.get(inputPath);
        Path resolved = candidate.isAbsolute()
                ? candidate.toAbsolutePath().normalize()
                : base.resolve(candidate).normalize();

        if (!isWithin(base, resolved)) {
            throw new IllegalArgumentException("path escapes workDir: " + inputPath);
        }
        return resolved;
    }

    public static boolean isWithin(Path workDir, Path candidate) {
        if (workDir == null || candidate == null) {
            return false;
        }
        Path base = workDir.toAbsolutePath().normalize();
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        return normalizedCandidate.equals(base) || normalizedCandidate.startsWith(base);
    }
}
