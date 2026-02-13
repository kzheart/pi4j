package com.pi4j.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pi4j.ai.stream.AssistantMessageEventStream;
import com.pi4j.ai.types.Context;
import com.pi4j.ai.types.Model;
import java.util.Arrays;
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

    @Test
    void registerProviderWithApiAndProviderName() {
        ApiProvider defaultProvider = new ApiProvider() {
            @Override
            public String getApi() {
                return "openai-completions";
            }

            @Override
            public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
                return new AssistantMessageEventStream();
            }
        };

        ApiProvider mistralProvider = new ApiProvider() {
            @Override
            public String getApi() {
                return "openai-completions";
            }

            @Override
            public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
                return new AssistantMessageEventStream();
            }
        };

        ApiRegistry.register(defaultProvider);
        ApiRegistry.register("openai-completions", "mistral", mistralProvider);

        assertSame(mistralProvider, ApiRegistry.getProvider("openai-completions", "mistral"));
        assertSame(defaultProvider, ApiRegistry.getProvider("openai-completions", "openai"));
    }

    @Test
    void getProviderThrowsWhenApiIsMissing() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> ApiRegistry.getProvider("missing-api"));
        assertEquals("No provider registered for api: missing-api", exception.getMessage());
    }

    @Test
    void streamUsesApiAndProviderSpecificRegistration() {
        AssistantMessageEventStream defaultStream = new AssistantMessageEventStream();
        AssistantMessageEventStream mistralStream = new AssistantMessageEventStream();

        ApiProvider defaultProvider = new ApiProvider() {
            @Override
            public String getApi() {
                return "openai-completions";
            }

            @Override
            public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
                return defaultStream;
            }
        };

        ApiProvider mistralProvider = new ApiProvider() {
            @Override
            public String getApi() {
                return "openai-completions";
            }

            @Override
            public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
                return mistralStream;
            }
        };

        ApiRegistry.register(defaultProvider);
        ApiRegistry.register("openai-completions", "mistral", mistralProvider);

        Model model = new Model(
                "demo",
                "Demo",
                "openai-completions",
                "mistral",
                "https://api.example.com",
                false,
                Arrays.asList("text"),
                null,
                64000,
                2048,
                Collections.<String, String>emptyMap());

        Context context = new Context(null, Collections.emptyList(), Collections.emptyList());
        StreamOptions options = StreamOptions.builder().apiKey("test-key").build();

        AssistantMessageEventStream stream = ApiRegistry.stream(model, context, options);
        assertSame(mistralStream, stream);
    }
}
