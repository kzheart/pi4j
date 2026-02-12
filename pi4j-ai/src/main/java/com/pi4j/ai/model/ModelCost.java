package com.pi4j.ai.model;

public final class ModelCost {
    private final double input;
    private final double output;
    private final double cacheRead;
    private final double cacheWrite;

    public ModelCost(double input, double output, double cacheRead, double cacheWrite) {
        this.input = input;
        this.output = output;
        this.cacheRead = cacheRead;
        this.cacheWrite = cacheWrite;
    }

    public double getInput() {
        return input;
    }

    public double getOutput() {
        return output;
    }

    public double getCacheRead() {
        return cacheRead;
    }

    public double getCacheWrite() {
        return cacheWrite;
    }
}
