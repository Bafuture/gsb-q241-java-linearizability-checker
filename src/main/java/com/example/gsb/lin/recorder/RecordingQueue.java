package com.example.gsb.lin.recorder;

import com.example.gsb.lin.ConcurrentQueue;
import com.example.gsb.lin.History;

/** A {@link ConcurrentQueue} whose calls are recorded for later consistency checks. */
public final class RecordingQueue<T> {

    private final ConcurrentQueue<T> queue;
    private final OperationRecorder recorder;

    public RecordingQueue(ConcurrentQueue<T> queue, OperationRecorder recorder) {
        this.queue = queue;
        this.recorder = recorder;
    }

    public void enq(String process, T item) {
        recorder.recordVoid(process, "enq", new Object[]{item}, () -> queue.enq(item));
    }

    public T deq(String process) {
        return recorder.record(process, "deq", queue::deq);
    }

    public History history() {
        return recorder.history();
    }
}
