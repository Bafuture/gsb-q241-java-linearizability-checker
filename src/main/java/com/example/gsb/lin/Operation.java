package com.example.gsb.lin;

import java.util.List;

/**
 * A single operation in a concurrent history.
 *
 * @param id          unique identifier (used for stable reporting)
 * @param process     name of the thread/process that executed the operation
 * @param invoke      invocation timestamp (same clock for the whole history)
 * @param response    response timestamp, must be {@code >= invoke}
 * @param method      operation type, e.g. {@code "increment"}, {@code "enq"}
 * @param args        operation arguments
 * @param returnValue observed return value ({@code null} for void)
 */
public record Operation(int id, String process, long invoke, long response,
                        String method, List<Object> args, Object returnValue) {

    public Operation {
        args = args == null ? List.of() : List.copyOf(args);
        if (response < invoke) {
            throw new IllegalArgumentException("response < invoke for " + method);
        }
    }

    public static Operation of(int id, String process, long invoke, long response,
                               String method, Object returnValue, Object... args) {
        return new Operation(id, process, invoke, response, method, List.of(args), returnValue);
    }

    /** Real-time precedence: this operation completed before {@code other} started. */
    public boolean precedes(Operation other) {
        return this.response < other.invoke;
    }

    @Override
    public String toString() {
        return "#" + id + " " + process + ":" + method + args
                + " -> " + returnValue + " [" + invoke + "," + response + "]";
    }
}
