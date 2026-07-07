package com.pi4j.ai.provider;

/**
 * Provider 错误类别与重试判据。
 */
public enum ErrorKind {
    /** 认证失败（401/403、缺少 apiKey）。 */
    AUTH(false, true),
    /** 限流（429）。 */
    RATE_LIMITED(true, true),
    /** 请求超时（408/504、读超时）。 */
    TIMEOUT(true, true),
    /** 网络不可达（DNS/连接失败/传输中断）。 */
    NETWORK(true, true),
    /** 输入超出模型上下文窗口。 */
    CONTEXT_OVERFLOW(false, true),
    /** 内容被安全策略拦截。 */
    CONTENT_FILTER(false, false),
    /** 服务端错误（5xx）。 */
    SERVER_ERROR(true, true),
    /** 请求不合法（400/413/422 且非溢出非过滤）。 */
    BAD_REQUEST(false, true),
    /** 调用方主动中止。 */
    ABORTED(false, false),
    /** 无法归类。 */
    UNKNOWN(false, true);

    private final boolean retryableSameModel;
    private final boolean retryableOtherModel;

    ErrorKind(boolean retryableSameModel, boolean retryableOtherModel) {
        this.retryableSameModel = retryableSameModel;
        this.retryableOtherModel = retryableOtherModel;
    }

    /** 同一模型立即重试是否可能成功（限流/超时/网络/服务端瞬时故障）。 */
    public boolean isRetryableSameModel() {
        return retryableSameModel;
    }

    /** 换一个模型或 provider 重试是否可能成功（内容过滤与主动中止除外）。 */
    public boolean isRetryableOtherModel() {
        return retryableOtherModel;
    }
}
