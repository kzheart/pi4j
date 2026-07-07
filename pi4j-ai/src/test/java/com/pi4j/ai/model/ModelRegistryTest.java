package com.pi4j.ai.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.pi4j.ai.types.Model;
import org.junit.jupiter.api.Test;

class ModelRegistryTest {

    @Test
    void findByProviderAndAlias() {
        Model model = ModelRegistry.find("anthropic:claude-sonnet-4-5");
        assertEquals("claude-sonnet-4-5-20250929", model.getId());
    }

    @Test
    void findByBareId() {
        Model model = ModelRegistry.find("claude-sonnet-4-5-20250929");
        assertEquals("claude-sonnet-4-5-20250929", model.getId());
    }

    @Test
    void findByAliasIsCaseInsensitive() {
        Model model = ModelRegistry.find("CLAUDE-SONNET-4-5");
        assertEquals("claude-sonnet-4-5-20250929", model.getId());
    }

    @Test
    void findByProviderAndAliasPreservesOriginalIdCasing() {
        Model model = ModelRegistry.find("minimax:minimax-m2.5");
        assertEquals("MiniMax-M2.5", model.getId());
    }

    @Test
    void findReturnsNullForUnknownOrBlankSpec() {
        assertNull(ModelRegistry.find("unknown"));
        assertNull(ModelRegistry.find(null));
        assertNull(ModelRegistry.find("   "));
    }

    @Test
    void allReturnsSevenBuiltInModels() {
        assertEquals(7, ModelRegistry.all().size());
    }
}
