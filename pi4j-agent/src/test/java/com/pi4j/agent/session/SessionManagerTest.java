package com.pi4j.agent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pi4j.agent.AgentMessage;
import com.pi4j.agent.LlmAgentMessage;
import com.pi4j.ai.types.ContentBlock;
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

        List<AgentMessage> messages = session.getMessages();
        assertEquals(1, messages.size());
        assertTrue(messages.get(0) instanceof LlmAgentMessage);

        Session loaded = manager.load("demo-session");
        assertEquals(1, loaded.getMessages().size());

        List<SessionInfo> sessions = manager.list();
        assertFalse(sessions.isEmpty());
        assertEquals("demo-session", sessions.get(0).getSessionId());

        manager.delete("demo-session");
        assertTrue(manager.list().isEmpty());
    }
}
