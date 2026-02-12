package com.pi4j.ai.provider;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AbortHandle {
    private volatile boolean aborted;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<Runnable>();

    public void abort() {
        if (aborted) {
            return;
        }
        aborted = true;
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    public boolean isAborted() {
        return aborted;
    }

    public void throwIfAborted() {
        if (aborted) {
            throw new AbortException("Execution aborted");
        }
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }
}
