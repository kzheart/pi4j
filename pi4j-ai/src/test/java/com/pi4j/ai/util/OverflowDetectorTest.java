package com.pi4j.ai.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.StopReason;
import com.pi4j.ai.types.Usage;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class OverflowDetectorTest {

    @Test
    void detectOverflowByUsageTotalTokens() {
        Usage usage = new Usage(100, 50, 0, 0, 200, null);
        AssistantMessage message = new AssistantMessage(
                Collections.emptyList(),
                "anthropic-messages",
                "anthropic",
                "claude",
                usage,
                StopReason.STOP,
                null);

        assertTrue(OverflowDetector.isContextOverflow(message, 128));
        assertFalse(OverflowDetector.isContextOverflow(message, 256));
    }
}
