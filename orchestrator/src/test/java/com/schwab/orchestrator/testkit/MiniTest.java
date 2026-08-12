package com.schwab.orchestrator.testkit;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Zero-dependency assertion + micro test-runner library.
 *
 * Why not JUnit: this prototype is built and verified in a network-restricted
 * sandbox with no access to Maven Central, so JUnit could not be fetched here.
 * The suites below are written in a JUnit-like style (one public void method
 * per test, discovered by naming convention) so porting to real JUnit 5 on a
 * machine with normal internet access is a mechanical find/replace - see
 * docs/04-testing-and-tradeoffs.md.
 */
public final class MiniTest {

    public static class AssertionFailed extends RuntimeException {
        public AssertionFailed(String message) { super(message); }
    }

    private MiniTest() { }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionFailed(message + " -- expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionFailed(message);
        }
    }

    public static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    public static void assertNotNull(Object value, String message) {
        if (value == null) {
            throw new AssertionFailed(message + " -- expected non-null value");
        }
    }

    public static <T extends Throwable> void assertThrows(Class<T> expectedType, Runnable action, String message) {
        try {
            action.run();
        } catch (Throwable t) {
            if (expectedType.isInstance(t)) {
                return;
            }
            throw new AssertionFailed(message + " -- expected " + expectedType.getSimpleName()
                    + " but got " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        throw new AssertionFailed(message + " -- expected " + expectedType.getSimpleName() + " but nothing was thrown");
    }

    public static void assertNoThrow(Runnable action, String message) {
        try {
            action.run();
        } catch (Throwable t) {
            throw new AssertionFailed(message + " -- unexpected exception " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }
}
