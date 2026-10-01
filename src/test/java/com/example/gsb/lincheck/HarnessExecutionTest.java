package com.example.gsb.lincheck;

import com.example.gsb.lincheck.check.CheckReport;
import com.example.gsb.lincheck.check.LinearizabilityChecker;
import com.example.gsb.lincheck.check.SequentialConsistencyChecker;
import com.example.gsb.lincheck.check.Verdict;
import com.example.gsb.lincheck.harness.ExecutionDriver;
import com.example.gsb.lincheck.harness.GatedRacyCounter;
import com.example.gsb.lincheck.harness.SynchronizedCounter;
import com.example.gsb.lincheck.harness.SynchronizedQueue;
import com.example.gsb.lincheck.model.History;
import com.example.gsb.lincheck.model.Operation;
import com.example.gsb.lincheck.spec.CounterSpec;
import com.example.gsb.lincheck.spec.QueueSpec;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessExecutionTest {

    @Test
    void synchronizedCounterHistoryIsLinearizable() {
        SynchronizedCounter counter = new SynchronizedCounter();
        History history = ExecutionDriver.run(4, (index, name, recorder) -> {
            for (int i = 0; i < 20; i++) {
                recorder.record(name, "inc", null, counter::incrementAndGet);
            }
        });

        assertThat(history.size()).isEqualTo(80);
        for (Operation operation : history.operations()) {
            assertThat(operation.responseTime())
                    .as("real timestamps recorded")
                    .isGreaterThanOrEqualTo(operation.invocationTime());
        }

        CheckReport lin = new LinearizabilityChecker<>(new CounterSpec()).check(history);
        CheckReport sc = new SequentialConsistencyChecker<>(new CounterSpec()).check(history);

        assertThat(lin.verdict()).isEqualTo(Verdict.CONSISTENT);
        assertThat(lin.witnessOrder()).hasSize(80);
        assertThat(sc.verdict()).isEqualTo(Verdict.CONSISTENT);

        Set<Long> results = new HashSet<>();
        for (Operation operation : history.operations()) {
            results.add((Long) operation.result());
        }
        assertThat(results).hasSize(80);
        for (long v = 1; v <= 80; v++) {
            assertThat(results).contains(v);
        }
    }

    @Test
    void synchronizedQueueHistoryIsLinearizable() {
        SynchronizedQueue queue = new SynchronizedQueue();
        History history = ExecutionDriver.run(3, (index, name, recorder) -> {
            recorder.recordVoid(name, "enq", name + "-a", () -> queue.enq(name + "-a"));
            recorder.recordVoid(name, "enq", name + "-b", () -> queue.enq(name + "-b"));
            recorder.record(name, "deq", null, queue::deq);
            recorder.record(name, "deq", null, queue::deq);
        });

        assertThat(history.size()).isEqualTo(12);

        CheckReport report = new LinearizabilityChecker<>(new QueueSpec()).check(history);

        assertThat(report.verdict()).isEqualTo(Verdict.CONSISTENT);
        assertThat(report.witnessOrder()).hasSize(12);
    }

    @Test
    void racyCounterHistoryIsReportedWithTwoOperationCounterexample() {
        GatedRacyCounter racy = new GatedRacyCounter(4);
        History history = ExecutionDriver.run(4, (index, name, recorder) -> {
            recorder.record(name, "inc", null, racy::incrementAndGet);
            recorder.record(name, "inc", null, racy::incrementAndGet);
        });

        assertThat(history.size()).isEqualTo(8);

        CheckReport report = new LinearizabilityChecker<>(new CounterSpec())
                .withMaxNodes(1_000_000)
                .check(history);

        assertThat(report.verdict()).isEqualTo(Verdict.INCONSISTENT);
        assertThat(report.minimalConflict()).hasSize(2);
        assertThat(report.minimalConflict())
                .allSatisfy(o -> assertThat(o.method()).isEqualTo("inc"));
        Object firstResult = report.minimalConflict().get(0).result();
        assertThat(report.minimalConflict().get(1).result())
                .as("two increments returning the same value cannot be ordered")
                .isEqualTo(firstResult);

        CheckReport sc = new SequentialConsistencyChecker<>(new CounterSpec()).check(history);
        assertThat(sc.verdict()).isEqualTo(Verdict.INCONSISTENT);
    }
}
