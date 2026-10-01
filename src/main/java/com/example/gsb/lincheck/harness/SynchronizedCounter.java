package com.example.gsb.lincheck.harness;

/** A correctly synchronized (linearizable) counter. */
public final class SynchronizedCounter implements ConcurrentCounter {

    private long value;

    @Override
    public synchronized long incrementAndGet() {
        return ++value;
    }

    @Override
    public synchronized long get() {
        return value;
    }
}
