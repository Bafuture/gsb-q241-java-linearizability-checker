package com.example.gsb.lincheck;

import com.example.gsb.lincheck.check.CheckReport;
import com.example.gsb.lincheck.check.LinearizabilityChecker;
import com.example.gsb.lincheck.check.Verdict;
import com.example.gsb.lincheck.model.History;
import com.example.gsb.lincheck.model.Operation;
import com.example.gsb.lincheck.spec.CounterSpec;
import com.example.gsb.lincheck.spec.QueueSpec;
import com.example.gsb.lincheck.spec.SequentialSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LinearizabilityCheckerTest {

    private static Operation op(int id, String thread, String method, Object arg, Object result,
                                long invocation, long response) {
        return new Operation(id, thread, method, arg, result, invocation, response);
    }

    private static <S> void assertValidLinearization(History history, List<Operation> witness,
                                                     SequentialSpec<S> spec) {
        assertThat(witness).containsExactlyInAnyOrderElementsOf(history.operations());
        for (int i = 0; i < witness.size(); i++) {
            for (int j = i + 1; j < witness.size(); j++) {
                assertThat(witness.get(j).precedes(witness.get(i)))
                        .as("witness order must respect real-time precedence: %s before %s",
                                witness.get(i), witness.get(j))
                        .isFalse();
            }
        }
        S state = spec.initialState();
        for (Operation operation : witness) {
            state = spec.step(state, operation);
            assertThat(state).as("spec must accept %s", operation).isNotNull();
        }
    }

    @Test
    void linearizableCounterHistoryPasses() {
        History history = new History(List.of(
                op(0, "T0", "inc", null, 1L, 1, 10),
                op(1, "T1", "inc", null, 2L, 2, 5),
                op(2, "T2", "get", null, 2L, 6, 7)));

        CheckReport report = new LinearizabilityChecker<>(new CounterSpec()).check(history);

        assertThat(report.verdict()).isEqualTo(Verdict.CONSISTENT);
        assertThat(report.isConsistent()).isTrue();
        assertValidLinearization(history, report.witnessOrder(), new CounterSpec());
    }

    @Test
    void linearizableQueueHistoryPasses() {
        History history = new History(List.of(
                op(0, "T0", "enq", "x", null, 1, 10),
                op(1, "T1", "enq", "y", null, 2, 11),
                op(2, "T2", "deq", null, "x", 3, 4),
                op(3, "T3", "deq", null, "y", 5, 6)));

        CheckReport report = new LinearizabilityChecker<>(new QueueSpec()).check(history);

        assertThat(report.verdict()).isEqualTo(Verdict.CONSISTENT);
        assertValidLinearization(history, report.witnessOrder(), new QueueSpec());
    }

    @Test
    void nonLinearizableHistoryReportsMinimalConflict() {
        // inc returns 1 and completes; afterwards get observes 0: impossible.
        History history = new History(List.of(
                op(0, "T0", "inc", null, 1L, 1, 2),
                op(1, "T1", "get", null, 0L, 3, 4)));

        CheckReport report = new LinearizabilityChecker<>(new CounterSpec()).check(history);

        assertThat(report.verdict()).isEqualTo(Verdict.INCONSISTENT);
        assertThat(report.minimalConflict()).hasSize(2);
        assertThat(report.minimalConflict()).containsExactlyInAnyOrderElementsOf(history.operations());
        assertThat(report.describe()).contains("counterexample");
    }

    @Test
    void conflictMinimizerIsolatesTheGuiltyOperations() {
        // The contradiction is between op 0 and op 1; ops 2 and 3 are innocent noise.
        History history = new History(List.of(
                op(0, "T0", "inc", null, 1L, 1, 2),
                op(1, "T1", "get", null, 0L, 3, 100),
                op(2, "T2", "get", null, 1L, 4, 101),
                op(3, "T3", "get", null, 1L, 5, 102)));

        CheckReport report = new LinearizabilityChecker<>(new CounterSpec()).check(history);

        assertThat(report.verdict()).isEqualTo(Verdict.INCONSISTENT);
        assertThat(report.minimalConflict()).hasSize(2);
        assertThat(report.minimalConflict())
                .anySatisfy(o -> assertThat(o.method()).isEqualTo("inc"))
                .anySatisfy(o -> {
                    assertThat(o.method()).isEqualTo("get");
                    assertThat(o.result()).isEqualTo(0L);
                });

        // The conflict set is itself inconsistent and 1-minimal.
        LinearizabilityChecker<Long> checker =
                new LinearizabilityChecker<>(new CounterSpec()).withConflictMinimization(false);
        assertThat(checker.check(new History(report.minimalConflict())).verdict())
                .isEqualTo(Verdict.INCONSISTENT);
        for (Operation operation : report.minimalConflict()) {
            List<Operation> rest = new java.util.ArrayList<>(report.minimalConflict());
            rest.remove(operation);
            assertThat(checker.check(new History(rest)).verdict())
                    .isEqualTo(Verdict.CONSISTENT);
        }
    }

    @Test
    void memoizationPrunesEquivalentSearchStates() {
        // Same history as above: several interleavings of the get operations
        // converge on identical (position, pending calls, state) triples.
        History history = new History(List.of(
                op(0, "T0", "inc", null, 1L, 1, 2),
                op(1, "T1", "get", null, 0L, 3, 100),
                op(2, "T2", "get", null, 1L, 4, 101),
                op(3, "T3", "get", null, 1L, 5, 102)));

        CheckReport report = new LinearizabilityChecker<>(new CounterSpec())
                .withConflictMinimization(false)
                .check(history);

        assertThat(report.verdict()).isEqualTo(Verdict.INCONSISTENT);
        assertThat(report.stats().memoHits()).isGreaterThanOrEqualTo(1);
        assertThat(report.stats().specRejections()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void intervalPruningAvoidsFullPermutationSearch() {
        // Six mutually overlapping increments returning 1..6: 6! = 720 naive
        // permutations, but the interval-constrained search needs only a
        // handful of nodes.
        History history = new History(List.of(
                op(0, "T0", "inc", null, 1L, 1, 12),
                op(1, "T1", "inc", null, 2L, 2, 11),
                op(2, "T2", "inc", null, 3L, 3, 10),
                op(3, "T3", "inc", null, 4L, 4, 9),
                op(4, "T4", "inc", null, 5L, 5, 8),
                op(5, "T5", "inc", null, 6L, 6, 7)));

        CheckReport report = new LinearizabilityChecker<>(new CounterSpec()).check(history);

        assertThat(report.verdict()).isEqualTo(Verdict.CONSISTENT);
        assertThat(report.stats().nodes()).isLessThan(100);
        assertValidLinearization(history, report.witnessOrder(), new CounterSpec());
    }

    @Test
    void statisticsAreReported() {
        History history = new History(List.of(
                op(0, "T0", "inc", null, 1L, 1, 2),
                op(1, "T1", "get", null, 1L, 3, 4)));

        CheckReport report = new LinearizabilityChecker<>(new CounterSpec()).check(history);

        assertThat(report.stats().operations()).isEqualTo(2);
        assertThat(report.stats().nodes()).isGreaterThan(0);
        assertThat(report.stats().elapsedNanos()).isGreaterThanOrEqualTo(0);
        assertThat(report.describe())
                .contains("operations=2")
                .contains("nodes=")
                .contains("memoHits=");
    }

    @Test
    void nodeBudgetYieldsInconclusive() {
        History history = new History(List.of(
                op(0, "T0", "inc", null, 1L, 1, 10),
                op(1, "T1", "inc", null, 2L, 2, 11)));

        CheckReport report = new LinearizabilityChecker<>(new CounterSpec())
                .withMaxNodes(1)
                .check(history);

        assertThat(report.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
    }
}
