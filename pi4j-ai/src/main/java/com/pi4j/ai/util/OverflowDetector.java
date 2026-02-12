package com.pi4j.ai.util;

import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.Usage;

public final class OverflowDetector {
    private OverflowDetector() {
    }

    public static boolean isContextOverflow(AssistantMessage message, Integer contextWindow) {
        if (message == null || contextWindow == null || contextWindow <= 0) {
            return false;
        }
        Usage usage = message.getUsage();
        if (usage == null) {
            return false;
        }
        return usage.getTotalTokens() > contextWindow;
    }
}
