package com.pi4j.examples.support;

import com.pi4j.ai.provider.ApiProvider;
import com.pi4j.ai.provider.StreamOptions;
import com.pi4j.ai.stream.AssistantMessageEventStream;
import com.pi4j.ai.stream.DoneEvent;
import com.pi4j.ai.stream.StartEvent;
import com.pi4j.ai.stream.TextDeltaEvent;
import com.pi4j.ai.stream.TextEndEvent;
import com.pi4j.ai.stream.TextStartEvent;
import com.pi4j.ai.stream.ToolCallEndEvent;
import com.pi4j.ai.stream.ToolCallStartEvent;
import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.Context;
import com.pi4j.ai.types.Message;
import com.pi4j.ai.types.Model;
import com.pi4j.ai.types.StopReason;
import com.pi4j.ai.types.TextContent;
import com.pi4j.ai.types.ToolCallContent;
import com.pi4j.ai.types.ToolResultMessage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolCallProvider implements ApiProvider {
    private final String api;

    public ToolCallProvider(String api) {
        this.api = api;
    }

    @Override
    public String getApi() {
        return api;
    }

    @Override
    public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
        ToolResultMessage toolResult = lastToolResult(context);
        if (toolResult == null) {
            return firstTurn(model);
        }
        return secondTurn(model, toolResult);
    }

    private AssistantMessageEventStream firstTurn(Model model) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("zone", "Asia/Shanghai");
        ToolCallContent toolCall = new ToolCallContent("call-time-1", "get_time", args);

        AssistantMessage assistantMessage = new AssistantMessage(
                Collections.<ContentBlock>singletonList(toolCall),
                model.getApi(),
                model.getProvider(),
                model.getId(),
                null,
                StopReason.TOOL_USE,
                null);

        AssistantMessageEventStream stream = new AssistantMessageEventStream();
        stream.push(new StartEvent());
        stream.push(new ToolCallStartEvent(0));
        stream.push(new ToolCallEndEvent(0, toolCall, assistantMessage));
        stream.push(new DoneEvent(StopReason.TOOL_USE, assistantMessage));
        stream.end(assistantMessage);
        return stream;
    }

    private AssistantMessageEventStream secondTurn(Model model, ToolResultMessage toolResult) {
        String toolText = "";
        if (!toolResult.getContent().isEmpty() && toolResult.getContent().get(0) instanceof TextContent) {
            toolText = ((TextContent) toolResult.getContent().get(0)).getText();
        }
        String reply = "工具结果: " + toolText;

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

    private ToolResultMessage lastToolResult(Context context) {
        for (int i = context.getMessages().size() - 1; i >= 0; i--) {
            Message message = context.getMessages().get(i);
            if (message instanceof ToolResultMessage) {
                return (ToolResultMessage) message;
            }
        }
        return null;
    }
}
