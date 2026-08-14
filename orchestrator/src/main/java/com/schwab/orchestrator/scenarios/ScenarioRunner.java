package com.schwab.orchestrator.scenarios;

import com.schwab.orchestrator.core.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Shared execution + reporting helpers used by all three scenario drivers. */
public final class ScenarioRunner {

    private ScenarioRunner() { }

    public static void printHeader(String title) {
        System.out.println();
        System.out.println("=".repeat(90));
        System.out.println(title);
        System.out.println("=".repeat(90));
    }

    public static void printStatus(RunState run) {
        System.out.println();
        System.out.println("-- stage statuses --");
        run.statusSnapshot().forEach((stage, status) -> System.out.printf("  %-22s %s%n", stage, status));
        System.out.println("-- run status: " + run.getStatus() + " --");
    }

    public static void printMetrics(RunState run) {
        RunMetrics metrics = new MetricsCollector().collect(run);
        System.out.println();
        System.out.println("-- reliability metrics --");
        System.out.println("  " + metrics);
    }

    public static void writeReport(String outputDir, String fileNamePrefix, RunState run, AuditTrail audit) throws Exception {
        Path dir = Path.of(outputDir);
        Files.createDirectories(dir);
        RunMetrics metrics = new MetricsCollector().collect(run);
        StringBuilder sb = new StringBuilder();
        sb.append("Run: ").append(run.getRunId()).append(" (").append(run.getScenarioName()).append(")\n");
        sb.append("Final status: ").append(run.getStatus()).append('\n');
        sb.append("Metrics: ").append(metrics).append("\n\n");
        sb.append("-- stage statuses --\n");
        for (Map.Entry<String, String> e : run.statusSnapshot().entrySet()) {
            sb.append(String.format("  %-22s %s%n", e.getKey(), e.getValue()));
        }
        sb.append("\n-- full audit timeline --\n");
        sb.append(audit.renderTimeline());
        Path file = dir.resolve(fileNamePrefix + "-" + run.getRunId() + "-trace.txt");
        Files.writeString(file, sb.toString());
        System.out.println();
        System.out.println("Full execution trace written to " + file);
    }
}
