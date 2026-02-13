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
    void detectOverflowByUsageInputTokens() {
        Usage usage = new Usage(200, 50, 10, 0, 260, null);
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

    @Test
    void detectOverflowByErrorPattern() {
        AssistantMessage message = new AssistantMessage(
                Collections.emptyList(),
                "openai-completions",
                "openai",
                "gpt-4.1",
                null,
                StopReason.ERROR,
                "Your input exceeds the context window of this model");
        assertTrue(OverflowDetector.isContextOverflow(message, null));
    }

    @Test
    void detectOverflowByNoBodyStatusPattern() {
        AssistantMessage message = new AssistantMessage(
                Collections.emptyList(),
                "openai-completions",
                "mistral",
                "mistral-small",
                null,
                StopReason.ERROR,
                "413 status code (no body)");
        assertTrue(OverflowDetector.isContextOverflow(message, null));
    }
}
