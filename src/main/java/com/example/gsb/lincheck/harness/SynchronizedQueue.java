package com.example.gsb.lincheck.harness;

import java.util.ArrayDeque;
import java.util.Deque;

/** A correctly synchronized (linearizable) FIFO queue. */
public final class SynchronizedQueue implements ConcurrentQueue {

    private final Deque<Object> items = new ArrayDeque<>();

    @Override
    public synchronized void enq(Object value) {
        items.addLast(value);
    }

    @Override
    public synchronized Object deq() {
        return items.pollFirst();
    }
}
