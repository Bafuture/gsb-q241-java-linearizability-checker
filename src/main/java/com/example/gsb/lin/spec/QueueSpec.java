package com.example.gsb.lin.spec;

import com.example.gsb.lin.Operation;
import com.example.gsb.lin.SequentialSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Sequential specification of a FIFO queue.
 *
 * <p>Methods: {@code enq(x)} (returns {@code null}), {@code deq()} (returns the head
 * element, or {@code null} when empty). State is an immutable list with the head first.</p>
 */
public final class QueueSpec implements SequentialSpec<List<Object>> {

    @Override
    public List<Object> initialState() {
        return List.of();
    }

    @Override
    public StepResult<List<Object>> step(List<Object> state, Operation op) {
        switch (op.method()) {
            case "enq": {
                List<Object> next = new ArrayList<>(state.size() + 1);
                next.addAll(state);
                next.add(op.args().get(0));
                return StepResult.of(List.copyOf(next), null);
            }
            case "deq": {
                if (state.isEmpty()) {
                    return StepResult.of(state, null);
                }
                return StepResult.of(List.copyOf(state.subList(1, state.size())), state.get(0));
            }
            default:
                throw new IllegalArgumentException("unknown queue method: " + op.method());
        }
    }
}
