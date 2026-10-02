package com.example.gsb.lin;

import java.util.List;

/**
 * Outcome of a consistency check.
 *
 * @param consistent      whether a legal serialization exists
 * @param model           the consistency model that was checked
 * @param witnessOrder    a legal linearization (empty when inconsistent)
 * @param minimalConflict a 1-minimal set of operations that is still not serializable
 *                        (empty when consistent); removing any single operation from it
 *                        makes the remaining sub-history consistent
 * @param stats           search statistics
 */
public record CheckResult(boolean consistent, ConsistencyModel model,
                          List<Operation> witnessOrder, List<Operation> minimalConflict,
                          CheckStats stats) {

    public CheckResult {
        witnessOrder = List.copyOf(witnessOrder);
        minimalConflict = List.copyOf(minimalConflict);
    }

    /** Convenience for checks run with {@link ConsistencyModel#LINEARIZABLE}. */
    public boolean linearizable() {
        if (model != ConsistencyModel.LINEARIZABLE) {
            throw new IllegalStateException("result is for model " + model);
        }
        return consistent;
    }
}
