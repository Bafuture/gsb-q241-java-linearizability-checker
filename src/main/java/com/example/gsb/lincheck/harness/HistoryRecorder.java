package com.example.gsb.lincheck.harness;

import com.example.gsb.lincheck.model.History;
import com.example.gsb.lincheck.model.Operation;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Records real executions into {@link Operation} histories. Call and return
 * times are sampled immediately before and after each operation, so the
 * resulting intervals contain the actual execution of the underlying call.
 */
public final class HistoryRecorder {

    private final List<Operation> operations = new CopyOnWriteArrayList<>();
    private final AtomicInteger nextId = new AtomicInteger();
    private final LongSupplier clock;

    public HistoryRecorder() {
        this(System::nanoTime);
    }

    HistoryRecorder(LongSupplier clock) {
        this.clock = clock;
    }

    public <T> T record(String thread, String method, Object argument, Supplier<T> call) {
        int id = nextId.getAndIncrement();
        long invocationTime = clock.getAsLong();
        T result = call.get();
        long responseTime = clock.getAsLong();
        operations.add(new Operation(id, thread, method, argument, result,
                invocationTime, responseTime));
        return result;
    }

    public void recordVoid(String thread, String method, Object argument, Runnable call) {
        record(thread, method, argument, () -> {
            call.run();
            return null;
        });
    }

    public History history() {
        return new History(operations);
    }
}
