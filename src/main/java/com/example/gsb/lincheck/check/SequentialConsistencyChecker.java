package com.example.gsb.lincheck.check;

import com.example.gsb.lincheck.model.History;
import com.example.gsb.lincheck.model.Operation;
import com.example.gsb.lincheck.spec.SequentialSpec;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sequential-consistency checker (Lamport): a history is sequentially
 * consistent iff there is a total order of its operations that
 * <ol>
 *   <li>respects <b>per-thread program order</b> only (operations of one
 *       thread appear in the order they were invoked; operations of different
 *       threads may be reordered even across real time), and</li>
 *   <li>is accepted by the sequential specification.</li>
 * </ol>
 *
 * <p>The only difference with {@link LinearizabilityChecker} is the
 * precedence relation: linearizability enforces global real-time precedence,
 * sequential consistency enforces only per-thread order. Hence every
 * linearizable history is also sequentially consistent, but not vice versa.
 *
 * <p>Search is memoized DFS over {@code (linearized set, abstract state)}.
 * An operation is eligible exactly when its predecessor within its own
 * thread has already been linearized, which makes the program-order
 * constraint pruning automatic.
 */
public final class SequentialConsistencyChecker<S> {

    private static final long DEFAULT_MAX_NODES = 5_000_000L;

    private final SequentialSpec<S> spec;
    private long maxNodes = DEFAULT_MAX_NODES;

    public SequentialConsistencyChecker(SequentialSpec<S> spec) {
        this.spec = spec;
    }

    public SequentialConsistencyChecker<S> withMaxNodes(long maxNodes) {
        this.maxNodes = maxNodes;
        return this;
    }

    public CheckReport check(History history) {
        Stats stats = new Stats();
        stats.operations(history.size());
        long start = System.nanoTime();
        Search search = new Search(history.operations(), stats);
        Verdict verdict;
        try {
            verdict = search.run() ? Verdict.CONSISTENT : Verdict.INCONSISTENT;
        } catch (BudgetExceeded e) {
            verdict = Verdict.INCONCLUSIVE;
        }
        stats.elapsedNanos(System.nanoTime() - start);
        List<Operation> witness = verdict == Verdict.CONSISTENT ? search.witness() : null;
        return new CheckReport("sequential-consistency", verdict, witness, null, stats);
    }

    private static final class BudgetExceeded extends RuntimeException {
        static final BudgetExceeded INSTANCE = new BudgetExceeded();

        private BudgetExceeded() {
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    private record Key(BitSet linearized, Object state) {
    }

    private final class Search {

        private final List<Operation> ops;
        private final int[] threadPredecessor;
        private final Stats stats;
        private final Set<Key> memo = new HashSet<>();
        private final Deque<Operation> linearization = new ArrayDeque<>();
        private List<Operation> witness;

        Search(List<Operation> ops, Stats stats) {
            this.ops = ops;
            this.stats = stats;
            this.threadPredecessor = new int[ops.size()];
            Map<String, List<Integer>> byThread = new LinkedHashMap<>();
            for (int i = 0; i < ops.size(); i++) {
                byThread.computeIfAbsent(ops.get(i).thread(), k -> new ArrayList<>()).add(i);
            }
            for (List<Integer> indices : byThread.values()) {
                indices.sort(Comparator.comparingLong(i -> ops.get(i).invocationTime()));
                for (int k = 0; k < indices.size(); k++) {
                    threadPredecessor[indices.get(k)] = k == 0 ? -1 : indices.get(k - 1);
                }
            }
        }

        boolean run() {
            return dfs(new BitSet(ops.size()), spec.initialState());
        }

        List<Operation> witness() {
            return witness;
        }

        private boolean dfs(BitSet linearized, S state) {
            stats.countNode();
            if (stats.nodes() > maxNodes) {
                throw BudgetExceeded.INSTANCE;
            }
            if (linearized.cardinality() == ops.size()) {
                witness = new ArrayList<>(linearization);
                return true;
            }
            Key key = new Key((BitSet) linearized.clone(), state);
            if (memo.contains(key)) {
                stats.countMemoHit();
                return false;
            }
            for (int j = 0; j < ops.size(); j++) {
                if (linearized.get(j)) {
                    continue;
                }
                int predecessor = threadPredecessor[j];
                if (predecessor >= 0 && !linearized.get(predecessor)) {
                    continue;
                }
                S next = spec.step(state, ops.get(j));
                if (next == null) {
                    stats.countSpecRejection();
                    continue;
                }
                linearized.set(j);
                linearization.addLast(ops.get(j));
                boolean ok = dfs(linearized, next);
                linearization.removeLast();
                linearized.clear(j);
                if (ok) {
                    return true;
                }
            }
            memo.add(key);
            return false;
        }
    }
}
