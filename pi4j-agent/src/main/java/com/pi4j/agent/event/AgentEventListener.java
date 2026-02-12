package com.pi4j.agent.event;

@FunctionalInterface
public interface AgentEventListener {
    void onEvent(AgentEvent event);
}
