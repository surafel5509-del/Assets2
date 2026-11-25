package org.junit;
public class AssumptionViolatedException extends RuntimeException {
    public AssumptionViolatedException(String message) { super(message); }
    public AssumptionViolatedException() { super("assumption failed"); }
}
