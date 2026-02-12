package com.pi4j.agent;

import com.pi4j.agent.event.AgentEndEvent;
import com.pi4j.agent.event.AgentEvent;
import com.pi4j.agent.event.AgentEventListener;
import com.pi4j.agent.event.AgentStartEvent;
import com.pi4j.agent.event.MessageEndEvent;
import com.pi4j.agent.event.MessageStartEvent;
import com.pi4j.agent.event.MessageUpdateEvent;
import com.pi4j.agent.event.ToolExecutionEndEvent;
import com.pi4j.agent.event.ToolExecutionStartEvent;
import com.pi4j.agent.event.ToolExecutionUpdateEvent;
import com.pi4j.agent.event.TurnEndEvent;
import com.pi4j.agent.event.TurnStartEvent;
import com.pi4j.agent.tool.AgentTool;
import com.pi4j.agent.tool.AgentToolResult;
import com.pi4j.ai.provider.AbortHandle;
import com.pi4j.ai.provider.ApiRegistry;
import com.pi4j.ai.provider.StreamOptions;
import com.pi4j.ai.stream.AssistantMessageEventStream;
import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.Context;
import com.pi4j.ai.types.Message;
import com.pi4j.ai.types.TextContent;
import com.pi4j.ai.types.Tool;
import com.pi4j.ai.types.ToolCallContent;
import com.pi4j.ai.types.ToolResultMessage;
import com.pi4j.ai.types.UserMessage;
import com.pi4j.ai.util.ToolValidationException;
import com.pi4j.ai.util.ToolValidator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Agent {
    private final Object lock = new Object();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CopyOnWriteArrayList<AgentEventListener> listeners = new CopyOnWriteArrayList<AgentEventListener>();
    private final Queue<AgentMessage> steeringQueue = new ConcurrentLinkedQueue<AgentMessage>();
    private final Queue<AgentMessage> followUpQueue = new ConcurrentLinkedQueue<AgentMessage>();

    private String systemPrompt;
    private com.pi4j.ai.types.Model model;
    private String thinkingLevel;
    private List<AgentTool> tools;
    private final List<AgentMessage> messages = new ArrayList<AgentMessage>();
    private final Set<String> pendingToolCalls = new LinkedHashSet<String>();

    private volatile boolean streaming;
    private volatile AgentMessage streamMessage;
    private volatile String error;
    private volatile CompletableFuture<Void> runningFuture;
    private volatile AbortHandle runningAbortHandle;
    private final AgentOptions options;

    public Agent(AgentOptions options) {
        this.options = options;
        this.systemPrompt = options.getSystemPrompt();
        this.model = options.getModel();
        this.thinkingLevel = options.getThinkingLevel();
        this.tools = new ArrayList<AgentTool>(options.getTools());
        this.messages.addAll(options.getInitialMessages());
    }

    public AgentState getState() {
        synchronized (lock) {
            return new AgentState(
                    systemPrompt,
                    model,
                    thinkingLevel,
                    tools,
                    messages,
                    streaming,
                    streamMessage,
                    pendingToolCalls,
                    error);
        }
    }

    public boolean isStreaming() {
        return streaming;
    }

    public void setSystemPrompt(String prompt) {
        synchronized (lock) {
            this.systemPrompt = prompt;
        }
    }

    public void setModel(com.pi4j.ai.types.Model model) {
        synchronized (lock) {
            this.model = model;
        }
    }

    public void setThinkingLevel(String level) {
        synchronized (lock) {
            this.thinkingLevel = level;
        }
    }

    public void setTools(List<AgentTool> tools) {
        synchronized (lock) {
            this.tools = new ArrayList<AgentTool>(tools);
        }
    }

    public void replaceMessages(List<AgentMessage> messages) {
        synchronized (lock) {
            this.messages.clear();
            this.messages.addAll(messages);
        }
    }

    public void appendMessage(AgentMessage message) {
        synchronized (lock) {
            this.messages.add(message);
        }
    }

    public void clearMessages() {
        synchronized (lock) {
            this.messages.clear();
        }
    }

    public CompletableFuture<Void> prompt(String text) {
        return prompt(text, Collections.emptyList());
    }

    public CompletableFuture<Void> prompt(String text, List<com.pi4j.ai.types.ImageContent> images) {
        List<ContentBlock> content = new ArrayList<ContentBlock>();
        content.add(new TextContent(text));
        content.addAll(images);
        return prompt(new LlmAgentMessage(new UserMessage(content)));
    }

    public CompletableFuture<Void> prompt(AgentMessage message) {
        return prompt(Collections.singletonList(message));
    }

    public CompletableFuture<Void> prompt(List<AgentMessage> newMessages) {
        synchronized (lock) {
            ensureNotStreaming();
            messages.addAll(newMessages);
            return startExecution();
        }
    }

    public CompletableFuture<Void> continueExecution() {
        synchronized (lock) {
            ensureNotStreaming();
            return startExecution();
        }
    }

    public void abort() {
        AbortHandle abortHandle = runningAbortHandle;
        if (abortHandle != null) {
            abortHandle.abort();
        }
    }

    public CompletableFuture<Void> waitForIdle() {
        CompletableFuture<Void> current = runningFuture;
        return current == null ? CompletableFuture.completedFuture(null) : current;
    }

    public void reset() {
        abort();
        synchronized (lock) {
            messages.clear();
            steeringQueue.clear();
            followUpQueue.clear();
            pendingToolCalls.clear();
            error = null;
            streamMessage = null;
        }
    }

    public void steer(AgentMessage message) {
        steeringQueue.offer(message);
        abort();
    }

    public void followUp(AgentMessage message) {
        followUpQueue.offer(message);
    }

    public Runnable subscribe(AgentEventListener listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private CompletableFuture<Void> startExecution() {
        streaming = true;
        error = null;
        AbortHandle abortHandle = new AbortHandle();
        runningAbortHandle = abortHandle;
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> executeLoop(abortHandle), executor);
        runningFuture = future.whenComplete((unused, throwable) -> {
            streaming = false;
            runningAbortHandle = null;
        });
        return runningFuture;
    }

    private void executeLoop(AbortHandle abortHandle) {
        fire(new AgentStartEvent());

        try {
            boolean keepRunning = true;
            while (keepRunning) {
                abortHandle.throwIfAborted();
                fire(new TurnStartEvent());

                List<AgentMessage> transformed = options.getTransformContext().transform(copyMessages(), abortHandle);
                List<Message> llmMessages = options.getConvertToLlm().convert(transformed);
                Context context = new Context(systemPrompt, llmMessages, toToolDefs());
                String apiKey = options.getGetApiKey().resolve(model.getProvider());

                StreamOptions streamOptions = StreamOptions.builder()
                        .apiKey(apiKey)
                        .reasoning(thinkingLevel)
                        .temperature(options.getTemperature())
                        .maxTokens(options.getMaxTokens())
                        .toolChoice(options.getToolChoice())
                        .abortHandle(abortHandle)
                        .build();

                AssistantMessageEventStream responseStream = ApiRegistry.stream(model, context, streamOptions);
                responseStream.subscribe(event -> {
                    AgentMessage current = streamMessage;
                    if (current != null) {
                        fire(new MessageUpdateEvent(current, event));
                    }
                });

                AssistantMessage assistant = waitStream(responseStream);
                LlmAgentMessage assistantMessage = new LlmAgentMessage(assistant);
                streamMessage = assistantMessage;
                fire(new MessageStartEvent(assistantMessage));
                appendMessageInternal(assistantMessage);
                fire(new MessageEndEvent(assistantMessage));

                List<ToolCallContent> toolCalls = extractToolCalls(assistant);
                List<ToolResultMessage> toolResults = new ArrayList<ToolResultMessage>();

                if (!toolCalls.isEmpty()) {
                    toolResults.addAll(executeToolCalls(toolCalls, abortHandle));
                    for (ToolResultMessage toolResult : toolResults) {
                        appendMessageInternal(new LlmAgentMessage(toolResult));
                    }
                }

                fire(new TurnEndEvent(assistant, toolResults));

                AgentMessage steering = steeringQueue.poll();
                if (steering != null) {
                    appendMessageInternal(steering);
                    continue;
                }

                if (toolCalls.isEmpty()) {
                    AgentMessage followUp = followUpQueue.poll();
                    if (followUp != null) {
                        appendMessageInternal(followUp);
                    } else {
                        keepRunning = false;
                    }
                }
            }

            fire(new AgentEndEvent(copyMessages()));
        } catch (Exception ex) {
            error = ex.getMessage();
            throw new RuntimeException(ex);
        } finally {
            streamMessage = null;
            pendingToolCalls.clear();
        }
    }

    private AssistantMessage waitStream(AssistantMessageEventStream responseStream)
            throws InterruptedException, ExecutionException {
        return responseStream.result().get();
    }

    private List<ToolResultMessage> executeToolCalls(List<ToolCallContent> toolCalls, AbortHandle abortHandle) {
        List<ToolResultMessage> results = new ArrayList<ToolResultMessage>();
        for (ToolCallContent toolCall : toolCalls) {
            pendingToolCalls.add(toolCall.getId());
            AgentTool tool = findTool(toolCall.getName());
            Map<String, Object> validated;
            try {
                validated = ToolValidator.validate(toToolDef(tool), toolCall);
            } catch (ToolValidationException validationException) {
                AgentToolResult errorResult = AgentToolResult.error(validationException.getMessage());
                ToolResultMessage toolResultMessage = new ToolResultMessage(
                        toolCall.getId(),
                        toolCall.getName(),
                        errorResult.getContent(),
                        errorResult.getDetails(),
                        true);
                results.add(toolResultMessage);
                fire(new ToolExecutionEndEvent(toolCall.getId(), toolCall.getName(), errorResult, true));
                pendingToolCalls.remove(toolCall.getId());
                continue;
            }

            fire(new ToolExecutionStartEvent(toolCall.getId(), toolCall.getName(), validated));

            try {
                AgentToolResult result = tool.execute(
                        toolCall.getId(),
                        validated,
                        abortHandle,
                        update -> fire(new ToolExecutionUpdateEvent(toolCall.getId(), toolCall.getName(), update)));
                ToolResultMessage toolResultMessage = new ToolResultMessage(
                        toolCall.getId(),
                        toolCall.getName(),
                        result.getContent(),
                        result.getDetails(),
                        false);
                results.add(toolResultMessage);
                fire(new ToolExecutionEndEvent(toolCall.getId(), toolCall.getName(), result, false));
            } catch (Exception ex) {
                AgentToolResult result = AgentToolResult.error(ex.getMessage());
                ToolResultMessage toolResultMessage = new ToolResultMessage(
                        toolCall.getId(),
                        toolCall.getName(),
                        result.getContent(),
                        result.getDetails(),
                        true);
                results.add(toolResultMessage);
                fire(new ToolExecutionEndEvent(toolCall.getId(), toolCall.getName(), result, true));
            }

            pendingToolCalls.remove(toolCall.getId());
        }
        return results;
    }

    private List<ToolCallContent> extractToolCalls(AssistantMessage assistant) {
        List<ToolCallContent> toolCalls = new ArrayList<ToolCallContent>();
        for (ContentBlock block : assistant.getContent()) {
            if (block instanceof ToolCallContent) {
                toolCalls.add((ToolCallContent) block);
            }
        }
        return toolCalls;
    }

    private List<Tool> toToolDefs() {
        List<Tool> defs = new ArrayList<Tool>();
        for (AgentTool tool : tools) {
            defs.add(toToolDef(tool));
        }
        return defs;
    }

    private Tool toToolDef(AgentTool tool) {
        return new Tool(tool.getName(), tool.getDescription(), tool.getParameters());
    }

    private AgentTool findTool(String name) {
        for (AgentTool tool : tools) {
            if (tool.getName().equals(name)) {
                return tool;
            }
        }
        throw new IllegalStateException("Tool not found: " + name);
    }

    private void fire(AgentEvent event) {
        for (AgentEventListener listener : listeners) {
            listener.onEvent(event);
        }
    }

    private List<AgentMessage> copyMessages() {
        synchronized (lock) {
            return new ArrayList<AgentMessage>(messages);
        }
    }

    private void appendMessageInternal(AgentMessage message) {
        synchronized (lock) {
            this.messages.add(message);
        }
    }

    private void ensureNotStreaming() {
        if (streaming) {
            throw new IllegalStateException("Agent is streaming");
        }
        if (model == null) {
            throw new IllegalStateException("Agent model is not configured");
        }
    }
}
