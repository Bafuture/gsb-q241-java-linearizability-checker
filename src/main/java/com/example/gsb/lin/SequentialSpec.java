package com.example.gsb.lin;

/**
 * Sequential specification of the data structure under test, as a pure state machine.
 *
 * @param <S> immutable (or value-based) state type; states are used as memoization keys
 */
public interface SequentialSpec<S> {

    S initialState();

    /**
     * Executes {@code op} on {@code state}.
     *
     * @return the next state together with the return value the spec produces
     * @throws IllegalArgumentException if the operation is not part of the spec
     */
    StepResult<S> step(S state, Operation op);

    record StepResult<S>(S nextState, Object returnValue) {
        public static <S> StepResult<S> of(S nextState, Object returnValue) {
            return new StepResult<>(nextState, returnValue);
        }
    }
}
