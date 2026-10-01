package com.example.gsb.lincheck.check;

import com.example.gsb.lincheck.model.History;
import com.example.gsb.lincheck.model.Operation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Extracts a 1-minimal inconsistent subset of a history with the
 * delta-debugging ddmin algorithm (Zeller). The result is a counterexample:
 * it is still inconsistent, but removing any single operation from it makes
 * it consistent.
 */
public final class ConflictMinimizer {

    private ConflictMinimizer() {
    }

    public static List<Operation> minimize(History history, Predicate<History> inconsistent) {
        List<Operation> ops = new ArrayList<>(history.operations());
        return ddmin(ops, 2, inconsistent);
    }

    private static List<Operation> ddmin(List<Operation> ops, int chunks,
                                         Predicate<History> inconsistent) {
        if (ops.size() <= 1) {
            return ops;
        }
        List<List<Operation>> subsets = split(ops, chunks);
        for (List<Operation> subset : subsets) {
            if (inconsistent.test(new History(subset))) {
                return ddmin(subset, Math.max(chunks - 1, 2), inconsistent);
            }
        }
        for (List<Operation> subset : subsets) {
            List<Operation> complement = complement(ops, subset);
            if (inconsistent.test(new History(complement))) {
                return ddmin(complement, Math.max(chunks - 1, 2), inconsistent);
            }
        }
        if (chunks < ops.size()) {
            return ddmin(ops, Math.min(ops.size(), chunks * 2), inconsistent);
        }
        return ops;
    }

    private static List<List<Operation>> split(List<Operation> ops, int chunks) {
        List<List<Operation>> result = new ArrayList<>(chunks);
        int chunkSize = ops.size() / chunks;
        int remainder = ops.size() % chunks;
        int start = 0;
        for (int i = 0; i < chunks; i++) {
            int size = chunkSize + (i < remainder ? 1 : 0);
            result.add(new ArrayList<>(ops.subList(start, start + size)));
            start += size;
        }
        return result;
    }

    private static List<Operation> complement(List<Operation> all, List<Operation> subset) {
        List<Operation> result = new ArrayList<>(all.size() - subset.size());
        int index = 0;
        for (Operation operation : all) {
            if (index < subset.size() && operation.equals(subset.get(index))) {
                index++;
            } else {
                result.add(operation);
            }
        }
        return result;
    }
}
