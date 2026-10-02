package com.example.gsb.lin.spec;

import com.example.gsb.lin.Operation;
import com.example.gsb.lin.SequentialSpec;

/**
 * Sequential specification of a monotone counter.
 *
 * <p>Methods: {@code get()} (no args), {@code increment()} (returns the new value),
 * {@code add(long)} (returns the new value). State is the current {@code long} value.</p>
 */
public final class CounterSpec implements SequentialSpec<Long> {

    @Override
    public Long initialState() {
        return 0L;
    }

    @Override
    public StepResult<Long> step(Long state, Operation op) {
        return switch (op.method()) {
            case "get" -> StepResult.of(state, state);
            case "increment" -> StepResult.of(state + 1, state + 1);
            case "add" -> {
                long delta = ((Number) op.args().get(0)).longValue();
                yield StepResult.of(state + delta, state + delta);
            }
            default -> throw new IllegalArgumentException("unknown counter method: " + op.method());
        };
    }
}
