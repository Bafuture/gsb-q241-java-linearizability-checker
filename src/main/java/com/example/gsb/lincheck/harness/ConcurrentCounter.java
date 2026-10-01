package com.example.gsb.lincheck.harness;

/** Minimal counter interface used by the built-in verification harness. */
public interface ConcurrentCounter {

    long incrementAndGet();

    long get();
}
