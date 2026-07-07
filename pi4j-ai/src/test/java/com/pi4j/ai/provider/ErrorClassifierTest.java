package com.pi4j.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class ErrorClassifierTest {

    private static final String API = "anthropic-messages";

    @Test
    void classifyHttp401IsAuth() {
        ProviderException ex = ErrorClassifier.classifyHttp(API, 401, "...");
        assertEquals(ErrorKind.AUTH, ex.getKind());
        assertEquals(Integer.valueOf(401), ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("request failed: 401"));
    }

    @Test
    void classifyHttp429IsRateLimitedAndRetryable() {
        ProviderException ex = ErrorClassifier.classifyHttp(API, 429, "rate limited");
        assertEquals(ErrorKind.RATE_LIMITED, ex.getKind());
        assertTrue(ex.isRetryableSameModel());
    }

    @Test
    void classifyHttp400OverflowBody() {
        ProviderException ex = ErrorClassifier.classifyHttp(API, 400, "prompt is too long");
        assertEquals(ErrorKind.CONTEXT_OVERFLOW, ex.getKind());
        assertFalse(ex.isRetryableSameModel());
        assertTrue(ex.isRetryableOtherModel());
    }

    @Test
    void classifyHttp400ContentFilterBody() {
        ProviderException ex = ErrorClassifier.classifyHttp(API, 400, "blocked by content_filter");
        assertEquals(ErrorKind.CONTENT_FILTER, ex.getKind());
        assertFalse(ex.isRetryableSameModel());
        assertFalse(ex.isRetryableOtherModel());
    }

    @Test
    void classifyHttp400BadRequestBody() {
        ProviderException ex = ErrorClassifier.classifyHttp(API, 400, "invalid json");
        assertEquals(ErrorKind.BAD_REQUEST, ex.getKind());
    }

    @Test
    void classifyHttp413EmptyBodyIsContextOverflow() {
        ProviderException ex = ErrorClassifier.classifyHttp(API, 413, "");
        assertEquals(ErrorKind.CONTEXT_OVERFLOW, ex.getKind());
    }

    @Test
    void classifyHttp503IsServerError() {
        ProviderException ex = ErrorClassifier.classifyHttp(API, 503, "unavailable");
        assertEquals(ErrorKind.SERVER_ERROR, ex.getKind());
    }

    @Test
    void toProviderExceptionSocketTimeoutIsTimeout() {
        ProviderException ex = ErrorClassifier.toProviderException(
                API, new SocketTimeoutException("read timed out"), false);
        assertEquals(ErrorKind.TIMEOUT, ex.getKind());
    }

    @Test
    void toProviderExceptionUnknownHostIsNetwork() {
        ProviderException ex = ErrorClassifier.toProviderException(
                API, new UnknownHostException("x"), false);
        assertEquals(ErrorKind.NETWORK, ex.getKind());
    }

    @Test
    void toProviderExceptionAbortedTakesPriority() {
        ProviderException ex = ErrorClassifier.toProviderException(
                API, new IOException("Canceled"), true);
        assertEquals(ErrorKind.ABORTED, ex.getKind());
    }

    @Test
    void toProviderExceptionReturnsExistingProviderException() {
        ProviderException original =
                new ProviderException(ErrorKind.AUTH, 401, null, "already classified");
        ProviderException ex = ErrorClassifier.toProviderException(API, original, false);
        assertSame(original, ex);
    }

    @Test
    void toProviderExceptionMissingApiKeyIsAuth() {
        ProviderException ex = ErrorClassifier.toProviderException(
                API, new IllegalArgumentException("apiKey is required"), false);
        assertEquals(ErrorKind.AUTH, ex.getKind());
    }
}
