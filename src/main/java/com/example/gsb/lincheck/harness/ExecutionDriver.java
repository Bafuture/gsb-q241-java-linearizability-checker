package com.example.gsb.lincheck.harness;

import com.example.gsb.lincheck.model.History;

import java.util.concurrent.CountDownLatch;

/**
 * Runs a workload on {@code n} real threads and returns the recorded history.
 * All threads are released by a single start latch so the operations genuinely
 * overlap; every operation executed through the shared
 * {@link HistoryRecorder} is timestamped with {@code System.nanoTime()}.
 */
public final class ExecutionDriver {

    @FunctionalInterface
    public interface Workload {
        void run(int threadIndex, String threadName, HistoryRecorder recorder) throws Exception;
    }

    private ExecutionDriver() {
    }

    public static History run(int threads, Workload workload) {
        HistoryRecorder recorder = new HistoryRecorder();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        Throwable[] failure = new Throwable[1];
        for (int i = 0; i < threads; i++) {
            final int index = i;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    workload.run(index, "T" + index, recorder);
                } catch (Throwable t) {
                    failure[0] = t;
                } finally {
                    done.countDown();
                }
            }, "workload-T" + i);
            thread.setDaemon(true);
            thread.start();
        }
        start.countDown();
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        if (failure[0] != null) {
            throw new IllegalStateException("workload thread failed", failure[0]);
        }
        return recorder.history();
    }
}
