package com.pi4j.ai.provider;

/**
 * Provider 调用失败的类型化异常：携带错误类别、HTTP 状态码与响应体摘要。
 */
public class ProviderException extends RuntimeException {
    private final ErrorKind kind;
    private final Integer httpStatus;
    private final String responseBody;

    public ProviderException(ErrorKind kind, Integer httpStatus, String responseBody, String message) {
        super(message);
        this.kind = kind == null ? ErrorKind.UNKNOWN : kind;
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
    }

    public ProviderException(ErrorKind kind, Integer httpStatus, String responseBody, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind == null ? ErrorKind.UNKNOWN : kind;
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
    }

    public ErrorKind getKind() {
        return kind;
    }

    /** 可能为 null（非 HTTP 层错误）。 */
    public Integer getHttpStatus() {
        return httpStatus;
    }

    /** 可能为 null（非 HTTP 层错误）；HTTP 错误时为截断到 512 字符的响应体。 */
    public String getResponseBody() {
        return responseBody;
    }

    public boolean isRetryableSameModel() {
        return kind.isRetryableSameModel();
    }

    public boolean isRetryableOtherModel() {
        return kind.isRetryableOtherModel();
    }
}
