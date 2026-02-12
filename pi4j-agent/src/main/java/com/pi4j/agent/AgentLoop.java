package com.pi4j.agent;

import com.pi4j.agent.event.AgentEvent;
import com.pi4j.ai.provider.AbortHandle;
import com.pi4j.ai.stream.EventStream;
import java.util.List;

final class AgentLoop {
    private AgentLoop() {
    }

    static EventStream<AgentEvent, List<AgentMessage>> run(
            List<AgentMessage> prompts,
            Agent agent,
            AbortHandle abortHandle) {
        EventStream<AgentEvent, List<AgentMessage>> stream = new EventStream<AgentEvent, List<AgentMessage>>();
        stream.end(prompts);
        return stream;
    }
}
