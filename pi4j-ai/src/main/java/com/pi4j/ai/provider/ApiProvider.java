package com.pi4j.ai.provider;

import com.pi4j.ai.stream.AssistantMessageEventStream;
import com.pi4j.ai.types.Context;
import com.pi4j.ai.types.Model;

public interface ApiProvider {
    String getApi();

    AssistantMessageEventStream stream(Model model, Context context, StreamOptions options);
}
