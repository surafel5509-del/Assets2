package org.junit;
public class Assume {
    public static void assumeTrue(boolean assumption) {
        if (!assumption) throw new AssumptionViolatedException();
    }
    public static void assumeTrue(String message, boolean assumption) {
        if (!assumption) throw new AssumptionViolatedException(message);
    }
    public static void assumeNotNull(Object... objects) {
        for (Object o : objects) if (o == null) throw new AssumptionViolatedException();
    }
    public static void assumeThat(Object assumption) {}
}
