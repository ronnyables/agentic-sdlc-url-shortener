package com.schwab.orchestrator.agents;

import com.schwab.orchestrator.core.StageExecutor;
import com.schwab.orchestrator.core.StageResult;
import com.schwab.orchestrator.core.WorkflowContext;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Not a simulation: this agent shells out to the real build tool and reports
 * the true pass/fail result.
 *
 * As of the Spring Boot / Maven rebuild, that means invoking
 * {@code mvn -f <projectDir>/pom.xml test} (optionally scoped to specific
 * classes via {@code -Dtest=}) and parsing Maven Surefire's own summary line
 * ("Tests run: X, Failures: Y, Errors: Z, Skipped: W"). The *last* such match
 * in the output is used, since Surefire prints one per test class plus a
 * final aggregate "Results:" block - taking the last one gives the aggregate.
 *
 * Requires {@code mvn} on PATH. If it isn't found, this fails honestly with a
 * clear message rather than fabricating a result (see
 * docs/04-testing-and-tradeoffs.md for why this couldn't be executed inside
 * the build sandbox this project was otherwise verified in, and how the
 * summary-parsing regex was validated in isolation instead).
 */
public final class TestingAgent implements StageExecutor {

    private static final Pattern SUMMARY = Pattern.compile(
            "Tests run:\\s*(\\d+),\\s*Failures:\\s*(\\d+),\\s*Errors:\\s*(\\d+),\\s*Skipped:\\s*(\\d+)");

    private final String mavenProjectDir;
    private final List<String> explicitTestClasses; // empty/null = run Maven's default full test suite

    public TestingAgent(String mavenProjectDir, List<String> explicitTestClasses) {
        this.mavenProjectDir = mavenProjectDir;
        this.explicitTestClasses = explicitTestClasses;
    }

    @Override
    public StageResult execute(WorkflowContext context, int attemptNumber) throws Exception {
        Path pom = Path.of(mavenProjectDir, "pom.xml");

        // No "-q": Surefire's own "Tests run: X, Failures: Y, Errors: Z, Skipped: W"
        // summary line is what SUMMARY below parses to determine pass/fail, and -q
        // suppresses it - which surfaced as a real bug the first time this ran
        // against a working Maven install: mvn genuinely exited 0 (all tests passed)
        // but the quiet flag meant no summary line ever reached this agent's output
        // buffer, so it reported failure anyway. -B (batch mode, no interactive
        // prompts) is kept since it's still needed for non-interactive CI-style runs.
        List<String> command = new ArrayList<>(List.of("mvn", "-B", "-f", pom.toString()));
        if (explicitTestClasses != null && !explicitTestClasses.isEmpty()) {
            command.add("-Dtest=" + String.join(",", explicitTestClasses));
        }
        command.add("test");

        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (Exception e) {
            return StageResult.failed("could not launch Maven ('mvn' on PATH is required to run tests for the "
                    + "Spring Boot module): " + e.getMessage());
        }

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        boolean finished = process.waitFor(180, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return StageResult.failed("mvn test timed out after 180s");
        }
        int exitCode = process.exitValue();
        String out = output.toString();

        int total = -1, failures = -1, errors = -1, skipped = -1;
        Matcher m = SUMMARY.matcher(out);
        while (m.find()) {
            total = Integer.parseInt(m.group(1));
            failures = Integer.parseInt(m.group(2));
            errors = Integer.parseInt(m.group(3));
            skipped = Integer.parseInt(m.group(4));
        }

        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("exitCode", exitCode);
        artifacts.put("totalTests", total);
        artifacts.put("failedTests", failures);
        artifacts.put("errorTests", errors);
        artifacts.put("skippedTests", skipped);
        artifacts.put("allTestsPassed", exitCode == 0 && failures == 0 && errors == 0 && total > 0);
        artifacts.put("rawOutputTail", out.length() > 2000 ? out.substring(out.length() - 2000) : out);

        context.recordDecision("testing", "agent:TestingAgent",
                exitCode == 0 ? "TESTS_PASSED" : "TESTS_FAILED",
                "ran real `mvn test`: total=" + total + " failures=" + failures + " errors=" + errors + " skipped=" + skipped);

        if (exitCode != 0 || total <= 0) {
            return StageResult.failed("mvn test reported failures/errors or produced no parseable summary (exit=" + exitCode + ")");
        }
        return StageResult.ok("All " + total + " tests passed", artifacts);
    }
}
