package com.example.gsb.lin.spec;

import com.example.gsb.lin.Operation;
import com.example.gsb.lin.SequentialSpec;

/**
 * Sequential specification of a read/write register.
 *
 * <p>Methods: {@code write(v)} (returns {@code null}), {@code read()} (returns value).
 * The initial value is configurable (default {@code null}).</p>
 */
public final class RegisterSpec implements SequentialSpec<Object> {

    private final Object initial;

    public RegisterSpec(Object initial) {
        this.initial = initial;
    }

    public RegisterSpec() {
        this(null);
    }

    @Override
    public Object initialState() {
        return initial;
    }

    @Override
    public StepResult<Object> step(Object state, Operation op) {
        return switch (op.method()) {
            case "write" -> StepResult.of(op.args().get(0), null);
            case "read" -> StepResult.of(state, state);
            default -> throw new IllegalArgumentException("unknown register method: " + op.method());
        };
    }
}
