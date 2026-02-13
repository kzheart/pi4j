package com.pi4j.examples.support;

import com.pi4j.ai.types.Model;
import java.util.Arrays;
import java.util.Collections;

public final class ExampleModels {
    private ExampleModels() {
    }

    public static Model mockModel(String id, String api, String provider) {
        return new Model(
                id,
                id,
                api,
                provider,
                "http://localhost/mock",
                false,
                Arrays.asList("text"),
                null,
                8192,
                2048,
                Collections.<String, String>emptyMap());
    }
}
