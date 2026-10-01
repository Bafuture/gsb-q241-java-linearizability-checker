package com.example.gsb.lincheck;

import com.example.gsb.lincheck.check.CheckReport;
import com.example.gsb.lincheck.check.LinearizabilityChecker;
import com.example.gsb.lincheck.check.SequentialConsistencyChecker;
import com.example.gsb.lincheck.check.Verdict;
import com.example.gsb.lincheck.model.History;
import com.example.gsb.lincheck.model.Operation;
import com.example.gsb.lincheck.spec.CounterSpec;
import com.example.gsb.lincheck.spec.QueueSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SequentialConsistencyCheckerTest {

    private static Operation op(int id, String thread, String method, Object arg, Object result,
                                long invocation, long response) {
        return new Operation(id, thread, method, arg, result, invocation, response);
    }

    /**
     * Classic Herlihy-Wing style history: A enqueues x and finishes before B
     * enqueues y, yet A later dequeues y. Sequentially consistent (enqueue y
     * can be ordered first while respecting per-thread program order) but not
     * linearizable (real time forces enq(x) before enq(y), while deq -> y
     * forces y ahead of x).
     */
    @Test
    void sequentiallyConsistentButNotLinearizable() {
        History history = new History(List.of(
                op(0, "A", "enq", "x", null, 1, 2),
                op(1, "B", "enq", "y", null, 3, 4),
                op(2, "A", "deq", null, "y", 5, 6)));

        CheckReport sc = new SequentialConsistencyChecker<>(new QueueSpec()).check(history);
        CheckReport lin = new LinearizabilityChecker<>(new QueueSpec()).check(history);

        assertThat(sc.verdict()).isEqualTo(Verdict.CONSISTENT);
        // Witness: enq(y), enq(x), deq() -> y.
        assertThat(sc.witnessOrder().get(0).argument()).isEqualTo("y");
        assertThat(sc.witnessOrder().get(1).argument()).isEqualTo("x");
        assertThat(sc.witnessOrder().get(2).result()).isEqualTo("y");

        assertThat(lin.verdict()).isEqualTo(Verdict.INCONSISTENT);
        assertMinimalConflictIsOneMinimal(history, lin.minimalConflict());
    }

    /**
     * The reported conflict set must be inconsistent on its own, and removing
     * any single operation from it must make it consistent (1-minimality).
     */
    static void assertMinimalConflictIsOneMinimal(History original, List<Operation> conflict) {
        assertThat(conflict).isNotEmpty();
        assertThat(original.operations()).containsAll(conflict);
        LinearizabilityChecker<List<Object>> checker =
                new LinearizabilityChecker<>(new QueueSpec()).withConflictMinimization(false);
        assertThat(checker.check(new History(conflict)).verdict())
                .as("conflict set is itself inconsistent")
                .isEqualTo(Verdict.INCONSISTENT);
        for (Operation operation : conflict) {
            List<Operation> rest = new ArrayList<>(conflict);
            rest.remove(operation);
            assertThat(checker.check(new History(rest)).verdict())
                    .as("removing %s makes the conflict set consistent", operation)
                    .isEqualTo(Verdict.CONSISTENT);
        }
    }

    @Test
    void linearizableHistoriesAreAlsoSequentiallyConsistent() {
        History history = new History(List.of(
                op(0, "T0", "inc", null, 1L, 1, 10),
                op(1, "T1", "inc", null, 2L, 2, 5),
                op(2, "T2", "get", null, 2L, 6, 7)));

        CheckReport lin = new LinearizabilityChecker<>(new CounterSpec()).check(history);
        CheckReport sc = new SequentialConsistencyChecker<>(new CounterSpec()).check(history);

        assertThat(lin.verdict()).isEqualTo(Verdict.CONSISTENT);
        assertThat(sc.verdict()).isEqualTo(Verdict.CONSISTENT);
    }

    @Test
    void historyViolatingProgramOrderIsNotSequentiallyConsistent() {
        // Single thread observes 0 then 1 although no increment ever happened.
        History history = new History(List.of(
                op(0, "A", "get", null, 0L, 1, 2),
                op(1, "A", "get", null, 1L, 3, 4)));

        CheckReport sc = new SequentialConsistencyChecker<>(new CounterSpec()).check(history);

        assertThat(sc.verdict()).isEqualTo(Verdict.INCONSISTENT);
    }
}
