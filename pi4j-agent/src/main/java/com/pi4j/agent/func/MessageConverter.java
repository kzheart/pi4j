package com.pi4j.agent.func;

import com.pi4j.agent.AgentMessage;
import com.pi4j.ai.types.Message;
import java.util.List;

@FunctionalInterface
public interface MessageConverter {
    List<Message> convert(List<AgentMessage> messages);
}
