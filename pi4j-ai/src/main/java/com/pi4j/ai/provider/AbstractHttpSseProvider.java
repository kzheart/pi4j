package com.pi4j.ai.provider;
import com.pi4j.ai.stream.AssistantMessageEventStream;
import com.pi4j.ai.stream.ErrorEvent;
import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.Context;
import com.pi4j.ai.types.Model;
import com.pi4j.ai.types.StopReason;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * HTTP + SSE 流式 provider 的共享骨架：统一负责请求发起、一次重试、abort→Call.cancel 联动、
 * HTTP 错误分类（{@link ProviderException}）与错误事件投递。子类只实现请求构建与 SSE 解析。
 */
public abstract class AbstractHttpSseProvider implements ApiProvider {

    protected final OkHttpClient client;

    protected AbstractHttpSseProvider(OkHttpClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
        AssistantMessageEventStream stream = new AssistantMessageEventStream();
        CompletableFuture.runAsync(() -> invokeStream(stream, model, context, options));
        return stream;
    }

    /** 构建本次请求（子类专属：URL、认证头、payload）。 */
    protected abstract Request buildRequest(Model model, Context context, StreamOptions options);

    /** 解析 SSE 响应流并向 stream 推送事件（子类专属）。 */
    protected abstract void parseSse(Reader reader, AssistantMessageEventStream stream, Model model, AbortHandle abortHandle)
            throws IOException;

    private void invokeStream(AssistantMessageEventStream stream, Model model, Context context, StreamOptions options) {
        AbortHandle abortHandle = options.getAbortHandle();
        try {
            IOException parseError = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                Request request = buildRequest(model, context, options);
                Call call = client.newCall(request);
                Runnable cancelOnAbort = call::cancel;
                if (abortHandle != null) {
                    abortHandle.addListener(cancelOnAbort);
                    if (abortHandle.isAborted()) {
                        call.cancel();
                    }
                }
                try (Response response = call.execute()) {
                    if (!response.isSuccessful()) {
                        throw ErrorClassifier.classifyHttp(getApi(), response.code(), readErrorBody(response));
                    }
                    if (response.body() == null) {
                        throw new ProviderException(ErrorKind.SERVER_ERROR, response.code(), null,
                                getApi() + " response body is empty");
                    }
                    try {
                        parseSse(response.body().charStream(), stream, model, abortHandle);
                        return;
                    } catch (IOException ioException) {
                        parseError = ioException;
                        if (attempt == 1 || (abortHandle != null && abortHandle.isAborted())) {
                            throw ioException;
                        }
                    }
                } finally {
                    if (abortHandle != null) {
                        abortHandle.removeListener(cancelOnAbort);
                    }
                }
            }
            if (parseError != null) {
                throw parseError;
            }
        } catch (Exception ex) {
            boolean aborted = abortHandle != null && abortHandle.isAborted();
            ProviderException providerException = ErrorClassifier.toProviderException(getApi(), ex, aborted);
            AssistantMessage errorMessage = new AssistantMessage(
                    Collections.<ContentBlock>emptyList(),
                    getApi(),
                    model.getProvider(),
                    model.getId(),
                    null,
                    aborted ? StopReason.ABORTED : StopReason.ERROR,
                    providerException.getMessage());
            stream.push(new ErrorEvent(errorMessage.getStopReason(), errorMessage));
            stream.error(providerException);
        }
    }

    private String readErrorBody(Response response) {
        try {
            String body = response.body() == null ? "" : response.body().string();
            if (body.length() > 512) {
                body = body.substring(0, 512);
            }
            return body;
        } catch (IOException ioException) {
            return "";
        }
    }
}
