package com.example.gsb.lin.recorder;

import com.example.gsb.lin.History;
import com.example.gsb.lin.Operation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Records real executions into a {@link History}: wraps each call with
 * {@code System.nanoTime()} invoke/response timestamps.
 */
public final class OperationRecorder {

    private static final Object[] NO_ARGS = new Object[0];

    private final List<Operation> operations = new CopyOnWriteArrayList<>();
    private final AtomicInteger nextId = new AtomicInteger();

    public <T> T record(String process, String method, Object[] args, Supplier<T> body) {
        int id = nextId.getAndIncrement();
        long invoke = System.nanoTime();
        T result = body.get();
        long response = System.nanoTime();
        operations.add(new Operation(id, process, invoke, response, method,
                args == null ? List.of() : List.of(args), result));
        return result;
    }

    public <T> T record(String process, String method, Supplier<T> body) {
        return record(process, method, NO_ARGS, body);
    }

    public void recordVoid(String process, String method, Object[] args, Runnable body) {
        record(process, method, args, () -> {
            body.run();
            return null;
        });
    }

    public History history() {
        return new History(new ArrayList<>(operations));
    }
}
