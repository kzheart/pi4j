package com.pi4j.ai.provider.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.pi4j.ai.provider.StreamOptions;
import com.pi4j.ai.types.ContentBlock;
import com.pi4j.ai.types.Context;
import com.pi4j.ai.types.Message;
import com.pi4j.ai.types.Model;
import com.pi4j.ai.types.TextContent;
import com.pi4j.ai.types.UserMessage;
import java.util.Arrays;
import java.util.Collections;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import org.junit.jupiter.api.Test;

class BedrockProviderTest {

    @Test
    void buildRequestBuildsBedrockConversePayload() {
        BedrockProvider provider = new BedrockProvider();
        Model model = new Model(
                "anthropic.claude-3-5-sonnet-20240620-v1:0",
                "Claude",
                "bedrock-converse-stream",
                "bedrock",
                null,
                false,
                Arrays.asList("text"),
                null,
                200000,
                4096,
                Collections.<String, String>singletonMap("aws-region", "us-east-1"));

        Context context = new Context(
                null,
                Collections.<Message>singletonList(new UserMessage(Collections.<ContentBlock>singletonList(new TextContent("hi")))),
                Collections.emptyList());

        ConverseStreamRequest request = provider.buildRequest(model, context, StreamOptions.builder().build());

        assertEquals("anthropic.claude-3-5-sonnet-20240620-v1:0", request.modelId());
        assertEquals(1, request.messages().size());
        assertEquals("user", request.messages().get(0).roleAsString());
    }
}
