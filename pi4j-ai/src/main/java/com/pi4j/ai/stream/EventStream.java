package com.pi4j.ai.stream;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class EventStream<T, R> {
    private final Queue<T> queue = new ConcurrentLinkedQueue<>();
    private final CopyOnWriteArrayList<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    private final CompletableFuture<R> finalResult = new CompletableFuture<>();
    private volatile boolean done;

    public void push(T event) {
        if (done) {
            return;
        }
        queue.offer(event);
        for (Consumer<T> listener : listeners) {
            listener.accept(event);
        }
    }

    public void end(R result) {
        if (done) {
            return;
        }
        done = true;
        finalResult.complete(result);
    }

    public void error(Throwable cause) {
        if (done) {
            return;
        }
        done = true;
        finalResult.completeExceptionally(cause);
    }

    public Runnable subscribe(Consumer<T> listener) {
        for (T event : queue) {
            listener.accept(event);
        }
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public CompletableFuture<R> result() {
        return finalResult;
    }

    public boolean isDone() {
        return done;
    }
}
