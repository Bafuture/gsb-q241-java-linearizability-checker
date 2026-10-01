package com.example.gsb.lincheck.check;

/**
 * Search statistics of a single check run.
 */
public final class Stats {

    private int operations;
    private long nodes;
    private long memoHits;
    private long specRejections;
    private long elapsedNanos;

    /** Number of operations in the checked history. */
    public int operations() {
        return operations;
    }

    /** Number of search nodes (DFS states) entered. */
    public long nodes() {
        return nodes;
    }

    /** Number of times a state was pruned because it had already been proven unproductive. */
    public long memoHits() {
        return memoHits;
    }

    /** Number of candidate linearization steps rejected by the sequential specification. */
    public long specRejections() {
        return specRejections;
    }

    /** Wall-clock time of the check, in nanoseconds. */
    public long elapsedNanos() {
        return elapsedNanos;
    }

    void operations(int operations) {
        this.operations = operations;
    }

    void countNode() {
        nodes++;
    }

    void countMemoHit() {
        memoHits++;
    }

    void countSpecRejection() {
        specRejections++;
    }

    void elapsedNanos(long elapsedNanos) {
        this.elapsedNanos = elapsedNanos;
    }

    @Override
    public String toString() {
        return "Stats{operations=" + operations
                + ", nodes=" + nodes
                + ", memoHits=" + memoHits
                + ", specRejections=" + specRejections
                + ", elapsedNanos=" + elapsedNanos + '}';
    }
}
