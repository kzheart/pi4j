package com.pi4j.ai.stream;

import java.util.Queue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class EventStream<T, R> {
    private final Object lock = new Object();
    private final Queue<T> queue = new ConcurrentLinkedQueue<>();
    private final CopyOnWriteArrayList<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    private final CompletableFuture<R> finalResult = new CompletableFuture<>();
    private volatile boolean done;

    public void push(T event) {
        List<Consumer<T>> snapshot;
        synchronized (lock) {
            if (done) {
                return;
            }
            queue.offer(event);
            snapshot = new ArrayList<Consumer<T>>(listeners);
        }
        for (Consumer<T> listener : snapshot) {
            listener.accept(event);
        }
    }

    public void end(R result) {
        synchronized (lock) {
            if (done) {
                return;
            }
            done = true;
        }
        finalResult.complete(result);
    }

    public void error(Throwable cause) {
        synchronized (lock) {
            if (done) {
                return;
            }
            done = true;
        }
        finalResult.completeExceptionally(cause);
    }

    public Runnable subscribe(Consumer<T> listener) {
        List<T> replay;
        synchronized (lock) {
            listeners.add(listener);
            replay = new ArrayList<T>(queue);
        }
        for (T event : replay) {
            listener.accept(event);
        }
        return () -> {
            synchronized (lock) {
                listeners.remove(listener);
            }
        };
    }

    public CompletableFuture<R> result() {
        return finalResult;
    }

    public boolean isDone() {
        return done;
    }
}
