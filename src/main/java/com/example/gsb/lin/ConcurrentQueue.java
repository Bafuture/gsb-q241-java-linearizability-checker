package com.example.gsb.lin;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Thread-safe FIFO queue backed by a {@link ConcurrentLinkedQueue}. */
public final class ConcurrentQueue<T> {

    private final ConcurrentLinkedQueue<T> queue = new ConcurrentLinkedQueue<>();

    public void enq(T item) {
        queue.add(item);
    }

    /** @return the head element, or {@code null} when the queue is empty */
    public T deq() {
        return queue.poll();
    }
}
