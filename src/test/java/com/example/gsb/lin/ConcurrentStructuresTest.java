package com.example.gsb.lin;

import com.example.gsb.lin.recorder.OperationRecorder;
import com.example.gsb.lin.recorder.RecordingCounter;
import com.example.gsb.lin.recorder.RecordingQueue;
import com.example.gsb.lin.spec.CounterSpec;
import com.example.gsb.lin.spec.QueueSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static java.time.Duration.ofSeconds;

/**
 * Runs the built-in concurrent counter/queue for real, converts the recorded
 * executions into histories, and feeds them to the checker.
 */
class ConcurrentStructuresTest {

    @Test
    void recordedCounterExecutionIsLinearizable() {
        assertTimeoutPreemptively(ofSeconds(30), () -> {
            RecordingCounter counter = new RecordingCounter(
                    new ConcurrentCounter(), new OperationRecorder());

            int threads = 4;
            int perThread = 10; // 40 operations, fits the 64-op bitmask
            CountDownLatch start = new CountDownLatch(1);
            List<Thread> workers = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                String process = "P" + t;
                Thread worker = new Thread(() -> {
                    awaitQuietly(start);
                    for (int i = 0; i < perThread; i++) {
                        counter.increment(process);
                        if (i % 3 == 0) {
                            counter.get(process);
                        }
                    }
                });
                workers.add(worker);
                worker.start();
            }
            start.countDown();
            for (Thread worker : workers) {
                worker.join();
            }

            History history = counter.history();
            int getsPerThread = (perThread + 2) / 3;
            assertThat(history.size()).isEqualTo(threads * (perThread + getsPerThread));

            CheckResult result = Checker.checkLinearizable(history, new CounterSpec());

            assertThat(result.linearizable())
                    .as("real AtomicLong execution must be linearizable")
                    .isTrue();
            assertThat(result.witnessOrder()).hasSize(history.size());
            assertThat(result.stats().nodesExplored())
                    .as("distinct increment results should force a near-unique path, got "
                            + result.stats())
                    .isLessThan((long) history.size() * 4);
        });
    }

    @Test
    void recordedQueueExecutionIsLinearizable() {
        assertTimeoutPreemptively(ofSeconds(30), () -> {
            RecordingQueue<String> queue = new RecordingQueue<>(
                    new ConcurrentQueue<>(), new OperationRecorder());

            int producers = 2;
            int perProducer = 5;
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch producersDone = new CountDownLatch(producers);
            ExecutorService pool = Executors.newFixedThreadPool(3);

            for (int t = 0; t < producers; t++) {
                String process = "P" + t;
                pool.submit(() -> {
                    awaitQuietly(start);
                    for (int i = 0; i < perProducer; i++) {
                        queue.enq(process, process + "-" + i);
                    }
                    producersDone.countDown();
                });
            }
            Thread consumer = new Thread(() -> {
                awaitQuietly(start);
                int consumed = 0;
                int emptyDeqs = 0;
                // Bounded attempts with backoff: empty deqs are real returned values
                // and must be checked too, but the history is limited to 64 operations.
                while (consumed < producers * perProducer && emptyDeqs < 40) {
                    String item = queue.deq("C");
                    if (item != null) {
                        consumed++;
                    } else {
                        emptyDeqs++;
                        java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
                    }
                }
                assertThat(consumed).isEqualTo(producers * perProducer);
            });
            consumer.start();
            start.countDown();
            producersDone.await();
            consumer.join();
            pool.shutdown();

            History history = queue.history();
            assertThat(history.size()).isLessThanOrEqualTo(History.MAX_OPERATIONS);
            CheckResult result = Checker.checkLinearizable(history, new QueueSpec());

            assertThat(result.linearizable())
                    .as("real ConcurrentLinkedQueue execution must be linearizable; stats=%s",
                            result.stats())
                    .isTrue();
            assertThat(result.witnessOrder()).hasSize(history.size());
        });
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
