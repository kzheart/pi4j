package com.pi4j.agent.tool;

import java.util.concurrent.CompletableFuture;

/**
 * 异步确认闸门：消费方收到请求后向用户征询，把结果写入返回的 future；
 * 超时与 abort 联动由框架的 confirmation 中间件统一处理。
 */
public interface ConfirmationGate {

    enum Decision {
        APPROVED,
        DENIED
    }

    CompletableFuture<Decision> requestConfirmation(ToolExecutionContext context);
}
