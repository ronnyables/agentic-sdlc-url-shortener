package com.schwab.orchestrator.agents;

import com.schwab.orchestrator.core.StageExecutor;
import com.schwab.orchestrator.core.StageResult;
import com.schwab.orchestrator.core.WorkflowContext;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Not a simulation: this agent actually shells out and runs the url-shortener's
 * real test harness (com.schwab.shortener.testkit.TestRunner) against the
 * compiled test classes, and reports the true pass/fail result. If the target
 * classpath hasn't been compiled yet, the stage fails honestly rather than
 * fabricating a result.
 */
public final class TestingAgent implements StageExecutor {

    private static final Pattern SUMMARY = Pattern.compile("Total: (\\d+)\\s+Passed: (\\d+)\\s+Failed: (\\d+)");

    private final String testClasspath;
    private final List<String> testClassNames;

    public TestingAgent(String testClasspath, List<String> testClassNames) {
        this.testClasspath = testClasspath;
        this.testClassNames = testClassNames;
    }

    @Override
    public StageResult execute(WorkflowContext context, int attemptNumber) throws Exception {
        String javaBin = System.getProperty("java.home") + "/bin/java";
        java.util.List<String> command = new java.util.ArrayList<>(List.of(javaBin, "-cp", testClasspath,
                "com.schwab.shortener.testkit.TestRunner"));
        command.addAll(testClassNames);

        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return StageResult.failed("test run timed out after 30s");
        }
        int exitCode = process.exitValue();
        String out = output.toString();

        Matcher m = SUMMARY.matcher(out);
        int total = -1, passed = -1, failed = -1;
        if (m.find()) {
            total = Integer.parseInt(m.group(1));
            passed = Integer.parseInt(m.group(2));
            failed = Integer.parseInt(m.group(3));
        }

        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("exitCode", exitCode);
        artifacts.put("totalTests", total);
        artifacts.put("passedTests", passed);
        artifacts.put("failedTests", failed);
        artifacts.put("allTestsPassed", exitCode == 0 && failed == 0 && total > 0);
        artifacts.put("rawOutputTail", out.length() > 2000 ? out.substring(out.length() - 2000) : out);

        context.recordDecision("testing", "agent:TestingAgent",
                exitCode == 0 ? "TESTS_PASSED" : "TESTS_FAILED",
                "ran real TestRunner subprocess: total=" + total + " passed=" + passed + " failed=" + failed);

        if (exitCode != 0 || total <= 0) {
            return StageResult.failed("test run reported failures or produced no parseable summary (exit=" + exitCode + ")");
        }
        return StageResult.ok("All " + total + " tests passed", artifacts);
    }
}
