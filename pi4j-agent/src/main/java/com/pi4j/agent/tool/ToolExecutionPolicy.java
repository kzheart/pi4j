package com.pi4j.agent.tool;

/**
 * 工具的执行策略：由工具自身声明（{@link AgentTool#getExecutionPolicy()}），
 * 框架在进入中间件管线前套用到 {@link ToolExecutionContext}，
 * 消费方无需再写「向下转型工具实例来富化上下文」的中间件。
 */
public final class ToolExecutionPolicy {
    public static final ToolExecutionPolicy DEFAULT = builder().build();

    private final ToolDispatchMode dispatchMode;
    private final boolean confirmationRequired;
    private final long timeoutMillis;
    private final int maxRetries;

    private ToolExecutionPolicy(Builder builder) {
        this.dispatchMode = builder.dispatchMode;
        this.confirmationRequired = builder.confirmationRequired;
        this.timeoutMillis = builder.timeoutMillis;
        this.maxRetries = builder.maxRetries;
    }

    public static Builder builder() {
        return new Builder();
    }

    public ToolDispatchMode getDispatchMode() {
        return dispatchMode;
    }

    public boolean isConfirmationRequired() {
        return confirmationRequired;
    }

    /** 0 表示不限制。 */
    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    /** 0 表示不重试。 */
    public int getMaxRetries() {
        return maxRetries;
    }

    public static final class Builder {
        private ToolDispatchMode dispatchMode = ToolDispatchMode.DIRECT;
        private boolean confirmationRequired;
        private long timeoutMillis;
        private int maxRetries;

        public Builder dispatchMode(ToolDispatchMode dispatchMode) {
            this.dispatchMode = dispatchMode == null ? ToolDispatchMode.DIRECT : dispatchMode;
            return this;
        }

        public Builder confirmationRequired(boolean confirmationRequired) {
            this.confirmationRequired = confirmationRequired;
            return this;
        }

        public Builder timeoutMillis(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public ToolExecutionPolicy build() {
            return new ToolExecutionPolicy(this);
        }
    }
}
