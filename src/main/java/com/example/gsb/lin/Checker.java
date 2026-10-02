package com.example.gsb.lin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Linearizability / sequential-consistency checker.
 *
 * <h2>Algorithm</h2>
 * Backtracking search in the style of Wing &amp; Gong (1993):
 * <ol>
 *   <li><b>Precedence constraints.</b> For {@link ConsistencyModel#LINEARIZABLE},
 *       operation {@code a} must precede {@code b} whenever
 *       {@code a.response < b.invoke} (real-time order). For
 *       {@link ConsistencyModel#SEQUENTIALLY_CONSISTENT}, {@code a} must precede
 *       {@code b} whenever both belong to the same process and {@code a} was issued
 *       first (program order).</li>
 *   <li><b>Interval-constraint pruning.</b> At every search node only operations whose
 *       predecessors have all been linearized are tried as the next step, instead of
 *       enumerating all {@code n!} permutations.</li>
 *   <li><b>Return-value pruning.</b> A candidate is executed against the sequential
 *       spec; if the spec's return value differs from the recorded one, the branch is
 *       cut immediately.</li>
 *   <li><b>Memoization.</b> Failed {@code (spec state, linearized set)} pairs are
 *       cached, so each such pair is explored at most once.</li>
 * </ol>
 *
 * <h2>Complexity</h2>
 * Worst case is bounded by the subset lattice: {@code O(2^n * n)} spec steps with
 * memoization (versus {@code O(n!)} for naive permutation enumeration). For histories
 * whose intervals mostly do not overlap, precedence pruning leaves a single candidate
 * per node and the search is effectively {@code O(n)}. The counterexample minimization
 * performs {@code O(n)} additional sub-checks (greedy 1-minimal deletion).
 */
public final class Checker {

    private Checker() {
    }

    public static <S> CheckResult checkLinearizable(History history, SequentialSpec<S> spec) {
        return check(history, spec, ConsistencyModel.LINEARIZABLE);
    }

    public static <S> CheckResult checkSequentiallyConsistent(History history, SequentialSpec<S> spec) {
        return check(history, spec, ConsistencyModel.SEQUENTIALLY_CONSISTENT);
    }

    public static <S> boolean isLinearizable(History history, SequentialSpec<S> spec) {
        return checkLinearizable(history, spec).consistent();
    }

    public static <S> CheckResult check(History history, SequentialSpec<S> spec, ConsistencyModel model) {
        List<Operation> ops = new ArrayList<>(history.operations());
        ops.sort(java.util.Comparator.comparingLong(Operation::invoke).thenComparingInt(Operation::id));

        long[] predecessors = switch (model) {
            case LINEARIZABLE -> realTimePrecedence(ops);
            case SEQUENTIALLY_CONSISTENT -> programOrderPrecedence(ops);
        };

        Engine<S> engine = new Engine<>(ops, spec, predecessors);
        long started = System.nanoTime();
        List<Operation> witness = engine.search(spec.initialState(), 0L);
        long duration = System.nanoTime() - started;

        CheckStats stats = engine.stats(ops.size(), duration);
        if (witness != null) {
            return new CheckResult(true, model, witness, List.of(), stats);
        }
        List<Operation> conflict = minimalConflict(ops, spec, model);
        return new CheckResult(false, model, List.of(), conflict, stats);
    }

    /** a precedes b iff a.response < b.invoke. */
    private static long[] realTimePrecedence(List<Operation> ops) {
        long[] pred = new long[ops.size()];
        for (int i = 0; i < ops.size(); i++) {
            for (int j = 0; j < ops.size(); j++) {
                if (i != j && ops.get(j).precedes(ops.get(i))) {
                    pred[i] |= 1L << j;
                }
            }
        }
        return pred;
    }

    /** a precedes b iff same process and a issued before b (transitively closed via chain). */
    private static long[] programOrderPrecedence(List<Operation> ops) {
        long[] pred = new long[ops.size()];
        Map<String, Integer> lastIndexPerProcess = new LinkedHashMap<>();
        for (int i = 0; i < ops.size(); i++) {
            Integer previous = lastIndexPerProcess.put(ops.get(i).process(), i);
            if (previous != null) {
                pred[i] |= 1L << previous;
            }
        }
        return pred;
    }

    /**
     * Greedy 1-minimal conflict: repeatedly drop any operation whose removal keeps the
     * sub-history inconsistent. The result is still inconsistent, but removing any
     * single remaining operation makes it consistent.
     */
    private static <S> List<Operation> minimalConflict(List<Operation> ops, SequentialSpec<S> spec,
                                                       ConsistencyModel model) {
        List<Operation> current = new ArrayList<>(ops);
        boolean removedOne = true;
        while (removedOne) {
            removedOne = false;
            for (int i = 0; i < current.size(); i++) {
                List<Operation> trial = new ArrayList<>(current);
                trial.remove(i);
                if (!check(new History(trial), spec, model).consistent()) {
                    current = trial; // still inconsistent without it: drop and re-scan
                    removedOne = true;
                    break;
                }
            }
        }
        return current;
    }

    private static final class Engine<S> {
        private final List<Operation> ops;
        private final SequentialSpec<S> spec;
        private final long[] pred;
        private final long fullMask;
        private final Set<MemoKey> failed = new HashSet<>();

        private long nodes;
        private long prunedByPrecedence;
        private long prunedByReturnValue;
        private long memoHits;

        Engine(List<Operation> ops, SequentialSpec<S> spec, long[] pred) {
            this.ops = ops;
            this.spec = spec;
            this.pred = pred;
            this.fullMask = ops.isEmpty() ? 0L : -1L >>> (64 - ops.size());
        }

        /** @return a legal linearization, or {@code null} if none exists */
        List<Operation> search(S state, long done) {
            nodes++;
            if (done == fullMask) {
                return new ArrayList<>();
            }
            MemoKey key = new MemoKey(done, state);
            if (failed.contains(key)) {
                memoHits++;
                return null;
            }
            long remaining = fullMask & ~done;
            long available = 0L;
            for (long bits = remaining; bits != 0; bits &= bits - 1) {
                int i = Long.numberOfTrailingZeros(bits);
                if ((pred[i] & ~done) == 0) {
                    available |= 1L << i;
                }
            }
            prunedByPrecedence += Long.bitCount(remaining) - Long.bitCount(available);

            for (long bits = available; bits != 0; bits &= bits - 1) {
                int i = Long.numberOfTrailingZeros(bits);
                Operation op = ops.get(i);
                SequentialSpec.StepResult<S> result = spec.step(state, op);
                if (!Objects.equals(result.returnValue(), op.returnValue())) {
                    prunedByReturnValue++;
                    continue;
                }
                List<Operation> rest = search(result.nextState(), done | (1L << i));
                if (rest != null) {
                    rest.add(0, op);
                    return rest;
                }
            }
            failed.add(key);
            return null;
        }

        CheckStats stats(int opCount, long durationNanos) {
            return new CheckStats(opCount, nodes, prunedByPrecedence, prunedByReturnValue,
                    memoHits, durationNanos);
        }
    }

    private record MemoKey(long done, Object state) {
    }
}
