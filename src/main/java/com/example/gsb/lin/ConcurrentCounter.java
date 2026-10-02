package com.example.gsb.lin;

import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe counter backed by an {@link AtomicLong}. */
public final class ConcurrentCounter {

    private final AtomicLong value = new AtomicLong();

    /** Atomically increments and returns the new value. */
    public long increment() {
        return value.incrementAndGet();
    }

    /** Atomically adds {@code delta} and returns the new value. */
    public long add(long delta) {
        return value.addAndGet(delta);
    }

    public long get() {
        return value.get();
    }
}
