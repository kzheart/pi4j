package com.pi4j.examples.support;

import com.pi4j.ai.provider.ApiProvider;
import com.pi4j.ai.provider.StreamOptions;
import com.pi4j.ai.stream.AssistantMessageEventStream;
import com.pi4j.ai.stream.DoneEvent;
import com.pi4j.ai.stream.StartEvent;
import com.pi4j.ai.stream.TextDeltaEvent;
import com.pi4j.ai.stream.TextEndEvent;
import com.pi4j.ai.stream.TextStartEvent;
import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.Context;
import com.pi4j.ai.types.Model;
import com.pi4j.ai.types.StopReason;
import com.pi4j.ai.types.TextContent;
import java.util.Collections;

public final class SimpleTextProvider implements ApiProvider {
    private final String api;
    private final String reply;

    public SimpleTextProvider(String api, String reply) {
        this.api = api;
        this.reply = reply;
    }

    @Override
    public String getApi() {
        return api;
    }

    @Override
    public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
        AssistantMessage assistantMessage = new AssistantMessage(
                Collections.<ContentBlock>singletonList(new TextContent(reply)),
                model.getApi(),
                model.getProvider(),
                model.getId(),
                null,
                StopReason.STOP,
                null);

        AssistantMessageEventStream stream = new AssistantMessageEventStream();
        stream.push(new StartEvent());
        stream.push(new TextStartEvent(0));
        stream.push(new TextDeltaEvent(0, reply, assistantMessage));
        stream.push(new TextEndEvent(0));
        stream.push(new DoneEvent(StopReason.STOP, assistantMessage));
        stream.end(assistantMessage);
        return stream;
    }
}
