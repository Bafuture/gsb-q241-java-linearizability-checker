package com.example.gsb.lincheck.harness;

/** Minimal FIFO queue interface used by the built-in verification harness. */
public interface ConcurrentQueue {

    void enq(Object value);

    /** Returns {@code null} when the queue is empty. */
    Object deq();
}
