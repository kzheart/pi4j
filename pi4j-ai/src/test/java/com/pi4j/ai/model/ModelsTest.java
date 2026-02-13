package com.pi4j.ai.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ModelsTest {

    @Test
    void providesMainstreamModelConstants() {
        assertNotNull(Models.CLAUDE_SONNET_4_5);
        assertNotNull(Models.GPT_4_1);
        assertNotNull(Models.GPT_4_1_MINI);
        assertNotNull(Models.GEMINI_2_5_FLASH);
        assertNotNull(Models.DEEPSEEK_CLAUDE_COMPAT);
        assertEquals("anthropic", Models.CLAUDE_SONNET_4_5.getProvider());
        assertEquals("openai", Models.GPT_4_1.getProvider());
        assertEquals("google", Models.GEMINI_2_5_FLASH.getProvider());
    }
}
