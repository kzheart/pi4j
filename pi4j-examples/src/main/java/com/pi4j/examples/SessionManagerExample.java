package com.pi4j.examples;

import com.pi4j.agent.LlmAgentMessage;
import com.pi4j.agent.session.Session;
import com.pi4j.agent.session.SessionInfo;
import com.pi4j.agent.session.SessionManager;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.TextContent;
import com.pi4j.ai.types.UserMessage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

public final class SessionManagerExample {
    private SessionManagerExample() {
    }

    public static void main(String[] args) {
        Path sessionDir = Paths.get("build", "example-sessions");
        SessionManager sessionManager = new SessionManager(sessionDir);

        Session session = sessionManager.create("demo-session");
        List<ContentBlock> content = Collections.<ContentBlock>singletonList(new TextContent("hello session"));
        session.appendMessage(new LlmAgentMessage(new UserMessage(content)));

        Session loaded = sessionManager.load("demo-session");
        System.out.println("messages=" + loaded.getMessages().size());

        for (SessionInfo info : sessionManager.list()) {
            System.out.println(info.getSessionId() + " -> " + info.getPath());
        }
    }
}
