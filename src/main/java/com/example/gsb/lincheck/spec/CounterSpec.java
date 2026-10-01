package com.example.gsb.lincheck.spec;

import com.example.gsb.lincheck.model.Operation;

import java.util.Objects;

/**
 * Sequential specification of a counter.
 *
 * <ul>
 *   <li>{@code inc() -> r}: legal iff {@code r == state + 1}; new state is {@code state + 1}.</li>
 *   <li>{@code get() -> r}: legal iff {@code r == state}; state unchanged.</li>
 * </ul>
 */
public final class CounterSpec implements SequentialSpec<Long> {

    @Override
    public Long initialState() {
        return 0L;
    }

    @Override
    public Long step(Long state, Operation operation) {
        switch (operation.method()) {
            case "inc":
                return Objects.equals(operation.result(), state + 1) ? state + 1 : null;
            case "get":
                return Objects.equals(operation.result(), state) ? state : null;
            default:
                throw new IllegalArgumentException("unknown counter method: " + operation.method());
        }
    }
}
