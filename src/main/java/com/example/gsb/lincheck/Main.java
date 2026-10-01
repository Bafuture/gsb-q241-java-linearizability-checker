package com.example.gsb.lincheck;

import com.example.gsb.lincheck.check.CheckReport;
import com.example.gsb.lincheck.check.LinearizabilityChecker;
import com.example.gsb.lincheck.check.SequentialConsistencyChecker;
import com.example.gsb.lincheck.harness.ExecutionDriver;
import com.example.gsb.lincheck.harness.GatedRacyCounter;
import com.example.gsb.lincheck.harness.SynchronizedCounter;
import com.example.gsb.lincheck.harness.SynchronizedQueue;
import com.example.gsb.lincheck.model.History;
import com.example.gsb.lincheck.spec.CounterSpec;
import com.example.gsb.lincheck.spec.QueueSpec;

import java.util.List;

/**
 * Demo: records real executions of the built-in data structures and checks
 * the resulting histories for linearizability.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.out.println("== 1. synchronized counter, 4 threads x 25 increments ==");
        SynchronizedCounter counter = new SynchronizedCounter();
        History counterHistory = ExecutionDriver.run(4, (index, name, recorder) -> {
            for (int i = 0; i < 25; i++) {
                recorder.record(name, "inc", null, counter::incrementAndGet);
            }
        });
        print(new LinearizabilityChecker<>(new CounterSpec()).check(counterHistory));

        System.out.println("== 2. synchronized queue, 3 threads x (enq, enq, deq, deq) ==");
        SynchronizedQueue queue = new SynchronizedQueue();
        History queueHistory = ExecutionDriver.run(3, (index, name, recorder) -> {
            recorder.recordVoid(name, "enq", name + "-a", () -> queue.enq(name + "-a"));
            recorder.recordVoid(name, "enq", name + "-b", () -> queue.enq(name + "-b"));
            recorder.record(name, "deq", null, queue::deq);
            recorder.record(name, "deq", null, queue::deq);
        });
        print(new LinearizabilityChecker<>(new QueueSpec()).check(queueHistory));

        System.out.println("== 3. gated racy counter, 4 threads x 2 increments ==");
        GatedRacyCounter racy = new GatedRacyCounter(4);
        History racyHistory = ExecutionDriver.run(4, (index, name, recorder) -> {
            recorder.record(name, "inc", null, racy::incrementAndGet);
            recorder.record(name, "inc", null, racy::incrementAndGet);
        });
        print(new LinearizabilityChecker<>(new CounterSpec()).check(racyHistory));

        System.out.println("== 4. same racy history under sequential consistency ==");
        print(new SequentialConsistencyChecker<>(new CounterSpec()).check(racyHistory));
    }

    private static void print(CheckReport report) {
        String text = report.describe();
        List<String> lines = text.lines().toList();
        int limit = Math.min(lines.size(), 14);
        for (int i = 0; i < limit; i++) {
            System.out.println("  " + lines.get(i));
        }
        if (lines.size() > limit) {
            System.out.println("  ... (" + (lines.size() - limit) + " more lines)");
        }
        System.out.println();
    }
}
