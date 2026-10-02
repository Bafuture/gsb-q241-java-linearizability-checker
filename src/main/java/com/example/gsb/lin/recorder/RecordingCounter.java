package com.example.gsb.lin.recorder;

import com.example.gsb.lin.ConcurrentCounter;
import com.example.gsb.lin.History;

/** A {@link ConcurrentCounter} whose calls are recorded for later consistency checks. */
public final class RecordingCounter {

    private final ConcurrentCounter counter;
    private final OperationRecorder recorder;

    public RecordingCounter(ConcurrentCounter counter, OperationRecorder recorder) {
        this.counter = counter;
        this.recorder = recorder;
    }

    public long increment(String process) {
        return recorder.record(process, "increment", counter::increment);
    }

    public long add(String process, long delta) {
        return recorder.record(process, "add", new Object[]{delta}, () -> counter.add(delta));
    }

    public long get(String process) {
        return recorder.record(process, "get", counter::get);
    }

    public History history() {
        return recorder.history();
    }
}
