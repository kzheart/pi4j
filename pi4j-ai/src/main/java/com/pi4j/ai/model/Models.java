package com.pi4j.ai.model;

import com.pi4j.ai.constant.ApiTypes;
import com.pi4j.ai.constant.Providers;
import com.pi4j.ai.types.Model;
import java.util.Arrays;
import java.util.Collections;

public final class Models {
    public static final Model CLAUDE_SONNET_4_5 = new Model(
            "claude-sonnet-4-5-20250929",
            "Claude Sonnet 4.5",
            ApiTypes.ANTHROPIC_MESSAGES,
            Providers.ANTHROPIC,
            "https://api.anthropic.com",
            true,
            Arrays.asList("text", "image"),
            null,
            200000,
            8192,
            Collections.<String, String>emptyMap());

    public static final Model GPT_4_1 = new Model(
            "gpt-4.1",
            "GPT-4.1",
            ApiTypes.OPENAI_RESPONSES,
            Providers.OPENAI,
            "https://api.openai.com",
            true,
            Arrays.asList("text", "image"),
            null,
            128000,
            8192,
            Collections.<String, String>emptyMap());

    public static final Model GPT_4_1_MINI = new Model(
            "gpt-4.1-mini",
            "GPT-4.1 Mini",
            ApiTypes.OPENAI_RESPONSES,
            Providers.OPENAI,
            "https://api.openai.com",
            true,
            Arrays.asList("text", "image"),
            null,
            128000,
            8192,
            Collections.<String, String>emptyMap());

    public static final Model GEMINI_2_5_FLASH = new Model(
            "gemini-2.5-flash",
            "Gemini 2.5 Flash",
            ApiTypes.GOOGLE_GENERATIVE_AI,
            Providers.GOOGLE,
            "https://generativelanguage.googleapis.com",
            true,
            Arrays.asList("text", "image"),
            null,
            1048576,
            8192,
            Collections.<String, String>emptyMap());

    public static final Model QWEN_3_5_PLUS = new Model(
            "qwen3.5-plus",
            "Qwen 3.5 Plus",
            ApiTypes.OPENAI_COMPLETIONS,
            Providers.BAILIAN,
            "https://dashscope.aliyuncs.com/compatible-mode",
            false,
            Arrays.asList("text"),
            null,
            131072,
            8192,
            Collections.<String, String>emptyMap());

    public static final Model DEEPSEEK_CLAUDE_COMPAT = new Model(
            "deepseek-chat",
            "DeepSeek Chat (Anthropic Compat)",
            ApiTypes.ANTHROPIC_MESSAGES,
            Providers.DEEPSEEK,
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
