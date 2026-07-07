package com.pi4j.ai.provider;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 把 HTTP 状态码 / 异常映射为带 {@link ErrorKind} 的 {@link ProviderException}。
 */
public final class ErrorClassifier {

    private static final List<Pattern> OVERFLOW_PATTERNS = Arrays.asList(
            Pattern.compile("prompt is too long", Pattern.CASE_INSENSITIVE),
            Pattern.compile("input is too long for requested model", Pattern.CASE_INSENSITIVE),
            Pattern.compile("exceeds the context window", Pattern.CASE_INSENSITIVE),
            Pattern.compile("input token count.*exceeds the maximum", Pattern.CASE_INSENSITIVE),
            Pattern.compile("maximum prompt length is \\d+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("reduce the length of the messages", Pattern.CASE_INSENSITIVE),
            Pattern.compile("maximum context length is \\d+ tokens", Pattern.CASE_INSENSITIVE),
            Pattern.compile("exceeds the limit of \\d+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("exceeds the available context size", Pattern.CASE_INSENSITIVE),
            Pattern.compile("greater than the context length", Pattern.CASE_INSENSITIVE),
            Pattern.compile("context window exceeds limit", Pattern.CASE_INSENSITIVE),
            Pattern.compile("exceeded model token limit", Pattern.CASE_INSENSITIVE),
            Pattern.compile("context[_ ]length[_ ]exceeded", Pattern.CASE_INSENSITIVE),
            Pattern.compile("too many tokens", Pattern.CASE_INSENSITIVE),
            Pattern.compile("token limit exceeded", Pattern.CASE_INSENSITIVE));

    private static final List<Pattern> CONTENT_FILTER_PATTERNS = Arrays.asList(
            Pattern.compile("content[_ ]filter", Pattern.CASE_INSENSITIVE),
            Pattern.compile("content[_ ]policy", Pattern.CASE_INSENSITIVE),
            Pattern.compile("content management policy", Pattern.CASE_INSENSITIVE),
            Pattern.compile("moderation", Pattern.CASE_INSENSITIVE),
            Pattern.compile("safety (system|settings|reasons)", Pattern.CASE_INSENSITIVE));

    private ErrorClassifier() {
    }

    /** HTTP 非 2xx 响应 → 类型化异常。body 为已截断的响应体，可为 null。 */
    public static ProviderException classifyHttp(String api, int httpStatus, String body) {
        String trimmedBody = body == null ? "" : body.trim();
        ErrorKind kind = kindOfHttp(httpStatus, trimmedBody);
        String display = trimmedBody.isEmpty() ? "(no body)" : trimmedBody;
        return new ProviderException(
                kind, httpStatus, trimmedBody, api + " request failed: " + httpStatus + " " + display);
    }

    /** 任意异常 → 类型化异常。aborted 为调用方是否已主动中止（优先级最高）。 */
    public static ProviderException toProviderException(String api, Exception ex, boolean aborted) {
        if (aborted || ex instanceof AbortException) {
            return new ProviderException(ErrorKind.ABORTED, null, null, api + " request aborted", ex);
        }
        if (ex instanceof ProviderException) {
            return (ProviderException) ex;
        }
        ErrorKind kind;
        if (ex instanceof InterruptedIOException) {
            kind = ErrorKind.TIMEOUT;
        } else if (ex instanceof UnknownHostException
                || ex instanceof ConnectException
                || ex instanceof NoRouteToHostException) {
            kind = ErrorKind.NETWORK;
        } else if (ex instanceof IOException) {
            kind = ErrorKind.NETWORK;
        } else if (ex instanceof IllegalArgumentException
                && ex.getMessage() != null
                && ex.getMessage().contains("apiKey")) {
            kind = ErrorKind.AUTH;
        } else {
            kind = ErrorKind.UNKNOWN;
        }
        String message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
        return new ProviderException(kind, null, null, message, ex);
    }

    private static ErrorKind kindOfHttp(int httpStatus, String body) {
        if (httpStatus == 401 || httpStatus == 403) {
            return ErrorKind.AUTH;
        }
        if (httpStatus == 429) {
            return ErrorKind.RATE_LIMITED;
        }
        if (httpStatus == 408 || httpStatus == 504) {
            return ErrorKind.TIMEOUT;
        }
        if (httpStatus == 400 || httpStatus == 413 || httpStatus == 422) {
            if (body.isEmpty()) {
                // 部分网关对超限请求直接回 400/413 无 body，按上下文溢出处理
                return httpStatus == 422 ? ErrorKind.BAD_REQUEST : ErrorKind.CONTEXT_OVERFLOW;
            }
            if (matchesAny(OVERFLOW_PATTERNS, body)) {
                return ErrorKind.CONTEXT_OVERFLOW;
            }
            if (matchesAny(CONTENT_FILTER_PATTERNS, body)) {
                return ErrorKind.CONTENT_FILTER;
            }
            return ErrorKind.BAD_REQUEST;
        }
        if (httpStatus >= 500) {
            return ErrorKind.SERVER_ERROR;
        }
        return ErrorKind.UNKNOWN;
    }

    private static boolean matchesAny(List<Pattern> patterns, String text) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }
}
