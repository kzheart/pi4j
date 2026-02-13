package com.pi4j.agent;

import com.pi4j.agent.func.ApiKeyResolver;
import com.pi4j.agent.func.ContextTransformer;
import com.pi4j.agent.func.MessageConverter;
import com.pi4j.agent.tool.AgentTool;
import com.pi4j.ai.types.Message;
import com.pi4j.ai.types.Model;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AgentOptions {
    private final String systemPrompt;
    private final Model model;
    private final String thinkingLevel;
    private final List<AgentTool> tools;
    private final List<AgentMessage> initialMessages;
    private final MessageConverter convertToLlm;
    private final ContextTransformer transformContext;
    private final ApiKeyResolver getApiKey;
    private final Double temperature;
    private final Integer maxTokens;
    private final Integer thinkingBudget;
    private final String thinkingEffort;
    private final String toolChoice;
    private final String cacheRetention;
    private final String sessionId;
    private final String steeringMode;
    private final String followUpMode;

    private AgentOptions(Builder builder) {
        this.systemPrompt = builder.systemPrompt;
        this.model = builder.model;
        this.thinkingLevel = builder.thinkingLevel;
        this.tools = Collections.unmodifiableList(new ArrayList<AgentTool>(builder.tools));
        this.initialMessages = Collections.unmodifiableList(new ArrayList<AgentMessage>(builder.initialMessages));
        this.convertToLlm = builder.convertToLlm;
        this.transformContext = builder.transformContext;
        this.getApiKey = builder.getApiKey;
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.thinkingBudget = builder.thinkingBudget;
        this.thinkingEffort = builder.thinkingEffort;
        this.toolChoice = builder.toolChoice;
        this.cacheRetention = builder.cacheRetention;
        this.sessionId = builder.sessionId;
        this.steeringMode = builder.steeringMode;
        this.followUpMode = builder.followUpMode;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public Model getModel() {
        return model;
    }

    public String getThinkingLevel() {
        return thinkingLevel;
    }

    public List<AgentTool> getTools() {
        return tools;
    }

    public List<AgentMessage> getInitialMessages() {
        return initialMessages;
    }

    public MessageConverter getConvertToLlm() {
        return convertToLlm;
    }

    public ContextTransformer getTransformContext() {
        return transformContext;
    }

    public ApiKeyResolver getGetApiKey() {
        return getApiKey;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public Integer getThinkingBudget() {
        return thinkingBudget;
    }

    public String getThinkingEffort() {
        return thinkingEffort;
    }

    public String getToolChoice() {
        return toolChoice;
    }

    public String getCacheRetention() {
        return cacheRetention;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getSteeringMode() {
        return steeringMode;
    }

    public String getFollowUpMode() {
        return followUpMode;
    }

    public static final class Builder {
        private String systemPrompt;
        private Model model;
        private String thinkingLevel = "off";
        private List<AgentTool> tools = new ArrayList<AgentTool>();
        private List<AgentMessage> initialMessages = new ArrayList<AgentMessage>();
        private MessageConverter convertToLlm = messages -> {
            List<Message> converted = new ArrayList<Message>();
            for (AgentMessage message : messages) {
                if (message instanceof LlmAgentMessage) {
                    converted.add(((LlmAgentMessage) message).getMessage());
                }
            }
            return converted;
        };
        private ContextTransformer transformContext = (messages, abortHandle) -> messages;
        private ApiKeyResolver getApiKey = provider -> System.getenv("DEEPSEEK_API_KEY");
        private Double temperature;
        private Integer maxTokens;
        private Integer thinkingBudget;
        private String thinkingEffort;
        private String toolChoice;
        private String cacheRetention;
        private String sessionId;
        private String steeringMode = "all";
        private String followUpMode = "all";

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder model(Model model) {
            this.model = model;
            return this;
        }

        public Builder thinkingLevel(String thinkingLevel) {
            this.thinkingLevel = thinkingLevel;
            return this;
        }

        public Builder tools(List<AgentTool> tools) {
            this.tools = new ArrayList<AgentTool>(tools);
            return this;
        }

        public Builder initialMessages(List<AgentMessage> initialMessages) {
            this.initialMessages = new ArrayList<AgentMessage>(initialMessages);
            return this;
        }

        public Builder convertToLlm(MessageConverter convertToLlm) {
            this.convertToLlm = convertToLlm;
            return this;
        }

        public Builder transformContext(ContextTransformer transformContext) {
            this.transformContext = transformContext;
            return this;
        }

        public Builder getApiKey(ApiKeyResolver getApiKey) {
            this.getApiKey = getApiKey;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder thinkingBudget(Integer thinkingBudget) {
            this.thinkingBudget = thinkingBudget;
            return this;
        }

        public Builder thinkingEffort(String thinkingEffort) {
            this.thinkingEffort = thinkingEffort;
            return this;
        }

        public Builder toolChoice(String toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public Builder cacheRetention(String cacheRetention) {
            this.cacheRetention = cacheRetention;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder steeringMode(String steeringMode) {
            this.steeringMode = steeringMode;
            return this;
        }

        public Builder followUpMode(String followUpMode) {
            this.followUpMode = followUpMode;
            return this;
        }

        public AgentOptions build() {
            return new AgentOptions(this);
        }
    }
}
