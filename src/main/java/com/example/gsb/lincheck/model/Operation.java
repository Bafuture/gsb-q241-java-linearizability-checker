package com.example.gsb.lincheck.model;

import java.util.Objects;

/**
 * A single completed operation in a concurrent history.
 *
 * <p>The operation is active during the closed interval
 * {@code [invocationTime, responseTime]}; its abstract linearization point,
 * if one exists, must lie inside that interval.
 */
public final class Operation {

    private final int id;
    private final String thread;
    private final String method;
    private final Object argument;
    private final Object result;
    private final long invocationTime;
    private final long responseTime;

    public Operation(int id, String thread, String method, Object argument, Object result,
                     long invocationTime, long responseTime) {
        if (responseTime < invocationTime) {
            throw new IllegalArgumentException(
                    "responseTime must be >= invocationTime for operation " + id);
        }
        this.id = id;
        this.thread = Objects.requireNonNull(thread, "thread");
        this.method = Objects.requireNonNull(method, "method");
        this.argument = argument;
        this.result = result;
        this.invocationTime = invocationTime;
        this.responseTime = responseTime;
    }

    public int id() {
        return id;
    }

    public String thread() {
        return thread;
    }

    public String method() {
        return method;
    }

    public Object argument() {
        return argument;
    }

    public Object result() {
        return result;
    }

    public long invocationTime() {
        return invocationTime;
    }

    public long responseTime() {
        return responseTime;
    }

    /** Real-time precedence: this operation completes before {@code other} starts. */
    public boolean precedes(Operation other) {
        return this.responseTime < other.invocationTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Operation operation)) {
            return false;
        }
        return id == operation.id
                && invocationTime == operation.invocationTime
                && responseTime == operation.responseTime
                && thread.equals(operation.thread)
                && method.equals(operation.method)
                && Objects.equals(argument, operation.argument)
                && Objects.equals(result, operation.result);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, thread, method, argument, result, invocationTime, responseTime);
    }

    @Override
    public String toString() {
        String args = argument == null ? "" : String.valueOf(argument);
        return "#" + id + "[" + thread + "] " + method + "(" + args + ") -> "
                + (result == null ? "void" : result)
                + " @[" + invocationTime + "," + responseTime + "]";
    }
}
