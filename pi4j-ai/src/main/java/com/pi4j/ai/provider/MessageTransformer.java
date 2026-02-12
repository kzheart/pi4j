package com.pi4j.ai.provider;

import com.pi4j.ai.types.AssistantMessage;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.Message;
import com.pi4j.ai.types.Model;
import com.pi4j.ai.types.StopReason;
import com.pi4j.ai.types.TextContent;
import com.pi4j.ai.types.ThinkingContent;
import java.util.ArrayList;
import java.util.List;

public final class MessageTransformer {
    private MessageTransformer() {
    }

    public static List<Message> transform(List<Message> messages, Model targetModel) {
        List<Message> transformed = new ArrayList<Message>();
        for (Message message : messages) {
            if (message instanceof AssistantMessage) {
                AssistantMessage assistant = (AssistantMessage) message;
                if (assistant.getStopReason() == StopReason.ERROR || assistant.getStopReason() == StopReason.ABORTED) {
                    continue;
                }
                if (!targetModel.getProvider().equals(assistant.getProvider())) {
                    transformed.add(convertThinkingToText(assistant));
                    continue;
                }
            }
            transformed.add(message);
        }
        return transformed;
    }

    private static AssistantMessage convertThinkingToText(AssistantMessage message) {
        List<ContentBlock> converted = new ArrayList<ContentBlock>();
        for (ContentBlock block : message.getContent()) {
            if (block instanceof ThinkingContent) {
                ThinkingContent thinking = (ThinkingContent) block;
                converted.add(new TextContent(thinking.getThinking()));
            } else {
                converted.add(block);
            }
        }
        return new AssistantMessage(
                converted,
                message.getApi(),
                message.getProvider(),
                message.getModel(),
                message.getUsage(),
                message.getStopReason(),
                message.getErrorMessage(),
                message.getTimestamp());
    }
}
