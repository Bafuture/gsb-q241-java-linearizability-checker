package com.example.gsb.lincheck.check;

import com.example.gsb.lincheck.model.Operation;

import java.util.List;

/**
 * Result of a consistency check: verdict, a witness ordering (when
 * consistent), a minimal conflict set (when inconsistent) and search
 * statistics.
 */
public final class CheckReport {

    private final String criterion;
    private final Verdict verdict;
    private final List<Operation> witnessOrder;
    private final List<Operation> minimalConflict;
    private final Stats stats;

    CheckReport(String criterion, Verdict verdict, List<Operation> witnessOrder,
                List<Operation> minimalConflict, Stats stats) {
        this.criterion = criterion;
        this.verdict = verdict;
        this.witnessOrder = witnessOrder;
        this.minimalConflict = minimalConflict;
        this.stats = stats;
    }

    /** The consistency criterion that was checked (e.g. {@code "linearizability"}). */
    public String criterion() {
        return criterion;
    }

    public Verdict verdict() {
        return verdict;
    }

    public boolean isConsistent() {
        return verdict == Verdict.CONSISTENT;
    }

    /**
     * When consistent: a total order of all operations that respects the
     * criterion and reproduces every observed result. {@code null} otherwise.
     */
    public List<Operation> witnessOrder() {
        return witnessOrder;
    }

    /**
     * When inconsistent: a 1-minimal subset of operations that is already
     * inconsistent on its own (removing any single operation makes it
     * consistent). {@code null} otherwise.
     */
    public List<Operation> minimalConflict() {
        return minimalConflict;
    }

    public Stats stats() {
        return stats;
    }

    /** Human-readable summary, including the counterexample when inconsistent. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("criterion: ").append(criterion).append('\n');
        sb.append("verdict:   ").append(verdict).append('\n');
        sb.append("stats:     operations=").append(stats.operations())
                .append(", nodes=").append(stats.nodes())
                .append(", memoHits=").append(stats.memoHits())
                .append(", specRejections=").append(stats.specRejections())
                .append(", elapsed=").append(stats.elapsedNanos() / 1_000_000).append("ms\n");
        if (witnessOrder != null) {
            sb.append("witness order (").append(witnessOrder.size()).append(" ops):\n");
            for (Operation op : witnessOrder) {
                sb.append("  ").append(op).append('\n');
            }
        }
        if (minimalConflict != null) {
            sb.append("counterexample - the following ").append(minimalConflict.size())
                    .append(" operation(s) cannot be ordered consistently")
                    .append(" (minimal conflict set):\n");
            for (Operation op : minimalConflict) {
                sb.append("  ").append(op).append('\n');
            }
        }
        return sb.toString();
    }
}
