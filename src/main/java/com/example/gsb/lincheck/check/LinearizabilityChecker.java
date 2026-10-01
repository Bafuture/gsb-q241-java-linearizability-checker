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
import java.util.List;
import java.util.Set;

/**
 * Linearizability checker (Herlihy&amp;Wing): a history is linearizable iff
 * there is a total order of its operations that
 * <ol>
 *   <li>respects real-time precedence (op a precedes op b whenever
 *       {@code a.responseTime < b.invocationTime}), and</li>
 *   <li>is accepted by the sequential specification, reproducing every
 *       observed result.</li>
 * </ol>
 *
 * <p><b>Algorithm.</b> Instead of enumerating all n! permutations, the checker
 * sweeps the 2n call/return events in time order (Wing&amp;Gong style). An
 * operation may only be linearized while it is active, i.e. after its call
 * event and no later than its return event; this interval constraint prunes
 * every permutation that violates real-time precedence before it is ever
 * materialized. Search states are memoized on the triple
 * {@code (event position, set of called-but-unlinearized operations, abstract
 * spec state)}: two different linearization prefixes leading to the same
 * triple have identical futures, so the second visit is pruned (counted as a
 * memo hit). Candidate steps rejected by the spec are pruned as well.
 *
 * <p><b>Complexity.</b> The number of distinct memo keys is bounded by
 * {@code 2n * 2^c * |S|}, where {@code c} is the maximum number of
 * simultaneously active operations (contention) and {@code |S|} the number of
 * reachable abstract states; each key is expanded at most once. Hence the
 * checker is near-linear in n for low-contention histories, while the general
 * problem remains worst-case exponential (linearizability checking is
 * NP-hard). A configurable node budget guards pathological inputs and yields
 * {@link Verdict#INCONCLUSIVE}.
 *
 * <p>On inconsistency the checker extracts a 1-minimal conflict set with a
 * delta-debugging (ddmin) pass, see {@link ConflictMinimizer}.
 */
public final class LinearizabilityChecker<S> {

    private static final long DEFAULT_MAX_NODES = 5_000_000L;

    private final SequentialSpec<S> spec;
    private long maxNodes = DEFAULT_MAX_NODES;
    private boolean conflictMinimization = true;

    public LinearizabilityChecker(SequentialSpec<S> spec) {
        this.spec = spec;
    }

    /** Maximum number of search nodes before the check gives up as inconclusive. */
    public LinearizabilityChecker<S> withMaxNodes(long maxNodes) {
        this.maxNodes = maxNodes;
        return this;
    }

    /** Whether to run the ddmin conflict minimizer on inconsistent histories. */
    public LinearizabilityChecker<S> withConflictMinimization(boolean enabled) {
        this.conflictMinimization = enabled;
        return this;
    }

    public CheckReport check(History history) {
        return checkInternal(history, conflictMinimization);
    }

    private CheckReport checkInternal(History history, boolean minimize) {
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
        List<Operation> conflict = null;
        if (verdict == Verdict.INCONSISTENT && minimize) {
            conflict = ConflictMinimizer.minimize(history,
                    sub -> checkInternal(sub, false).verdict() == Verdict.INCONSISTENT);
        }
        return new CheckReport("linearizability", verdict, witness, conflict, stats);
    }

    /** Thrown when the node budget is exhausted; allocation-free singleton. */
    private static final class BudgetExceeded extends RuntimeException {
        static final BudgetExceeded INSTANCE = new BudgetExceeded();

        private BudgetExceeded() {
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    private record Event(long time, boolean invoke, int op) {
    }

    private record Key(int position, BitSet calls, Object state) {
    }

    private final class Search {

        private final List<Operation> ops;
        private final int[] eventOp;
        private final boolean[] eventInvoke;
        private final Stats stats;
        private final Set<Key> memo = new HashSet<>();
        private final Deque<Operation> linearization = new ArrayDeque<>();
        private List<Operation> witness;

        Search(List<Operation> ops, Stats stats) {
            this.ops = ops;
            this.stats = stats;
            List<Event> events = new ArrayList<>(ops.size() * 2);
            for (int i = 0; i < ops.size(); i++) {
                Operation op = ops.get(i);
                events.add(new Event(op.invocationTime(), true, i));
                events.add(new Event(op.responseTime(), false, i));
            }
            // Calls before returns at equal timestamps: operations with
            // touching intervals are concurrent, and a zero-length operation
            // must be allowed to linearize at its single instant.
            events.sort(Comparator.comparingLong(Event::time)
                    .thenComparing(e -> e.invoke() ? 0 : 1));
            eventOp = new int[events.size()];
            eventInvoke = new boolean[events.size()];
            for (int i = 0; i < events.size(); i++) {
                eventOp[i] = events.get(i).op();
                eventInvoke[i] = events.get(i).invoke();
            }
        }

        boolean run() {
            return dfs(0, new BitSet(ops.size()), spec.initialState());
        }

        List<Operation> witness() {
            return witness;
        }

        /**
         * @param position index of the next unprocessed event
         * @param calls    operations that were called but not yet linearized
         * @param state    abstract state after the linearized prefix
         */
        private boolean dfs(int position, BitSet calls, S state) {
            stats.countNode();
            if (stats.nodes() > maxNodes) {
                throw BudgetExceeded.INSTANCE;
            }
            if (position == eventOp.length) {
                if (calls.isEmpty()) {
                    witness = new ArrayList<>(linearization);
                    return true;
                }
                return false;
            }
            Key key = new Key(position, (BitSet) calls.clone(), state);
            if (memo.contains(key)) {
                stats.countMemoHit();
                return false;
            }
            boolean ok;
            int op = eventOp[position];
            if (eventInvoke[position]) {
                // Call event: the operation becomes available for linearization.
                calls.set(op);
                ok = dfs(position + 1, calls, state);
                calls.clear(op);
            } else if (!calls.get(op)) {
                // Return event of an already-linearized operation: either move
                // on, or linearize more still-active operations at this instant.
                ok = dfs(position + 1, calls, state);
                if (!ok) {
                    ok = linearizeOnePending(position, calls, state);
                }
            } else {
                // Return event of a still-pending operation: it (and possibly
                // other active operations) must be linearized now, before its
                // interval closes.
                ok = linearizeOnePending(position, calls, state);
            }
            if (!ok) {
                memo.add(key);
            }
            return ok;
        }

        /** Tries linearizing one pending operation at the current event position. */
        private boolean linearizeOnePending(int position, BitSet calls, S state) {
            for (int j = calls.nextSetBit(0); j >= 0; j = calls.nextSetBit(j + 1)) {
                S next = spec.step(state, ops.get(j));
                if (next == null) {
                    stats.countSpecRejection();
                    continue;
                }
                calls.clear(j);
                linearization.addLast(ops.get(j));
                boolean ok = dfs(position, calls, next);
                linearization.removeLast();
                calls.set(j);
                if (ok) {
                    return true;
                }
            }
            return false;
        }
    }
}
