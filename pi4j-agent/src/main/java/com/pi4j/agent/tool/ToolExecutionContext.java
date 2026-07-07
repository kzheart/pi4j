package com.pi4j.agent.tool;

import com.pi4j.ai.provider.AbortHandle;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolExecutionContext {
    private final String toolCallId;
    private final String toolName;
    private final AgentTool tool;
    private final Map<String, Object> params;
    private final AbortHandle abortHandle;
    private final ToolUpdateCallback onUpdate;
    private final ToolDispatchMode dispatchMode;
    private final boolean confirmationRequired;
    private final long timeoutMillis;
    private final int maxRetries;
    private final Map<String, Object> attributes;

    public ToolExecutionContext(
            String toolCallId,
            String toolName,
            AgentTool tool,
            Map<String, Object> params,
            AbortHandle abortHandle,
            ToolUpdateCallback onUpdate) {
        this(
                toolCallId,
                toolName,
                tool,
                params,
                abortHandle,
                onUpdate,
                ToolDispatchMode.DIRECT,
                false,
                0L,
                0,
                Collections.<String, Object>emptyMap());
    }

    private ToolExecutionContext(
            String toolCallId,
            String toolName,
            AgentTool tool,
            Map<String, Object> params,
            AbortHandle abortHandle,
            ToolUpdateCallback onUpdate,
            ToolDispatchMode dispatchMode,
            boolean confirmationRequired,
            long timeoutMillis,
            int maxRetries,
            Map<String, Object> attributes) {
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.tool = tool;
        this.params = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(params));
        this.abortHandle = abortHandle;
        this.onUpdate = onUpdate;
        this.dispatchMode = dispatchMode;
        this.confirmationRequired = confirmationRequired;
        this.timeoutMillis = timeoutMillis;
        this.maxRetries = maxRetries;
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(attributes));
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public AgentTool getTool() {
        return tool;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public AbortHandle getAbortHandle() {
        return abortHandle;
    }

    public ToolUpdateCallback getOnUpdate() {
        return onUpdate;
    }

    public ToolDispatchMode getDispatchMode() {
        return dispatchMode;
    }

    public boolean isConfirmationRequired() {
        return confirmationRequired;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(AttributeKey<T> key) {
        return (T) attributes.get(key.getName());
    }

    public ToolExecutionContext withDispatchMode(ToolDispatchMode value) {
        return new ToolExecutionContext(
                toolCallId,
                toolName,
                tool,
                params,
                abortHandle,
                onUpdate,
                value,
                confirmationRequired,
                timeoutMillis,
                maxRetries,
                attributes);
    }

    public ToolExecutionContext requireConfirmation(boolean value) {
        return new ToolExecutionContext(
                toolCallId,
                toolName,
                tool,
                params,
                abortHandle,
                onUpdate,
                dispatchMode,
                value,
                timeoutMillis,
                maxRetries,
                attributes);
    }

    public ToolExecutionContext withTimeoutMillis(long value) {
        return new ToolExecutionContext(
                toolCallId,
                toolName,
                tool,
                params,
                abortHandle,
                onUpdate,
                dispatchMode,
                confirmationRequired,
                value,
                maxRetries,
                attributes);
    }

    public ToolExecutionContext withMaxRetries(int value) {
        return new ToolExecutionContext(
                toolCallId,
                toolName,
                tool,
                params,
                abortHandle,
                onUpdate,
                dispatchMode,
                confirmationRequired,
                timeoutMillis,
                value,
                attributes);
    }

    public ToolExecutionContext withAttribute(String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<String, Object>(attributes);
        copy.put(key, value);
        return new ToolExecutionContext(
                toolCallId,
                toolName,
                tool,
                params,
                abortHandle,
                onUpdate,
                dispatchMode,
                confirmationRequired,
                timeoutMillis,
                maxRetries,
                copy);
    }

    public <T> ToolExecutionContext withAttribute(AttributeKey<T> key, T value) {
        return withAttribute(key.getName(), value);
    }
}
