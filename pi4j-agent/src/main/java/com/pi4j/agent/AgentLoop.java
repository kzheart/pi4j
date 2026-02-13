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
        try {
            if (prompts != null && !prompts.isEmpty()) {
                for (AgentMessage prompt : prompts) {
                    agent.appendMessageFromLoop(prompt);
                }
            }
            agent.executeLoopFromLoop(abortHandle);
            stream.end(agent.snapshotMessagesFromLoop());
        } catch (Exception ex) {
            stream.error(ex);
        }
        return stream;
    }
}
