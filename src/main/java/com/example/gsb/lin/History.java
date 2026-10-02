package com.example.gsb.lin;

import java.util.List;

/**
 * An immutable concurrent history: a set of operations with invoke/response intervals.
 *
 * <p>The checker uses a 64-bit bitmask per search node, so histories are limited to
 * {@value #MAX_OPERATIONS} operations.</p>
 */
public record History(List<Operation> operations) {

    public static final int MAX_OPERATIONS = 64;

    public History {
        operations = List.copyOf(operations);
        if (operations.size() > MAX_OPERATIONS) {
            throw new IllegalArgumentException(
                    "histories are limited to " + MAX_OPERATIONS + " operations, got " + operations.size());
        }
    }

    public static History of(Operation... operations) {
        return new History(List.of(operations));
    }

    public int size() {
        return operations.size();
    }
}
