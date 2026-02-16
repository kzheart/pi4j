package com.pi4j.agent.event;

import com.pi4j.ai.types.Context;

public final class TurnStartEvent extends AgentEvent {
    private final Context context;

    public TurnStartEvent(Context context) {
        super("turn_start");
        this.context = context;
    }

    /**
     * The full context sent to the LLM for this turn,
     * including system prompt, messages, and tool definitions.
     *
     * @return the LLM context, or {@code null} if not available
     */
    public Context getContext() {
        return context;
    }
}
