package com.pi4j.agent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pi4j.agent.AgentMessage;
import com.pi4j.agent.LlmAgentMessage;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.ObservationMessage;
import com.pi4j.ai.types.TextContent;
import com.pi4j.ai.types.UserMessage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionManagerTest {

    @Test
    void createAppendLoadListDeleteWorkflow() throws Exception {
        Path tempDir = Files.createTempDirectory("pi4j-session-test");
        SessionManager manager = new SessionManager(tempDir);

        Session session = manager.create("demo-session");
        session.appendMessage(new LlmAgentMessage(new UserMessage(
                Collections.<ContentBlock>singletonList(new TextContent("hello")))));
        session.appendMessage(new LlmAgentMessage(new ObservationMessage(
                "pet-task",
                Collections.<ContentBlock>singletonList(new TextContent("completed")),
                1234L)));

        List<AgentMessage> messages = session.getMessages();
        assertEquals(2, messages.size());
        assertTrue(messages.get(0) instanceof LlmAgentMessage);

        Session loaded = manager.load("demo-session");
        assertEquals(2, loaded.getMessages().size());
        ObservationMessage observation = (ObservationMessage) ((LlmAgentMessage) loaded.getMessages().get(1)).getMessage();
        assertEquals("pet-task", observation.getSource());
        assertEquals(1234L, observation.getTimestamp());
        assertEquals("completed", ((TextContent) observation.getContent().get(0)).getText());

        List<SessionInfo> sessions = manager.list();
        assertFalse(sessions.isEmpty());
        assertEquals("demo-session", sessions.get(0).getSessionId());

        manager.delete("demo-session");
        assertTrue(manager.list().isEmpty());
    }
}
