package com.example.gsb.lincheck.model;

import java.util.ArrayList;
import java.util.List;

/**
 * An immutable history of completed operations.
 *
 * <p>A history stores every operation together with its call/return times,
 * method, argument and observed result. Sub-histories (used by the conflict
 * minimizer) keep the original ids and timestamps.
 */
public final class History {

    private final List<Operation> operations;

    public History(List<Operation> operations) {
        this.operations = List.copyOf(operations);
    }

    public List<Operation> operations() {
        return operations;
    }

    public int size() {
        return operations.size();
    }

    /** Returns the sub-history containing exactly the given operations (ids are preserved). */
    public History select(List<Operation> kept) {
        return new History(new ArrayList<>(kept));
    }
}
