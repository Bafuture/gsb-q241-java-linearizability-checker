package com.example.gsb.lincheck.harness;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * A deliberately <b>non-linearizable</b> counter.
 *
 * <p>Exactly {@code parties} threads must call {@link #incrementAndGet()} the
 * same number of times. In each "round" two cyclic barriers force every
 * thread to read the old value <i>before</i> any thread writes its result:
 * all threads therefore return the same value {@code v + 1}, where a real
 * counter would have returned distinct {@code v + 1 .. v + parties} values.
 * The duplicate return values make the recorded history provably
 * non-linearizable, deterministically (no timing-dependent flakiness).
 */
public final class GatedRacyCounter implements ConcurrentCounter {

    private final CyclicBarrier allRead;
    private final CyclicBarrier allComputed;
    private long value;

    public GatedRacyCounter(int parties) {
        this.allRead = new CyclicBarrier(parties);
        this.allComputed = new CyclicBarrier(parties);
    }

    @Override
    public long incrementAndGet() {
        long observed = value;
        await(allRead);
        long next = observed + 1;
        await(allComputed);
        value = next;
        return next;
    }

    @Override
    public long get() {
        return value;
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (BrokenBarrierException e) {
            throw new IllegalStateException(e);
        }
    }
}
