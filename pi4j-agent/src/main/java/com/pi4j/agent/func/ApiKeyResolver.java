package com.pi4j.agent.func;

@FunctionalInterface
public interface ApiKeyResolver {
    String resolve(String provider);
}
