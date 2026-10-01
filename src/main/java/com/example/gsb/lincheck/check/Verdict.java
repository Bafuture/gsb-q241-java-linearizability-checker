package com.example.gsb.lincheck.check;

/** Outcome of a consistency check. */
public enum Verdict {
    /** A valid sequential ordering matching all observed results exists. */
    CONSISTENT,
    /** No valid ordering exists; a minimal conflict set is attached to the report. */
    INCONSISTENT,
    /** The search node budget was exhausted before a verdict was reached. */
    INCONCLUSIVE
}
