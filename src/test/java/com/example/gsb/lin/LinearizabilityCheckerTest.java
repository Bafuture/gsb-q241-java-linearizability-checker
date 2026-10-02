package com.example.gsb.lin;

import com.example.gsb.lin.spec.CounterSpec;
import com.example.gsb.lin.spec.QueueSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LinearizabilityCheckerTest {

    private static Operation op(int id, String process, long invoke, long response,
                                String method, Object ret, Object... args) {
        return Operation.of(id, process, invoke, response, method, ret, args);
    }

    @Test
    void sequentialQueueHistoryIsLinearizable() {
        History history = History.of(
                op(0, "P1", 0, 1, "enq", null, "x"),
                op(1, "P1", 2, 3, "enq", null, "y"),
                op(2, "P2", 4, 5, "deq", "x"));

        CheckResult result = Checker.checkLinearizable(history, new QueueSpec());

        assertThat(result.linearizable()).isTrue();
        assertThat(result.minimalConflict()).isEmpty();
        assertThat(result.witnessOrder()).hasSize(3);
        assertThat(result.witnessOrder().get(2).method()).isEqualTo("deq");
    }

    @Test
    void overlappingOperationsMayReorder() {
        // enq(x) spans [0,10]; enq(y) and deq()->y both happen while enq(x) is pending.
        // Legal linearization: enq(y), deq()->y, enq(x).
        History history = History.of(
                op(0, "P1", 0, 10, "enq", null, "x"),
                op(1, "P2", 1, 2, "enq", null, "y"),
                op(2, "P3", 3, 4, "deq", "y"));

        CheckResult result = Checker.checkLinearizable(history, new QueueSpec());

        assertThat(result.linearizable()).isTrue();
        // Any legal witness must linearize enq(y) before the deq that returned y.
        List<Operation> witness = result.witnessOrder();
        assertThat(witness).hasSize(3);
        int enqY = indexOf(witness, 1);
        int deq = indexOf(witness, 2);
        assertThat(enqY).isLessThan(deq);
    }

    private static int indexOf(List<Operation> witness, int id) {
        for (int i = 0; i < witness.size(); i++) {
            if (witness.get(i).id() == id) {
                return i;
            }
        }
        throw new AssertionError("op #" + id + " missing from witness");
    }

    @Test
    void counterHistoryWithOverlapIsLinearizable() {
        History history = History.of(
                op(0, "P1", 0, 4, "increment", 1L),
                op(1, "P2", 1, 5, "increment", 2L),
                op(2, "P3", 2, 3, "get", 1L),
                op(3, "P1", 6, 7, "get", 2L));

        CheckResult result = Checker.checkLinearizable(history, new CounterSpec());

        assertThat(result.linearizable()).isTrue();
        assertThat(result.witnessOrder()).hasSize(4);
    }

    @Test
    void nonLinearizableHistoryReportsMinimalConflict() {
        // enq(x) completes before enq(y), then deq() returns y, although x must be at head.
        History history = History.of(
                op(0, "P1", 0, 1, "enq", null, "x"),
                op(1, "P2", 2, 3, "enq", null, "y"),
                op(2, "P3", 4, 5, "deq", "y"));

        CheckResult result = Checker.checkLinearizable(history, new QueueSpec());

        assertThat(result.linearizable()).isFalse();
        assertThat(result.minimalConflict()).isNotEmpty();

        // The reported set is itself not linearizable.
        CheckResult conflictAsHistory = Checker.checkLinearizable(
                new History(result.minimalConflict()), new QueueSpec());
        assertThat(conflictAsHistory.linearizable()).isFalse();

        // 1-minimality: removing any single operation from the conflict set makes it
        // linearizable (or trivially empty).
        for (Operation removed : result.minimalConflict()) {
            List<Operation> reduced = new ArrayList<>(result.minimalConflict());
            reduced.remove(removed);
            assertThat(Checker.isLinearizable(new History(reduced), new QueueSpec()))
                    .as("conflict must be 1-minimal; removing %s should make it consistent", removed)
                    .isTrue();
        }
    }

    @Test
    void impossibleReturnValueHasSingletonConflict() {
        // deq()->y where y is never enqueued: the offending op alone explains the failure.
        History history = History.of(
                op(0, "P1", 0, 1, "enq", null, "x"),
                op(1, "P2", 2, 3, "deq", "y"));

        CheckResult result = Checker.checkLinearizable(history, new QueueSpec());

        assertThat(result.linearizable()).isFalse();
        assertThat(result.minimalConflict()).hasSize(1);
        assertThat(result.minimalConflict().get(0).id()).isEqualTo(1);
    }

    @Test
    void sequentiallyConsistentButNotLinearizable() {
        // enq(x) < enq(y) in real time, so linearizability forces deq()->x first.
        // Sequential consistency only preserves per-process order and allows the
        // serialization enq(y), deq()->y, enq(x), deq()->x.
        History history = History.of(
                op(0, "P1", 0, 1, "enq", null, "x"),
                op(1, "P2", 2, 3, "enq", null, "y"),
                op(2, "P3", 4, 5, "deq", "y"),
                op(3, "P3", 6, 7, "deq", "x"));

        CheckResult linearizable = Checker.checkLinearizable(history, new QueueSpec());
        CheckResult sequentiallyConsistent = Checker.checkSequentiallyConsistent(history, new QueueSpec());

        assertThat(linearizable.linearizable()).isFalse();
        assertThat(sequentiallyConsistent.consistent()).isTrue();
        // The SC witness must place enq(y) before the deq that returned y, which is
        // exactly the reordering that real-time order forbids.
        List<Operation> witness = sequentiallyConsistent.witnessOrder();
        assertThat(indexOf(witness, 1)).isLessThan(indexOf(witness, 2));
        assertThat(indexOf(witness, 0)).isLessThan(indexOf(witness, 3));
    }

    @Test
    void sequentialConsistencyRespectsProgramOrder() {
        // P1 enqueues x before y; P2 dequeues x before y. SC must honor both chains.
        History valid = History.of(
                op(0, "P1", 0, 1, "enq", null, "x"),
                op(1, "P1", 2, 3, "enq", null, "y"),
                op(2, "P2", 4, 5, "deq", "x"),
                op(3, "P2", 6, 7, "deq", "y"));
        assertThat(Checker.checkSequentiallyConsistent(valid, new QueueSpec()).consistent()).isTrue();

        // deq()->y before deq()->x violates P2's program order against the enq chain:
        // x is forced before y by P1, yet y must leave before x for P2.
        History invalid = History.of(
                op(0, "P1", 0, 1, "enq", null, "x"),
                op(1, "P1", 2, 3, "enq", null, "y"),
                op(2, "P2", 4, 5, "deq", "y"),
                op(3, "P2", 6, 7, "deq", "x"));
        assertThat(Checker.checkSequentiallyConsistent(invalid, new QueueSpec()).consistent()).isFalse();
    }

    @Test
    void pruningAndStatisticsAreRecorded() {
        // Eight increments with fully disjoint intervals: every node has one candidate.
        List<Operation> ops = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            ops.add(op(i, "P" + i, 2L * i, 2L * i + 1, "increment", (long) (i + 1)));
        }
        CheckResult result = Checker.checkLinearizable(new History(ops), new CounterSpec());

        assertThat(result.linearizable()).isTrue();
        CheckStats stats = result.stats();
        assertThat(stats.operationCount()).isEqualTo(8);
        assertThat(stats.durationNanos()).isGreaterThanOrEqualTo(0);
        assertThat(stats.nodesExplored()).isBetween(1L, 9L);
        assertThat(stats.prunedByPrecedence()).isGreaterThan(0);
    }

    @Test
    void returnValuePruningAndStatsOnFailingHistory() {
        // deq()->z while only x/y are enqueued: no ordering succeeds and every
        // branch that reaches deq is cut by return-value mismatch.
        History history = History.of(
                op(0, "P1", 0, 2, "enq", null, "x"),
                op(1, "P2", 1, 3, "enq", null, "y"),
                op(2, "P3", 1, 4, "deq", "z"));

        CheckResult result = Checker.checkLinearizable(history, new QueueSpec());

        assertThat(result.linearizable()).isFalse();
        assertThat(result.stats().prunedByReturnValue()).isGreaterThan(0);
        assertThat(result.stats().nodesExplored()).isGreaterThan(0);
    }
}
