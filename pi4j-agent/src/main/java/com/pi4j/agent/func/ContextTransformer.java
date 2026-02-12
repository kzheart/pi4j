package com.pi4j.agent.func;

import com.pi4j.agent.AgentMessage;
import com.pi4j.ai.provider.AbortHandle;
import java.util.List;

@FunctionalInterface
public interface ContextTransformer {
    List<AgentMessage> transform(List<AgentMessage> messages, AbortHandle abortHandle);
}
