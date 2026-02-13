package com.pi4j.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TruncatorTest {

    @Test
    void truncateHeadUsesLineLimitFirst() {
        TruncationResult result = Truncator.truncateHead("a\nb\nc\nd", 2, 1024);

        assertEquals("a\nb", result.getContent());
        assertTrue(result.isTruncated());
        assertEquals("lines", result.getTruncatedBy());
        assertEquals(4, result.getTotalLines());
        assertEquals(2, result.getOutputLines());
    }

    @Test
    void truncateTailKeepsLastLines() {
        TruncationResult result = Truncator.truncateTail("a\nb\nc\nd", 2, 1024);

        assertEquals("c\nd", result.getContent());
        assertTrue(result.isTruncated());
        assertEquals("lines", result.getTruncatedBy());
    }

    @Test
    void truncateHeadFallsBackToByteLimit() {
        TruncationResult result = Truncator.truncateHead("abcdef", 10, 3);

        assertEquals("abc", result.getContent());
        assertTrue(result.isTruncated());
        assertEquals("bytes", result.getTruncatedBy());
    }

    @Test
    void truncateTailFallsBackToByteLimit() {
        TruncationResult result = Truncator.truncateTail("abcdef", 10, 3);

        assertEquals("def", result.getContent());
        assertTrue(result.isTruncated());
        assertEquals("bytes", result.getTruncatedBy());
    }

    @Test
    void truncateLineKeepsShortContentAndCutsLongContent() {
        assertEquals("abc", Truncator.truncateLine("abc", 10));
        assertEquals("ab", Truncator.truncateLine("abcd", 2));
        assertEquals("", Truncator.truncateLine("abcd", 0));
    }

    @Test
    void truncateHeadWithoutOverflowDoesNotMarkTruncated() {
        TruncationResult result = Truncator.truncateHead("a\nb", 10, 1024);
        assertFalse(result.isTruncated());
    }
}
