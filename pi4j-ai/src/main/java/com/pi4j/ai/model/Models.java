package com.pi4j.ai.model;

import com.pi4j.ai.types.Model;
import java.util.Arrays;
import java.util.Collections;

public final class Models {
    public static final Model DEEPSEEK_CLAUDE_COMPAT = new Model(
            "deepseek-chat",
            "DeepSeek Chat (Anthropic Compat)",
            "anthropic-messages",
            "deepseek",
            "https://api.deepseek.com/anthropic",
            false,
            Arrays.asList("text"),
            null,
            65536,
            4096,
            Collections.<String, String>emptyMap());

    private Models() {
    }
}
