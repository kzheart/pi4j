package com.pi4j.ai.provider.bedrock;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pi4j.ai.provider.AbortHandle;
import com.pi4j.ai.provider.ApiProvider;
import com.pi4j.ai.provider.StreamOptions;
import com.pi4j.ai.stream.AssistantMessageEventStream;
import com.pi4j.ai.stream.DoneEvent;
import com.pi4j.ai.stream.ErrorEvent;
import com.pi4j.ai.stream.StartEvent;
import com.pi4j.ai.stream.TextDeltaEvent;
import com.pi4j.ai.stream.TextEndEvent;
import com.pi4j.ai.stream.TextStartEvent;
import com.pi4j.ai.stream.ThinkingDeltaEvent;
import com.pi4j.ai.stream.ThinkingEndEvent;
import com.pi4j.ai.stream.ThinkingStartEvent;
import com.pi4j.ai.stream.ToolCallDeltaEvent;
import com.pi4j.ai.stream.ToolCallEndEvent;
import com.pi4j.ai.stream.ToolCallStartEvent;
import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.Context;
import com.pi4j.ai.types.Message;
import com.pi4j.ai.types.Model;
import com.pi4j.ai.types.StopReason;
import com.pi4j.ai.types.TextContent;
import com.pi4j.ai.types.ThinkingContent;
import com.pi4j.ai.types.ToolCallContent;
import com.pi4j.ai.types.ToolResultMessage;
import com.pi4j.ai.types.Usage;
import com.pi4j.ai.types.UserMessage;
import com.pi4j.ai.util.JsonUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;

public class BedrockProvider implements ApiProvider {

    @Override
    public String getApi() {
        return "bedrock-converse-stream";
    }

    @Override
    public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
        AssistantMessageEventStream stream = new AssistantMessageEventStream();
        CompletableFuture.runAsync(() -> invokeStream(stream, model, context, options));
        return stream;
    }

    private void invokeStream(AssistantMessageEventStream stream, Model model, Context context, StreamOptions options) {
        AbortHandle abortHandle = options.getAbortHandle();
        ParseState state = new ParseState();

        try (BedrockRuntimeAsyncClient client = createClient(model)) {
            stream.push(new StartEvent());

            ConverseStreamRequest request = buildRequest(model, context, options);
            ConverseStreamResponseHandler handler = ConverseStreamResponseHandler.builder()
                    .subscriber(ConverseStreamResponseHandler.Visitor.builder()
                            .onMessageStart(event -> {
                            })
                            .onContentBlockStart(event -> handleContentBlockStart(event, stream, state, model))
                            .onContentBlockDelta(event -> handleContentBlockDelta(event, stream, state, model))
                            .onContentBlockStop(event -> handleContentBlockStop(event, stream, state, model))
                            .onMessageStop(event -> handleMessageStop(event, state))
                            .onMetadata(event -> handleMetadata(event, state))
                            .build())
                    .onError(throwable -> {
                        throw new RuntimeException(throwable);
                    })
                    .build();

            CompletableFuture<Void> callFuture = client.converseStream(request, handler);
            callFuture.get();

            finishAndComplete(stream, model, state);
        } catch (Exception ex) {
            AssistantMessage errorMessage = new AssistantMessage(
                    Collections.<ContentBlock>emptyList(),
                    getApi(),
                    model.getProvider(),
                    model.getId(),
                    state.usage,
                    abortHandle.isAborted() ? StopReason.ABORTED : StopReason.ERROR,
                    ex.getMessage());
            stream.push(new ErrorEvent(errorMessage.getStopReason(), errorMessage));
            stream.error(ex);
        }
    }

    BedrockRuntimeAsyncClient createClient(Model model) {
        String region = model.getHeaders().get("aws-region");
        if (region == null || region.trim().isEmpty()) {
            region = System.getenv("AWS_REGION");
        }
        if (region == null || region.trim().isEmpty()) {
            region = "us-east-1";
        }

        return BedrockRuntimeAsyncClient.builder()
                .region(Region.of(region))
                .build();
    }

    ConverseStreamRequest buildRequest(Model model, Context context, StreamOptions options) {
        List<software.amazon.awssdk.services.bedrockruntime.model.Message> messages =
                new ArrayList<software.amazon.awssdk.services.bedrockruntime.model.Message>();

        for (Message message : context.getMessages()) {
            if (message instanceof UserMessage) {
                messages.add(software.amazon.awssdk.services.bedrockruntime.model.Message.builder()
                        .role(ConversationRole.USER)
                        .content(toBedrockTextContent(((UserMessage) message).getContent()))
                        .build());
            } else if (message instanceof AssistantMessage) {
                messages.add(software.amazon.awssdk.services.bedrockruntime.model.Message.builder()
                        .role(ConversationRole.ASSISTANT)
                        .content(toBedrockTextContent(((AssistantMessage) message).getContent()))
                        .build());
            } else if (message instanceof ToolResultMessage) {
                ToolResultMessage toolResult = (ToolResultMessage) message;
                List<ContentBlock> textContent = Collections.<ContentBlock>singletonList(
                        new TextContent("tool:" + toolResult.getToolName() + "\n" + flattenText(toolResult.getContent())));
                messages.add(software.amazon.awssdk.services.bedrockruntime.model.Message.builder()
                        .role(ConversationRole.USER)
                        .content(toBedrockTextContent(textContent))
                        .build());
            }
        }

        ConverseStreamRequest.Builder builder = ConverseStreamRequest.builder()
                .modelId(model.getId())
                .messages(messages);

        InferenceConfiguration.Builder inference = InferenceConfiguration.builder();
        if (options.getMaxTokens() != null) {
            inference.maxTokens(options.getMaxTokens());
        } else if (model.getMaxTokens() > 0) {
            inference.maxTokens(model.getMaxTokens());
        }
        if (options.getTemperature() != null) {
            inference.temperature(options.getTemperature().floatValue());
        }
        builder.inferenceConfig(inference.build());

        return builder.build();
    }

    private List<software.amazon.awssdk.services.bedrockruntime.model.ContentBlock> toBedrockTextContent(List<ContentBlock> content) {
        List<software.amazon.awssdk.services.bedrockruntime.model.ContentBlock> result =
                new ArrayList<software.amazon.awssdk.services.bedrockruntime.model.ContentBlock>();
        String text = flattenText(content);
        if (!text.isEmpty()) {
            result.add(software.amazon.awssdk.services.bedrockruntime.model.ContentBlock.fromText(text));
        }
        return result;
    }

    private void handleContentBlockStart(
            ContentBlockStartEvent event,
            AssistantMessageEventStream stream,
            ParseState state,
            Model model) {
        int index = event.contentBlockIndex() == null ? 0 : event.contentBlockIndex();
        if (event.start() != null && event.start().toolUse() != null) {
            ToolBuilder tool = new ToolBuilder();
            tool.id = event.start().toolUse().toolUseId();
            tool.name = event.start().toolUse().name();
            tool.index = index;
            state.tools.put(index, tool);
            state.blockTypes.put(index, "tool");
            stream.push(new ToolCallStartEvent(index));
        }
    }

    private void handleContentBlockDelta(
            ContentBlockDeltaEvent event,
            AssistantMessageEventStream stream,
            ParseState state,
            Model model) {
        int index = event.contentBlockIndex() == null ? 0 : event.contentBlockIndex();
        ContentBlockDelta delta = event.delta();
        if (delta == null) {
            return;
        }

        if (delta.text() != null) {
            StringBuilder text = state.textBlocks.get(index);
            if (text == null) {
                text = new StringBuilder();
                state.textBlocks.put(index, text);
                state.blockTypes.put(index, "text");
                stream.push(new TextStartEvent(index));
            }
            text.append(delta.text());
            stream.push(new TextDeltaEvent(index, delta.text(), state.partial(getApi(), model)));
        }

        if (delta.reasoningContent() != null && delta.reasoningContent().text() != null) {
            StringBuilder thinking = state.thinkingBlocks.get(index);
            if (thinking == null) {
                thinking = new StringBuilder();
                state.thinkingBlocks.put(index, thinking);
                state.blockTypes.put(index, "thinking");
                stream.push(new ThinkingStartEvent(index));
            }
            thinking.append(delta.reasoningContent().text());
            stream.push(new ThinkingDeltaEvent(index, delta.reasoningContent().text(), state.partial(getApi(), model)));
        }

        if (delta.toolUse() != null && delta.toolUse().input() != null) {
            ToolBuilder tool = state.tools.get(index);
            if (tool == null) {
                tool = new ToolBuilder();
                tool.id = UUID.randomUUID().toString();
                tool.name = "tool";
                tool.index = index;
                state.tools.put(index, tool);
                state.blockTypes.put(index, "tool");
                stream.push(new ToolCallStartEvent(index));
            }
            tool.arguments.append(delta.toolUse().input());
            stream.push(new ToolCallDeltaEvent(index, delta.toolUse().input(), state.partial(getApi(), model)));
        }
    }

    private void handleContentBlockStop(
            ContentBlockStopEvent event,
            AssistantMessageEventStream stream,
            ParseState state,
            Model model) {
        int index = event.contentBlockIndex() == null ? 0 : event.contentBlockIndex();
        String blockType = state.blockTypes.get(index);

        if ("text".equals(blockType)) {
            StringBuilder text = state.textBlocks.get(index);
            if (text != null) {
                state.blocks.add(new TextContent(text.toString()));
                stream.push(new TextEndEvent(index));
            }
            return;
        }

        if ("thinking".equals(blockType)) {
            StringBuilder thinking = state.thinkingBlocks.get(index);
            if (thinking != null) {
                state.blocks.add(new ThinkingContent(thinking.toString()));
                stream.push(new ThinkingEndEvent(index));
            }
            return;
        }

        if ("tool".equals(blockType)) {
            ToolBuilder tool = state.tools.get(index);
            if (tool != null) {
                Map<String, Object> args = parseArgs(tool.arguments.toString());
                ToolCallContent toolCall = new ToolCallContent(
                        tool.id == null || tool.id.isEmpty() ? UUID.randomUUID().toString() : tool.id,
                        tool.name == null || tool.name.isEmpty() ? "tool" : tool.name,
                        args);
                state.blocks.add(toolCall);
                stream.push(new ToolCallEndEvent(index, toolCall, state.partial(getApi(), model)));
            }
        }
    }

    private void handleMessageStop(MessageStopEvent event, ParseState state) {
        if (event.stopReason() == null) {
            state.stopReason = StopReason.STOP;
            return;
        }
        String reason = event.stopReasonAsString();
        if ("MAX_TOKENS".equals(reason)) {
            state.stopReason = StopReason.LENGTH;
        } else if ("TOOL_USE".equals(reason)) {
            state.stopReason = StopReason.TOOL_USE;
        } else {
            state.stopReason = StopReason.STOP;
        }
    }

    private void handleMetadata(ConverseStreamMetadataEvent event, ParseState state) {
        if (event.usage() == null) {
            return;
        }
        int input = event.usage().inputTokens() == null ? 0 : event.usage().inputTokens();
        int output = event.usage().outputTokens() == null ? 0 : event.usage().outputTokens();
        int total = event.usage().totalTokens() == null ? 0 : event.usage().totalTokens();
        int cacheRead = event.usage().cacheReadInputTokens() == null ? 0 : event.usage().cacheReadInputTokens();
        int cacheWrite = event.usage().cacheWriteInputTokens() == null ? 0 : event.usage().cacheWriteInputTokens();
        state.usage = new Usage(input, output, cacheRead, cacheWrite, total, null);
    }

    private void finishAndComplete(AssistantMessageEventStream stream, Model model, ParseState state) {
        if (stream.isDone()) {
            return;
        }
        AssistantMessage finalMessage = state.finalMessage(getApi(), model);
        StopReason reason = state.stopReason == null ? StopReason.STOP : state.stopReason;
        stream.push(new DoneEvent(reason, finalMessage));
        stream.end(finalMessage);
    }

    private String flattenText(List<ContentBlock> content) {
        StringBuilder builder = new StringBuilder();
        for (ContentBlock block : content) {
            if (block instanceof TextContent) {
                builder.append(((TextContent) block).getText());
            } else if (block instanceof ThinkingContent) {
                builder.append(((ThinkingContent) block).getThinking());
            }
        }
        return builder.toString();
    }

    private Map<String, Object> parseArgs(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        JsonObject object = JsonUtil.gson().fromJson(rawJson, JsonObject.class);
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            map.put(entry.getKey(), JsonUtil.gson().fromJson(entry.getValue(), Object.class));
        }
        return map;
    }

    private static final class ParseState {
        private final List<ContentBlock> blocks = new ArrayList<ContentBlock>();
        private final Map<Integer, String> blockTypes = new LinkedHashMap<Integer, String>();
        private final Map<Integer, StringBuilder> textBlocks = new LinkedHashMap<Integer, StringBuilder>();
        private final Map<Integer, StringBuilder> thinkingBlocks = new LinkedHashMap<Integer, StringBuilder>();
        private final Map<Integer, ToolBuilder> tools = new LinkedHashMap<Integer, ToolBuilder>();
        private Usage usage;
        private StopReason stopReason;

        private AssistantMessage partial(String api, Model model) {
            return new AssistantMessage(new ArrayList<ContentBlock>(blocks), api, model.getProvider(), model.getId(), usage, stopReason, null);
        }

        private AssistantMessage finalMessage(String api, Model model) {
            return new AssistantMessage(new ArrayList<ContentBlock>(blocks), api, model.getProvider(), model.getId(), usage, stopReason, null);
        }
    }

    private static final class ToolBuilder {
        private String id;
        private String name;
        private int index;
        private StringBuilder arguments = new StringBuilder();
    }
}
