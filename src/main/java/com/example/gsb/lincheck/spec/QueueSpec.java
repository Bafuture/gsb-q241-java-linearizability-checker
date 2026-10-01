package com.example.gsb.lincheck.spec;

import com.example.gsb.lincheck.model.Operation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Sequential specification of an unbounded FIFO queue whose {@code deq} on an
 * empty queue returns {@code null}.
 *
 * <ul>
 *   <li>{@code enq(x)}: always legal; appends {@code x} (result is ignored).</li>
 *   <li>{@code deq() -> r}: on empty queue legal iff {@code r == null};
 *       otherwise legal iff {@code r} equals the head, which is removed.</li>
 * </ul>
 */
public final class QueueSpec implements SequentialSpec<List<Object>> {

    @Override
    public List<Object> initialState() {
        return List.of();
    }

    @Override
    public List<Object> step(List<Object> state, Operation operation) {
        switch (operation.method()) {
            case "enq": {
                List<Object> next = new ArrayList<>(state.size() + 1);
                next.addAll(state);
                next.add(operation.argument());
                return List.copyOf(next);
            }
            case "deq": {
                if (state.isEmpty()) {
                    return operation.result() == null ? state : null;
                }
                if (!Objects.equals(operation.result(), state.get(0))) {
                    return null;
                }
                return List.copyOf(state.subList(1, state.size()));
            }
            default:
                throw new IllegalArgumentException("unknown queue method: " + operation.method());
        }
    }
}
