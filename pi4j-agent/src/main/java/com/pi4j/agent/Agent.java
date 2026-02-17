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
import com.pi4j.agent.tool.ToolExecutionContext;
import com.pi4j.agent.tool.ToolExecutor;
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
import com.pi4j.ai.types.Usage;
import com.pi4j.ai.types.StopReason;
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
import java.util.function.Consumer;

public class Agent {
    private final Object lock = new Object();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CopyOnWriteArrayList<AgentEventListener> listeners = new CopyOnWriteArrayList<AgentEventListener>();
    private final CopyOnWriteArrayList<Consumer<AgentState>> stateListeners = new CopyOnWriteArrayList<Consumer<AgentState>>();
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
    private final ToolExecutor toolExecutor;

    public Agent(AgentOptions options) {
        this.options = options;
        this.systemPrompt = options.getSystemPrompt();
        this.model = options.getModel();
        this.thinkingLevel = options.getThinkingLevel();
        this.tools = new ArrayList<AgentTool>(options.getTools());
        this.messages.addAll(options.getInitialMessages());
        this.toolExecutor = new ToolExecutor(options.getToolDispatcher(), options.getToolMiddlewares());
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
        fireState();
    }

    public void setModel(com.pi4j.ai.types.Model model) {
        synchronized (lock) {
            this.model = model;
        }
        fireState();
    }

    public void setThinkingLevel(String level) {
        synchronized (lock) {
            this.thinkingLevel = level;
        }
        fireState();
    }

    public void setTools(List<AgentTool> tools) {
        synchronized (lock) {
            this.tools = new ArrayList<AgentTool>(tools);
        }
        fireState();
    }

    public void replaceMessages(List<AgentMessage> messages) {
        synchronized (lock) {
            this.messages.clear();
            this.messages.addAll(messages);
        }
        fireState();
    }

    public void appendMessage(AgentMessage message) {
        appendMessageInternal(message);
    }

    public void clearMessages() {
        synchronized (lock) {
            this.messages.clear();
        }
        fireState();
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
        CompletableFuture<Void> future;
        synchronized (lock) {
            ensureNotStreaming();
            messages.addAll(newMessages);
            future = startExecution();
        }
        fireState();
        return future;
    }

    public CompletableFuture<Void> continueExecution() {
        CompletableFuture<Void> future;
        synchronized (lock) {
            ensureNotStreaming();
            future = startExecution();
        }
        fireState();
        return future;
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
        fireState();
    }

    public void steer(AgentMessage message) {
        steeringQueue.offer(message);
    }

    public void followUp(AgentMessage message) {
        followUpQueue.offer(message);
    }

    public void clearSteeringQueue() {
        steeringQueue.clear();
    }

    public void clearFollowUpQueue() {
        followUpQueue.clear();
    }

    public boolean hasQueuedMessages() {
        return !steeringQueue.isEmpty() || !followUpQueue.isEmpty();
    }

    public Runnable subscribe(AgentEventListener listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public Runnable subscribeState(Consumer<AgentState> listener) {
        stateListeners.add(listener);
        listener.accept(getState());
        return () -> stateListeners.remove(listener);
    }

    private CompletableFuture<Void> startExecution() {
        streaming = true;
        error = null;
        AbortHandle abortHandle = new AbortHandle();
        runningAbortHandle = abortHandle;
        fireState();
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                AgentLoop.run(Collections.<AgentMessage>emptyList(), this, abortHandle).result().get();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(interrupted);
            } catch (ExecutionException executionException) {
                Throwable cause = executionException.getCause();
                throw new RuntimeException(cause == null ? executionException : cause);
            }
        }, executor);
        runningFuture = future.whenComplete((unused, throwable) -> {
            streaming = false;
            runningAbortHandle = null;
            fireState();
        });
        return runningFuture;
    }

    void executeLoopFromLoop(AbortHandle abortHandle) {
        executeLoop(abortHandle);
    }

    List<AgentMessage> snapshotMessagesFromLoop() {
        return copyMessages();
    }

    void appendMessageFromLoop(AgentMessage message) {
        appendMessageInternal(message);
    }

    private void executeLoop(AbortHandle abortHandle) {
        fire(new AgentStartEvent());

        try {
            List<AgentMessage> pendingMessages = dequeueSteeringMessages();

            while (true) {
                boolean hasMoreToolCalls = true;

                while (hasMoreToolCalls || !pendingMessages.isEmpty()) {
                    abortHandle.throwIfAborted();

                    if (!pendingMessages.isEmpty()) {
                        for (AgentMessage message : pendingMessages) {
                            appendMessageInternal(message);
                        }
                        pendingMessages = Collections.emptyList();
                    }

                    List<AgentMessage> transformed = options.getTransformContext().transform(copyMessages(), abortHandle);
                    List<Message> llmMessages = options.getConvertToLlm().convert(transformed);
                    Context context = new Context(systemPrompt, llmMessages, toToolDefs());
                    fire(new TurnStartEvent(context));
                    String apiKey = options.getGetApiKey().resolve(model.getProvider());

                    StreamOptions streamOptions = StreamOptions.builder()
                            .apiKey(apiKey)
                            .reasoning(thinkingLevel)
                            .thinkingBudget(options.getThinkingBudget())
                            .thinkingEffort(resolveThinkingEffort())
                            .temperature(options.getTemperature())
                            .maxTokens(options.getMaxTokens())
                            .toolChoice(options.getToolChoice())
                            .cacheRetention(options.getCacheRetention())
                            .sessionId(options.getSessionId())
                            .abortHandle(abortHandle)
                            .build();

                    AgentMessage currentTurnStreamMessage = createStreamingMessagePlaceholder();
                    streamMessage = currentTurnStreamMessage;
                    fireState();
                    AssistantMessageEventStream responseStream = ApiRegistry.stream(model, context, streamOptions);
                    responseStream.subscribe(event -> fire(new MessageUpdateEvent(currentTurnStreamMessage, event)));

                    AssistantMessage assistant = waitStream(responseStream);
                    LlmAgentMessage assistantMessage = new LlmAgentMessage(assistant);
                    streamMessage = assistantMessage;
                    fireState();
                    fire(new MessageStartEvent(assistantMessage));
                    appendMessageInternal(assistantMessage);
                    fire(new MessageEndEvent(assistantMessage));

                    List<ToolCallContent> toolCalls = extractToolCalls(assistant);
                    hasMoreToolCalls = !toolCalls.isEmpty();
                    List<ToolResultMessage> toolResults = new ArrayList<ToolResultMessage>();
                    List<AgentMessage> steeringAfterTools = Collections.emptyList();

                    if (hasMoreToolCalls) {
                        ToolExecutionResult toolExecutionResult = executeToolCalls(toolCalls, abortHandle);
                        toolResults.addAll(toolExecutionResult.toolResults);
                        steeringAfterTools = toolExecutionResult.steeringMessages;
                        for (ToolResultMessage toolResult : toolResults) {
                            appendMessageInternal(new LlmAgentMessage(toolResult));
                        }
                    }

                    fire(new TurnEndEvent(assistant, toolResults));

                    if (!steeringAfterTools.isEmpty()) {
                        pendingMessages = steeringAfterTools;
                    } else {
                        pendingMessages = dequeueSteeringMessages();
                    }
                }

                List<AgentMessage> followUps = dequeueFollowUpMessages();
                if (!followUps.isEmpty()) {
                    pendingMessages = followUps;
                    continue;
                }
                break;
            }

            fire(new AgentEndEvent(copyMessages()));
        } catch (Exception ex) {
            StopReason stopReason = abortHandle.isAborted() ? StopReason.ABORTED : StopReason.ERROR;
            String errorMessage = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            AssistantMessage failure = new AssistantMessage(
                    Collections.<ContentBlock>singletonList(new TextContent("")),
                    model.getApi(),
                    model.getProvider(),
                    model.getId(),
                    new Usage(0, 0, 0, 0, 0, null),
                    stopReason,
                    errorMessage);
            LlmAgentMessage failureMessage = new LlmAgentMessage(failure);
            fire(new MessageStartEvent(failureMessage));
            appendMessageInternal(failureMessage);
            fire(new MessageEndEvent(failureMessage));
            fire(new TurnEndEvent(failure, Collections.<ToolResultMessage>emptyList()));
            fire(new AgentEndEvent(copyMessages()));

            error = errorMessage;
            fireState();
        } finally {
            streamMessage = null;
            pendingToolCalls.clear();
            fireState();
        }
    }

    private AssistantMessage waitStream(AssistantMessageEventStream responseStream)
            throws InterruptedException, ExecutionException {
        return responseStream.result().get();
    }

    private AgentMessage createStreamingMessagePlaceholder() {
        AssistantMessage placeholder = new AssistantMessage(
                Collections.<ContentBlock>emptyList(),
                model.getApi(),
                model.getProvider(),
                model.getId(),
                null,
                null,
                null);
        return new LlmAgentMessage(placeholder);
    }

    private ToolExecutionResult executeToolCalls(List<ToolCallContent> toolCalls, AbortHandle abortHandle) {
        List<ToolResultMessage> results = new ArrayList<ToolResultMessage>();
        List<AgentMessage> steeringMessages = Collections.emptyList();
        for (int index = 0; index < toolCalls.size(); index++) {
            ToolCallContent toolCall = toolCalls.get(index);
            pendingToolCalls.add(toolCall.getId());
            fireState();
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
                fireState();
                continue;
            }

            fire(new ToolExecutionStartEvent(toolCall.getId(), toolCall.getName(), validated));

            try {
                ToolExecutionContext context = new ToolExecutionContext(
                        toolCall.getId(),
                        toolCall.getName(),
                        tool,
                        validated,
                        abortHandle,
                        update -> fire(new ToolExecutionUpdateEvent(toolCall.getId(), toolCall.getName(), update)));
                AgentToolResult result = toolExecutor.execute(context);
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
            fireState();

            List<AgentMessage> steering = dequeueSteeringMessages();
            if (!steering.isEmpty()) {
                steeringMessages = steering;
                for (int skipped = index + 1; skipped < toolCalls.size(); skipped++) {
                    ToolCallContent skippedCall = toolCalls.get(skipped);
                    ToolResultMessage skippedResult = skipToolCall(skippedCall);
                    results.add(skippedResult);
                }
                break;
            }
        }
        return new ToolExecutionResult(results, steeringMessages);
    }

    private ToolResultMessage skipToolCall(ToolCallContent toolCall) {
        AgentToolResult skippedResult = AgentToolResult.error("Skipped due to queued user message.");
        fire(new ToolExecutionStartEvent(toolCall.getId(), toolCall.getName(), toolCall.getArguments()));
        fire(new ToolExecutionEndEvent(toolCall.getId(), toolCall.getName(), skippedResult, true));
        return new ToolResultMessage(
                toolCall.getId(),
                toolCall.getName(),
                skippedResult.getContent(),
                skippedResult.getDetails(),
                true);
    }

    private List<AgentMessage> dequeueSteeringMessages() {
        return dequeueMessages(steeringQueue, options.getSteeringMode());
    }

    private List<AgentMessage> dequeueFollowUpMessages() {
        return dequeueMessages(followUpQueue, options.getFollowUpMode());
    }

    private List<AgentMessage> dequeueMessages(Queue<AgentMessage> queue, String mode) {
        AgentMessage first = queue.poll();
        if (first == null) {
            return Collections.emptyList();
        }
        if ("one-at-a-time".equals(mode)) {
            return Collections.singletonList(first);
        }
        List<AgentMessage> all = new ArrayList<AgentMessage>();
        all.add(first);
        AgentMessage next;
        while ((next = queue.poll()) != null) {
            all.add(next);
        }
        return all;
    }

    private String resolveThinkingEffort() {
        if (options.getThinkingEffort() != null && !options.getThinkingEffort().trim().isEmpty()) {
            return options.getThinkingEffort();
        }
        if (thinkingLevel == null || "off".equals(thinkingLevel)) {
            return null;
        }
        return thinkingLevel;
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
        fireState();
    }

    private void fireState() {
        AgentState snapshot = getState();
        for (Consumer<AgentState> listener : stateListeners) {
            listener.accept(snapshot);
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

    private static final class ToolExecutionResult {
        private final List<ToolResultMessage> toolResults;
        private final List<AgentMessage> steeringMessages;

        private ToolExecutionResult(List<ToolResultMessage> toolResults, List<AgentMessage> steeringMessages) {
            this.toolResults = toolResults;
            this.steeringMessages = steeringMessages;
        }
    }
}
