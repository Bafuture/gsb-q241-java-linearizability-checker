package com.example.gsb.lincheck.spec;

import com.example.gsb.lincheck.model.Operation;

/**
 * Sequential specification of a data structure, used as the oracle by the
 * checkers.
 *
 * <p>States of type {@code S} must be immutable (or never mutated after being
 * handed out) and must implement value-based {@code equals}/{@code hashCode};
 * they are used as part of the search memoization key.
 */
public interface SequentialSpec<S> {

    /** The state before any operation executed. */
    S initialState();

    /**
     * Attempts to apply {@code operation} (including its recorded result) to
     * {@code state}.
     *
     * @return the successor state, or {@code null} if the operation with its
     *         recorded result is not a legal sequential step from {@code state}
     * @throws IllegalArgumentException if the operation's method is unknown to
     *         this specification (a configuration error, not a search failure)
     */
    S step(S state, Operation operation);
}
