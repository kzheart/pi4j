package com.pi4j.ai.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class AssistantMessage extends Message {
    private final List<ContentBlock> content;
    private final String api;
    private final String provider;
    private final String model;
    private final Usage usage;
    private final StopReason stopReason;
    private final String errorMessage;

    public AssistantMessage(
            List<ContentBlock> content,
            String api,
            String provider,
            String model,
            Usage usage,
            StopReason stopReason,
            String errorMessage) {
        this(content, api, provider, model, usage, stopReason, errorMessage, System.currentTimeMillis());
    }

    public AssistantMessage(
            List<ContentBlock> content,
            String api,
            String provider,
            String model,
            Usage usage,
            StopReason stopReason,
            String errorMessage,
            long timestamp) {
        super("assistant", timestamp);
        this.content = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(content, "content")));
        this.api = api;
        this.provider = provider;
        this.model = model;
        this.usage = usage;
        this.stopReason = stopReason;
        this.errorMessage = errorMessage;
    }

    public List<ContentBlock> getContent() {
        return content;
    }

    public String getApi() {
        return api;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public Usage getUsage() {
        return usage;
    }

    public StopReason getStopReason() {
        return stopReason;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
