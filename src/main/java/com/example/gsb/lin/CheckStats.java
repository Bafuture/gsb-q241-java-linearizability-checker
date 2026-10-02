package com.example.gsb.lin;

/**
 * Search statistics of a single check.
 *
 * @param operationCount     number of operations in the history
 * @param nodesExplored      search nodes visited (one per recursive call)
 * @param prunedByPrecedence candidate operations skipped because real-time/program
 *                           order predecessors were not yet linearized
 * @param prunedByReturnValue candidate operations skipped because the spec's return
 *                           value did not match the recorded one
 * @param memoHits           nodes cut because the same (state, linearized-set) was
 *                           already proven to fail
 * @param durationNanos      wall-clock time of the search
 */
public record CheckStats(int operationCount, long nodesExplored, long prunedByPrecedence,
                         long prunedByReturnValue, long memoHits, long durationNanos) {

    public long pruned() {
        return prunedByPrecedence + prunedByReturnValue;
    }

    @Override
    public String toString() {
        return "CheckStats{ops=" + operationCount
                + ", nodes=" + nodesExplored
                + ", pruned=" + pruned()
                + " (precedence=" + prunedByPrecedence
                + ", returnValue=" + prunedByReturnValue + ")"
                + ", memoHits=" + memoHits
                + ", durationNanos=" + durationNanos + '}';
    }
}
