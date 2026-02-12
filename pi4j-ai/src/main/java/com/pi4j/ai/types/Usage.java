package com.pi4j.ai.types;

public final class Usage {
    private final int input;
    private final int output;
    private final int cacheRead;
    private final int cacheWrite;
    private final int totalTokens;
    private final Cost cost;

    public Usage(int input, int output, int cacheRead, int cacheWrite, int totalTokens, Cost cost) {
        this.input = input;
        this.output = output;
        this.cacheRead = cacheRead;
        this.cacheWrite = cacheWrite;
        this.totalTokens = totalTokens;
        this.cost = cost;
    }

    public int getInput() {
        return input;
    }

    public int getOutput() {
        return output;
    }

    public int getCacheRead() {
        return cacheRead;
    }

    public int getCacheWrite() {
        return cacheWrite;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public Cost getCost() {
        return cost;
    }

    public static final class Cost {
        private final double input;
        private final double output;
        private final double cacheRead;
        private final double cacheWrite;
        private final double total;

        public Cost(double input, double output, double cacheRead, double cacheWrite, double total) {
            this.input = input;
            this.output = output;
            this.cacheRead = cacheRead;
            this.cacheWrite = cacheWrite;
            this.total = total;
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

        public double getTotal() {
            return total;
        }
    }
}
