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

/**
 * {@code Agent} 是 Pi4J 框架的运行时核心，对应整体架构中的「Agent Loop」支柱。
 *
 * <p>它是一个<b>有状态</b>的对象，持有一段完整对话所需的全部内容：系统提示词、所用模型、
 * 可调用的工具集合，以及不断增长的消息历史。外部通过 {@link #prompt} 等方法把输入交给它，
 * 它便驱动「调用 LLM → 执行工具 → 把结果回灌再调用 LLM」的多轮循环，直到模型不再请求工具为止。
 *
 * <p><b>两种观察方式</b>（二者并存，各取所需）：
 * <ul>
 *   <li>{@link #subscribe}：订阅细粒度的事件流（回合开始/结束、消息流式增量、工具执行开始/结束……），
 *       适合驱动实时 UI 或日志。</li>
 *   <li>{@link #subscribeState}：订阅不可变的 {@link AgentState 状态快照}，每当状态变化便推送一份当前全貌，
 *       适合「整体重渲染」式的视图。</li>
 * </ul>
 *
 * <p><b>两条插话队列</b>（对应 README 的执行循环图，是本类最具特色的设计）：
 * <ul>
 *   <li>{@link #steer 转向 / steering}：在当前循环<b>执行途中</b>插入消息，会在工具执行的间隙被尽快消费，
 *       用于「打断并纠偏」。</li>
 *   <li>{@link #followUp 追问 / follow-up}：在当前循环<b>完整结束后</b>再追加消息并继续下一轮，
 *       用于「排队等当前任务做完再说」。</li>
 * </ul>
 *
 * <p><b>线程模型</b>：所有对话循环都被提交到一个<b>单线程</b> {@link #executor} 上串行执行，
 * 因此任意时刻至多只有一个循环在跑。可变的配置与消息历史由 {@link #lock} 保护；少量需要被
 * 「跑循环的线程」与「外部调用线程」同时读写的瞬态字段用 {@code volatile} 修饰；队列与监听器
 * 列表使用并发集合，无需额外加锁。
 */
public class Agent implements AutoCloseable {
    // ===== 并发与协作设施 =====
    /** 保护下方可变配置与消息历史的互斥锁；只用于短临界区，不与「跑循环」长时间互斥。 */
    private final Object lock = new Object();
    /** 单线程执行器：保证同一时刻只有一个 Agent 循环在运行，把循环逻辑串行化。 */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    /** 事件监听器列表；写时复制容器，便于「触发事件遍历」与「增删监听器」并发时安全。 */
    private final CopyOnWriteArrayList<AgentEventListener> listeners = new CopyOnWriteArrayList<AgentEventListener>();
    /** 状态快照监听器列表，语义同上。 */
    private final CopyOnWriteArrayList<Consumer<AgentState>> stateListeners = new CopyOnWriteArrayList<Consumer<AgentState>>();
    /** 转向队列：循环执行途中插入、会被尽快消费的消息。 */
    private final Queue<AgentMessage> steeringQueue = new ConcurrentLinkedQueue<AgentMessage>();
    /** 追问队列：循环结束后才取出、用于触发下一轮的消息。 */
    private final Queue<AgentMessage> followUpQueue = new ConcurrentLinkedQueue<AgentMessage>();

    // ===== 对话配置（运行期可通过 setXxx 修改） =====
    private String systemPrompt;
    private com.pi4j.ai.types.Model model;
    /** 思考档位（如 "off" / "basic" / "deep"），最终会被换算成模型可识别的推理强度。 */
    private String thinkingLevel;
    private List<AgentTool> tools;
    /** 完整消息历史（用户、助手、工具结果都在内），所有读写都经 {@link #lock} 保护。 */
    private final List<AgentMessage> messages = new ArrayList<AgentMessage>();
    /** 当前已发起、尚未返回结果的工具调用 ID 集合，供 UI 展示「执行中」状态。 */
    private final Set<String> pendingToolCalls = new LinkedHashSet<String>();

    // ===== 运行时瞬态（会被多线程读取，故用 volatile 保证可见性） =====
    /** 是否有循环正在运行（流式中）。 */
    private volatile boolean streaming;
    /** 当前正在流式生成的助手消息：开始时是空占位符，流式结束后被替换为完整消息。 */
    private volatile AgentMessage streamMessage;
    /** 最近一次循环的错误信息；正常时为 {@code null}。 */
    private volatile String error;
    /** 当前循环的完成凭据，{@link #waitForIdle} 据此等待空闲。 */
    private volatile CompletableFuture<Void> runningFuture;
    /** 当前循环的中止句柄，{@link #abort} 通过它打断正在进行的循环。 */
    private volatile AbortHandle runningAbortHandle;

    // ===== 不可变协作者 =====
    /** 构造时传入的全部配置与可插拔策略（消息转换器、上下文变换器、密钥解析器、队列模式等）。 */
    private final AgentOptions options;
    /** 工具执行器，封装了工具分发模式（串行/并行/自定义）与中间件管线。 */
    private final ToolExecutor toolExecutor;

    /** 从 {@link AgentOptions} 拷出初始配置与初始消息，组装出一个就绪但尚未运行的 Agent。 */
    public Agent(AgentOptions options) {
        this.options = options;
        this.systemPrompt = options.getSystemPrompt();
        this.model = options.getModel();
        this.thinkingLevel = options.getThinkingLevel();
        this.tools = new ArrayList<AgentTool>(options.getTools());
        this.messages.addAll(options.getInitialMessages());
        this.toolExecutor = new ToolExecutor(options.getToolDispatcher(), options.getToolMiddlewares());
    }

    /** 在锁内拍一张不可变的状态快照；返回值与内部状态完全解耦，可安全地传给任意线程。 */
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

    // ----- 配置变更：均为「改字段 + 广播状态」的固定套路，便于订阅方实时跟随 -----

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

    /** 整体替换消息历史（如从持久化会话恢复对话时使用）。 */
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

    // ----- 发起对话：以下几个 prompt 重载层层收敛，最终都汇聚到 prompt(List) -----

    /** 便捷入口：仅发送纯文本。 */
    public CompletableFuture<Void> prompt(String text) {
        return prompt(text, Collections.emptyList());
    }

    /** 便捷入口：发送文本 + 若干图片，组装成一条多模态用户消息。 */
    public CompletableFuture<Void> prompt(String text, List<com.pi4j.ai.types.ImageContent> images) {
        List<ContentBlock> content = new ArrayList<ContentBlock>();
        content.add(new TextContent(text));
        content.addAll(images);
        return prompt(new LlmAgentMessage(new UserMessage(content)));
    }

    public CompletableFuture<Void> prompt(AgentMessage message) {
        return prompt(Collections.singletonList(message));
    }

    /**
     * 核心入口：把新消息追加进历史并启动一次循环。
     *
     * <p>{@link #ensureNotStreaming} 保证不会在已有循环运行时重复启动——若需在运行中插话，
     * 应改用 {@link #steer} 或 {@link #followUp}。
     */
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

    /** 不追加新消息，直接基于当前历史再跑一轮（例如外部已手动改过 messages 后续跑）。 */
    public CompletableFuture<Void> continueExecution() {
        CompletableFuture<Void> future;
        synchronized (lock) {
            ensureNotStreaming();
            future = startExecution();
        }
        fireState();
        return future;
    }

    /** 请求中止当前循环；若此刻无循环在跑则为空操作。实际打断点在循环内的 {@code throwIfAborted}。 */
    public void abort() {
        AbortHandle abortHandle = runningAbortHandle;
        if (abortHandle != null) {
            abortHandle.abort();
        }
    }

    /** 返回一个在「当前循环结束」时完成的 Future；若当前空闲则返回已完成的 Future。 */
    public CompletableFuture<Void> waitForIdle() {
        CompletableFuture<Void> current = runningFuture;
        return current == null ? CompletableFuture.completedFuture(null) : current;
    }

    /** 关闭 Agent：请求中止当前循环并关闭内部执行线程。关闭后不应再发起新的 prompt。 */
    @Override
    public void close() {
        abort();
        executor.shutdown();
    }

    /** 彻底复位：中止循环并清空历史、两条队列、执行中工具与错误状态，回到初始干净态。 */
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

    // ----- 两条插话队列的入队/清空操作（消费逻辑见 executeLoop / executeToolCalls） -----

    /** 转向入队：在当前循环执行途中尽快插入一条消息（打断并纠偏）。 */
    public void steer(AgentMessage message) {
        steeringQueue.offer(message);
    }

    /** 追问入队：在当前循环完整结束后再追加一条消息并触发下一轮。 */
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

    /** 订阅事件流；返回一个「退订」回调，调用即移除该监听器。 */
    public Runnable subscribe(AgentEventListener listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /** 订阅状态快照；订阅时立即推送一次当前状态，返回值同样是「退订」回调。 */
    public Runnable subscribeState(Consumer<AgentState> listener) {
        stateListeners.add(listener);
        listener.accept(getState());
        return () -> stateListeners.remove(listener);
    }

    /**
     * 启动一次循环：在单线程 executor 上异步执行 {@link #executeLoop}。
     *
     * <p>调用前必须已持有 {@link #lock}（由 {@link #prompt}/{@link #continueExecution} 保证），
     * 这样「置 streaming、建 AbortHandle」与外部状态读取之间不会竞争。{@code whenComplete}
     * 负责在循环结束（无论成功、异常还是中止）后统一收尾：复位 streaming 与中止句柄并广播状态。
     */
    private CompletableFuture<Void> startExecution() {
        streaming = true;
        error = null;
        AbortHandle abortHandle = new AbortHandle();
        runningAbortHandle = abortHandle;
        fireState();
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> executeLoop(abortHandle), executor);
        runningFuture = future.whenComplete((unused, throwable) -> {
            streaming = false;
            runningAbortHandle = null;
            fireState();
        });
        return runningFuture;
    }

    /**
     * Agent 的真正心脏：驱动「多轮对话 + 工具调用」的双层循环。
     *
     * <p><b>外层 {@code while(true)}</b>：每跑完一整轮对话，就去看追问队列——非空则带着追问消息
     * {@code continue} 开启下一轮，为空则 {@code break} 收工。
     *
     * <p><b>内层 {@code while(hasMoreToolCalls || !pendingMessages.isEmpty())}</b>：一个「回合(turn)」
     * 就是一次与 LLM 的完整往返。只要上一回合还请求了工具（需要把工具结果回灌让模型继续），
     * 或还有待注入的消息（来自转向队列），就继续下一回合。
     *
     * <p>每个回合内的固定流程：注入待处理消息 → 经可插拔的上下文变换/消息转换组装 {@link Context}
     * → 流式调用 LLM → 落地助手消息 → 提取并执行工具调用 → 检查转向队列。
     *
     * <p>整段被 try/catch 包裹：无论正常结束、出错还是被中止，都会发出对应事件并在 finally 中清理瞬态。
     */
    private void executeLoop(AbortHandle abortHandle) {
        fire(new AgentStartEvent());

        try {
            // 循环开始前先吸纳一批已排队的转向消息，作为本轮的起始输入。
            List<AgentMessage> pendingMessages = dequeueSteeringMessages();

            while (true) {
                boolean hasMoreToolCalls = true;

                while (hasMoreToolCalls || !pendingMessages.isEmpty()) {
                    // 每个回合开头检查中止信号，使 abort() 能在回合边界尽快生效。
                    abortHandle.throwIfAborted();

                    // 把待处理消息（用户输入 / 转向消息）落入历史，随后清空待处理列表。
                    if (!pendingMessages.isEmpty()) {
                        for (AgentMessage message : pendingMessages) {
                            appendMessageInternal(message);
                        }
                        pendingMessages = Collections.emptyList();
                    }

                    // 组装发送给 LLM 的上下文：先经上下文变换器（可裁剪/压缩历史），再转换成 LLM 原生消息格式。
                    List<AgentMessage> transformed = options.getTransformContext().transform(copyMessages(), abortHandle);
                    List<Message> llmMessages = options.getConvertToLlm().convert(transformed);
                    Context context = new Context(systemPrompt, llmMessages, toToolDefs());
                    fire(new TurnStartEvent(context));
                    String apiKey = options.getGetApiKey().resolve(model.getProvider());

                    // 汇集本回合的全部流式参数（密钥、推理强度、采样温度、上限、缓存策略、中止句柄等）。
                    StreamOptions streamOptions = StreamOptions.builder()
                            .apiKey(apiKey)
                            .reasoning(thinkingLevel)
                            .thinkingBudget(options.getThinkingBudget())
                            .thinkingEffort(resolveThinkingEffort())
                            .temperature(options.getTemperature())
                            .maxTokens(options.getMaxTokens())
                            .responseFormat(options.getResponseFormat())
                            .toolChoice(options.getToolChoice())
                            .cacheRetention(options.getCacheRetention())
                            .sessionId(options.getSessionId())
                            .abortHandle(abortHandle)
                            .build();

                    // 先放一个空的占位助手消息，让 UI 立刻有「正在生成」的反馈；流式增量都挂在它身上。
                    AgentMessage currentTurnStreamMessage = createStreamingMessagePlaceholder();
                    streamMessage = currentTurnStreamMessage;
                    fireState();
                    AssistantMessageEventStream responseStream = ApiRegistry.stream(model, context, streamOptions);
                    // 把底层流的每个增量事件包装成 MessageUpdateEvent 转发给订阅者，实现逐字流式输出。
                    responseStream.subscribe(event -> fire(new MessageUpdateEvent(currentTurnStreamMessage, event)));

                    // 阻塞等待本回合的完整助手消息，再用「成品」替换掉上面的占位符。
                    AssistantMessage assistant = waitStream(responseStream);
                    LlmAgentMessage assistantMessage = new LlmAgentMessage(assistant);
                    streamMessage = assistantMessage;
                    fireState();
                    fire(new MessageStartEvent(assistantMessage));
                    appendMessageInternal(assistantMessage);
                    fire(new MessageEndEvent(assistantMessage));

                    // 从助手消息里挑出工具调用块；有则本回合需执行工具，并据此决定是否还要再来一回合。
                    List<ToolCallContent> toolCalls = extractToolCalls(assistant);
                    hasMoreToolCalls = !toolCalls.isEmpty();
                    List<ToolResultMessage> toolResults = new ArrayList<ToolResultMessage>();
                    List<AgentMessage> steeringAfterTools = Collections.emptyList();

                    if (hasMoreToolCalls) {
                        // 执行全部工具调用，并把每条结果作为一条消息回灌历史，供下一回合喂给模型。
                        ToolExecutionResult toolExecutionResult = executeToolCalls(toolCalls, abortHandle);
                        toolResults.addAll(toolExecutionResult.toolResults);
                        steeringAfterTools = toolExecutionResult.steeringMessages;
                        for (ToolResultMessage toolResult : toolResults) {
                            appendMessageInternal(new LlmAgentMessage(toolResult));
                        }
                    }

                    fire(new TurnEndEvent(assistant, toolResults));

                    // 决定下一回合的起始输入：工具执行途中若有转向消息则优先消费，否则再瞧一眼转向队列。
                    if (!steeringAfterTools.isEmpty()) {
                        pendingMessages = steeringAfterTools;
                    } else {
                        pendingMessages = dequeueSteeringMessages();
                    }
                }

                // 一整轮对话结束：若追问队列里有消息，带着它们继续下一轮；否则整段循环到此为止。
                List<AgentMessage> followUps = dequeueFollowUpMessages();
                if (!followUps.isEmpty()) {
                    pendingMessages = followUps;
                    continue;
                }
                break;
            }

            fire(new AgentEndEvent(copyMessages()));
        } catch (Exception ex) {
            // 把异常（含中止）转化为一条「失败的助手消息」，让对话历史与事件流保持完整、可观测。
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
            // 无论结局如何都清理瞬态：流式占位清空、执行中工具集合清空，并广播最终状态。
            streamMessage = null;
            pendingToolCalls.clear();
            fireState();
        }
    }

    /** 阻塞等待流式结果。底层 EventStream 的结果以 Future 暴露，这里同步取出助手消息。 */
    private AssistantMessage waitStream(AssistantMessageEventStream responseStream)
            throws InterruptedException, ExecutionException {
        return responseStream.result().get();
    }

    /** 构造一条「空壳」助手消息作为流式占位：内容为空、用量与停止原因均未知，待流式完成后整体替换。 */
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

    /**
     * 顺序执行一批工具调用，并返回「全部工具结果 + 途中收到的转向消息」。
     *
     * <p>每个工具调用都遵循：登记为执行中 → 参数校验 → 执行 → 产出结果 → 注销执行中。
     * 校验失败或执行抛错都会被转化成一条「错误工具结果」，而非中断整个循环——因为 LLM 协议要求
     * <b>每一个工具调用都必须有对应的工具结果</b>，缺一不可。
     *
     * <p><b>转向打断</b>：每执行完一个工具就检查一次转向队列。一旦有转向消息，便不再执行后续工具，
     * 转而把剩余的工具调用统统标记为「已跳过」（同样产出占位结果以满足上述协议），然后带着转向消息
     * 提前返回，交由 {@link #executeLoop} 在下一回合优先处理。
     */
    private ToolExecutionResult executeToolCalls(List<ToolCallContent> toolCalls, AbortHandle abortHandle) {
        List<ToolResultMessage> results = new ArrayList<ToolResultMessage>();
        List<AgentMessage> steeringMessages = Collections.emptyList();
        for (int index = 0; index < toolCalls.size(); index++) {
            ToolCallContent toolCall = toolCalls.get(index);
            pendingToolCalls.add(toolCall.getId());
            fireState();
            AgentTool tool = findTool(toolCall.getName());
            if (tool == null) {
                AgentToolResult errorResult = AgentToolResult.error("Tool not found: " + toolCall.getName());
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
            Map<String, Object> validated;
            try {
                // 依据工具的 JSON Schema 校验并规整参数；不合法则直接产出错误结果并跳到下一个。
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
                // 组装执行上下文（含中止句柄与「进度回调」），交给 toolExecutor 走分发与中间件管线。
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
                // 执行期异常同样收敛为一条错误结果，保证「调用—结果」成对，不破坏后续与模型的交互。
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

            // 本工具执行完毕，检查是否有用户转向插入；若有则跳过剩余工具并提前结束。
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

    /** 为被转向打断而未执行的工具调用补一条「已跳过」结果，并照常发出开始/结束事件以保持事件流完整。 */
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

    /**
     * 从队列取消息，取多少取决于模式：
     * {@code "one-at-a-time"} 一次只取一条（细粒度交替），其余模式则一次排空整个队列。
     */
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

    /**
     * 换算最终传给模型的「推理强度」：
     * 优先用显式配置的 thinkingEffort；否则回退到 thinkingLevel——而 "off"（或未设）表示关闭思考返回 null。
     */
    private String resolveThinkingEffort() {
        if (options.getThinkingEffort() != null && !options.getThinkingEffort().trim().isEmpty()) {
            return options.getThinkingEffort();
        }
        if (thinkingLevel == null || "off".equals(thinkingLevel)) {
            return null;
        }
        return thinkingLevel;
    }

    /** 从助手消息的内容块中筛出所有工具调用块。 */
    private List<ToolCallContent> extractToolCalls(AssistantMessage assistant) {
        List<ToolCallContent> toolCalls = new ArrayList<ToolCallContent>();
        for (ContentBlock block : assistant.getContent()) {
            if (block instanceof ToolCallContent) {
                toolCalls.add((ToolCallContent) block);
            }
        }
        return toolCalls;
    }

    /** 把当前工具集合转换成发给 LLM 的工具定义列表（名称、描述、参数 Schema）。 */
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

    /** 按名字查找工具；找不到返回 {@code null}（模型可能幻觉出未注册的工具名，由调用方转化为错误工具结果）。 */
    private AgentTool findTool(String name) {
        for (AgentTool tool : tools) {
            if (tool.getName().equals(name)) {
                return tool;
            }
        }
        return null;
    }

    /** 向所有事件监听器广播一个事件。 */
    private void fire(AgentEvent event) {
        for (AgentEventListener listener : listeners) {
            listener.onEvent(event);
        }
    }

    /** 在锁内对消息历史做防御性拷贝，得到一份不受后续改动影响的快照。 */
    private List<AgentMessage> copyMessages() {
        synchronized (lock) {
            return new ArrayList<AgentMessage>(messages);
        }
    }

    /** 追加一条消息到历史并广播状态——这是循环内落地各类消息的统一出口。 */
    private void appendMessageInternal(AgentMessage message) {
        synchronized (lock) {
            this.messages.add(message);
        }
        fireState();
    }

    /** 拍一张状态快照并推送给所有状态监听器。 */
    private void fireState() {
        AgentState snapshot = getState();
        for (Consumer<AgentState> listener : stateListeners) {
            listener.accept(snapshot);
        }
    }

    /** 启动前置校验：已在流式中则禁止重复启动，模型未配置则无法运行。 */
    private void ensureNotStreaming() {
        if (streaming) {
            throw new IllegalStateException("Agent is streaming");
        }
        if (model == null) {
            throw new IllegalStateException("Agent model is not configured");
        }
    }

    /** {@link #executeToolCalls} 的返回载体：打包工具结果与「途中截获的转向消息」两份数据。 */
    private static final class ToolExecutionResult {
        private final List<ToolResultMessage> toolResults;
        private final List<AgentMessage> steeringMessages;

        private ToolExecutionResult(List<ToolResultMessage> toolResults, List<AgentMessage> steeringMessages) {
            this.toolResults = toolResults;
            this.steeringMessages = steeringMessages;
        }
    }
}
