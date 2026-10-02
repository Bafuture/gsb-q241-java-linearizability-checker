package com.example.gsb.lin;

/** Consistency criterion a history is checked against. */
public enum ConsistencyModel {
    /** Real-time order: non-overlapping operations must keep their wall-clock order. */
    LINEARIZABLE,
    /** Program order only: operations of one process keep their issue order. */
    SEQUENTIALLY_CONSISTENT
}
