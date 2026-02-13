package com.pi4j.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PathUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveWithinKeepsPathInsideWorkDir() {
        Path resolved = PathUtils.resolveWithin(tempDir, "sub/file.txt");
        assertEquals(tempDir.resolve("sub/file.txt").toAbsolutePath().normalize(), resolved);
        assertTrue(PathUtils.isWithin(tempDir, resolved));
    }

    @Test
    void resolveWithinRejectsEscapingPath() {
        assertThrows(IllegalArgumentException.class, () -> PathUtils.resolveWithin(tempDir, "../outside.txt"));
    }

    @Test
    void resolveWithinAcceptsAbsolutePathInsideWorkDir() throws Exception {
        Path nested = tempDir.resolve("nested");
        Files.createDirectories(nested);

        Path resolved = PathUtils.resolveWithin(tempDir, nested.toString());
        assertEquals(nested.toAbsolutePath().normalize(), resolved);
    }
}
