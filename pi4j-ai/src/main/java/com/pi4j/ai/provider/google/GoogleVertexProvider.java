package com.pi4j.ai.provider.google;

import com.pi4j.ai.provider.StreamOptions;
import com.pi4j.ai.types.Context;
import com.pi4j.ai.types.Model;
import java.util.Map;
import okhttp3.Request;

public class GoogleVertexProvider extends GoogleProvider {
    @Override
    public String getApi() {
        return "google-vertex";
    }

    @Override
    protected Request buildRequest(Model model, Context context, StreamOptions options) {
        Request baseRequest = super.buildRequest(model, context, options);
        Request.Builder builder = baseRequest.newBuilder();

        builder.removeHeader("x-goog-api-key");
        builder.removeHeader("authorization");
        builder.header("authorization", "Bearer " + options.getApiKey());

        for (Map.Entry<String, String> entry : model.getHeaders().entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : options.getHeaders().entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }

        return builder.build();
    }

    @Override
    protected String buildStreamUrl(String baseUrl, String modelId, String apiKey) {
        if (baseUrl.contains(":streamGenerateContent")) {
            return baseUrl + (baseUrl.contains("?") ? "&" : "?") + "alt=sse";
        }

        String project = System.getenv("GOOGLE_CLOUD_PROJECT");
        String location = System.getenv("GOOGLE_CLOUD_LOCATION");

        if (project == null || project.trim().isEmpty()) {
            project = "demo-project";
        }
        if (location == null || location.trim().isEmpty()) {
            location = "us-central1";
        }

        return baseUrl
                + "/v1/projects/"
                + project
                + "/locations/"
                + location
                + "/publishers/google/models/"
                + modelId
                + ":streamGenerateContent?alt=sse";
    }
}
