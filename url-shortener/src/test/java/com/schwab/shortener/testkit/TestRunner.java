package com.schwab.shortener.testkit;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Reflection-based runner: for each given class, instantiates it (no-arg
 * constructor) and invokes every public void no-arg method whose name starts
 * with "test", in declared order. Prints a JUnit-style report and exits
 * non-zero if any test failed or errored.
 */
public final class TestRunner {

    public static void main(String[] args) throws Exception {
        List<Class<?>> classes = new ArrayList<>();
        for (String className : args) {
            classes.add(Class.forName(className));
        }
        int total = 0, passed = 0, failed = 0;
        long suiteStart = System.nanoTime();
        List<String> failures = new ArrayList<>();

        for (Class<?> clazz : classes) {
            Object instance = clazz.getDeclaredConstructor().newInstance();
            List<Method> methods = new ArrayList<>();
            for (Method m : clazz.getMethods()) {
                if (m.getName().startsWith("test") && m.getParameterCount() == 0) {
                    methods.add(m);
                }
            }
            methods.sort((a, b) -> a.getName().compareTo(b.getName()));
            System.out.println();
            System.out.println(clazz.getSimpleName() + " (" + methods.size() + " tests)");
            for (Method m : methods) {
                total++;
                long start = System.nanoTime();
                try {
                    m.invoke(instance);
                    long ms = (System.nanoTime() - start) / 1_000_000;
                    System.out.println("  [PASS] " + m.getName() + " (" + ms + "ms)");
                    passed++;
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    long ms = (System.nanoTime() - start) / 1_000_000;
                    System.out.println("  [FAIL] " + m.getName() + " (" + ms + "ms) -- " + cause.getMessage());
                    failed++;
                    failures.add(clazz.getSimpleName() + "#" + m.getName() + ": " + cause.getMessage());
                }
            }
        }

        long totalMs = (System.nanoTime() - suiteStart) / 1_000_000;
        System.out.println();
        System.out.println("=====================================");
        System.out.println("Total: " + total + "  Passed: " + passed + "  Failed: " + failed + "  (" + totalMs + "ms)");
        System.out.println("=====================================");
        if (!failures.isEmpty()) {
            System.out.println("Failures:");
            for (String f : failures) System.out.println("  - " + f);
            System.exit(1);
        }
    }
}
