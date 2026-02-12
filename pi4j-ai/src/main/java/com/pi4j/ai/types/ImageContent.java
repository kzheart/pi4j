package com.pi4j.ai.types;

import java.util.Objects;

public final class ImageContent extends ContentBlock {
    private final String data;
    private final String mimeType;

    public ImageContent(String data, String mimeType) {
        super("image");
        this.data = Objects.requireNonNull(data, "data");
        this.mimeType = Objects.requireNonNull(mimeType, "mimeType");
    }

    public String getData() {
        return data;
    }

    public String getMimeType() {
        return mimeType;
    }
}
