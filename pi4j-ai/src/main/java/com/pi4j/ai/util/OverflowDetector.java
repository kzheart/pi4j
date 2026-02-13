package com.pi4j.ai.util;

import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.StopReason;
import com.pi4j.ai.types.Usage;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public final class OverflowDetector {
    private static final List<Pattern> OVERFLOW_PATTERNS = Arrays.asList(
            Pattern.compile("prompt is too long", Pattern.CASE_INSENSITIVE),
            Pattern.compile("input is too long for requested model", Pattern.CASE_INSENSITIVE),
            Pattern.compile("exceeds the context window", Pattern.CASE_INSENSITIVE),
            Pattern.compile("input token count.*exceeds the maximum", Pattern.CASE_INSENSITIVE),
            Pattern.compile("maximum prompt length is \\d+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("reduce the length of the messages", Pattern.CASE_INSENSITIVE),
            Pattern.compile("maximum context length is \\d+ tokens", Pattern.CASE_INSENSITIVE),
            Pattern.compile("exceeds the limit of \\d+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("exceeds the available context size", Pattern.CASE_INSENSITIVE),
            Pattern.compile("greater than the context length", Pattern.CASE_INSENSITIVE),
            Pattern.compile("context window exceeds limit", Pattern.CASE_INSENSITIVE),
            Pattern.compile("exceeded model token limit", Pattern.CASE_INSENSITIVE),
            Pattern.compile("context[_ ]length[_ ]exceeded", Pattern.CASE_INSENSITIVE),
            Pattern.compile("too many tokens", Pattern.CASE_INSENSITIVE),
            Pattern.compile("token limit exceeded", Pattern.CASE_INSENSITIVE));

    private OverflowDetector() {
    }

    public static boolean isContextOverflow(AssistantMessage message, Integer contextWindow) {
        if (message == null) {
            return false;
        }

        if (message.getStopReason() == StopReason.ERROR && message.getErrorMessage() != null) {
            String errorMessage = message.getErrorMessage();
            for (Pattern pattern : OVERFLOW_PATTERNS) {
                if (pattern.matcher(errorMessage).find()) {
                    return true;
                }
            }
            if (Pattern.compile("^4(00|13)\\s*(status code)?\\s*\\(no body\\)", Pattern.CASE_INSENSITIVE)
                    .matcher(errorMessage)
                    .find()) {
                return true;
            }
        }

        if (contextWindow == null || contextWindow <= 0 || message.getStopReason() != StopReason.STOP) {
            return false;
        }
        Usage usage = message.getUsage();
        if (usage == null) {
            return false;
        }
        int inputTokens = usage.getInput() + usage.getCacheRead();
        return inputTokens > contextWindow;
    }
}
