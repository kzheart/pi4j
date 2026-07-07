package com.pi4j.agent.event;

import com.pi4j.agent.AgentMessage;
import com.pi4j.ai.provider.ErrorKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AgentEndEvent extends AgentEvent {
    private final List<AgentMessage> messages;
    private final String error;
    private final ErrorKind errorKind;

    public AgentEndEvent(List<AgentMessage> messages) {
        this(messages, null, null);
    }

    public AgentEndEvent(List<AgentMessage> messages, String error, ErrorKind errorKind) {
        super("agent_end");
        this.messages = Collections.unmodifiableList(new ArrayList<AgentMessage>(messages));
        this.error = error;
        this.errorKind = errorKind;
    }

    public List<AgentMessage> getMessages() {
        return messages;
    }

    /** 正常结束时为 {@code null}。 */
    public String getError() {
        return error;
    }

    /** 正常结束时为 {@code null}。 */
    public ErrorKind getErrorKind() {
        return errorKind;
    }
}
