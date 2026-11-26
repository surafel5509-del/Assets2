package org.junit;
public class Assert {
    public static void fail() { throw new AssertionError(); }
    public static void fail(String message) { throw new AssertionError(message); }
    public static void assertTrue(boolean condition) { if (!condition) fail("expected true"); }
    public static void assertTrue(String message, boolean condition) { if (!condition) fail(message); }
    public static void assertFalse(boolean condition) { if (condition) fail("expected false"); }
    public static void assertFalse(String message, boolean condition) { if (condition) fail(message); }
    public static void assertNull(Object object) { if (object != null) fail("expected null but was: " + object); }
    public static void assertNull(String message, Object object) { if (object != null) fail(message + " expected null but was: " + object); }
    public static void assertNotNull(Object object) { if (object == null) fail("expected not null"); }
    public static void assertNotNull(String message, Object object) { if (object == null) fail(message); }
    public static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) return;
        fail("expected:<" + expected + "> but was:<" + actual + ">");
    }
    public static void assertEquals(String message, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) return;
        fail(message + " expected:<" + expected + "> but was:<" + actual + ">");
    }
    public static void assertEquals(long expected, long actual) {
        if (expected != actual) fail("expected:<" + expected + "> but was:<" + actual + ">");
    }
    public static void assertEquals(String message, long expected, long actual) {
        if (expected != actual) fail(message + " expected:<" + expected + "> but was:<" + actual + ">");
    }
    public static void assertEquals(int expected, int actual) {
        if (expected != actual) fail("expected:<" + expected + "> but was:<" + actual + ">");
    }
    public static void assertEquals(String message, int expected, int actual) {
        if (expected != actual) fail(message + " expected:<" + expected + "> but was:<" + actual + ">");
    }
    public static void assertEquals(double expected, double actual, double delta) {
        if (Double.compare(expected, actual) == 0) return;
        if (Math.abs(expected - actual) > delta) fail("expected:<" + expected + "> but was:<" + actual + ">");
    }
    public static void assertEquals(String message, double expected, double actual, double delta) {
        if (Double.compare(expected, actual) == 0) return;
        if (Math.abs(expected - actual) > delta) fail(message + " expected:<" + expected + "> but was:<" + actual + ">");
    }
    public static void assertEquals(float expected, float actual, float delta) {
        if (Float.compare(expected, actual) == 0) return;
        if (Math.abs(expected - actual) > delta) fail("expected:<" + expected + "> but was:<" + actual + ">");
    }
    public static void assertEquals(String message, float expected, float actual, float delta) {
        if (Float.compare(expected, actual) == 0) return;
        if (Math.abs(expected - actual) > delta) fail(message + " expected:<" + expected + "> but was:<" + actual + ">");
    }
}
