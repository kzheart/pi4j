package com.pi4j.ai.provider;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.pi4j.ai.stream.AssistantMessageEventStream;
import com.pi4j.ai.types.Context;
import com.pi4j.ai.types.Model;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ApiRegistryTest {

    @AfterEach
    void cleanup() {
        ApiRegistry.clear();
    }

    @Test
    void registerAndGetProvider() {
        ApiProvider provider = new ApiProvider() {
            @Override
            public String getApi() {
                return "demo";
            }

            @Override
            public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
                return new AssistantMessageEventStream();
            }
        };

        ApiRegistry.register(provider);

        assertSame(provider, ApiRegistry.getProvider("demo"));
    }
}
