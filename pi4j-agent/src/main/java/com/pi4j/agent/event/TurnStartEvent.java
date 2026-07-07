package com.pi4j.agent.event;

import com.pi4j.ai.types.Context;

public final class TurnStartEvent extends AgentEvent {
    private final Context context;
    private final int turnIndex;

    public TurnStartEvent(Context context, int turnIndex) {
        super("turn_start");
        this.context = context;
        this.turnIndex = turnIndex;
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

    /** 本次循环内从 0 递增的回合序号。 */
    public int getTurnIndex() {
        return turnIndex;
    }
}
