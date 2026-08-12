package com.schwab.orchestrator.agents;

import com.schwab.orchestrator.core.StageExecutor;
import com.schwab.orchestrator.core.StageResult;
import com.schwab.orchestrator.core.WorkflowContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Produces a real Markdown artifact on disk (not just an in-memory string) so
 * "documentation improvements" is a genuine, inspectable deliverable of the run.
 */
public final class DocumentationAgent implements StageExecutor {

    private final String outputDir;

    public DocumentationAgent(String outputDir) {
        this.outputDir = outputDir;
    }

    @SuppressWarnings("unchecked")
    @Override
    public StageResult execute(WorkflowContext context, int attemptNumber) throws Exception {
        List<String> functional = context.getArtifact("requirements.functionalRequirements", List.of());
        List<String> apiChanges = context.getArtifact("design.apiChanges", List.of());
        List<Map<String, Object>> tasks = context.getArtifact("task-decomposition.tasks", List.of());

        StringBuilder md = new StringBuilder();
        md.append("# Change Summary - run ").append(context.getRunId()).append("\n\n");
        md.append("Generated: ").append(Instant.now()).append("\n\n");
        md.append("## Original Requirement\n\n").append(context.getRawRequirement()).append("\n\n");
        md.append("## Functional Scope\n\n");
        for (String f : functional) md.append("- ").append(f).append('\n');
        md.append("\n## API Changes\n\n");
        for (String a : apiChanges) md.append("- ").append(a).append('\n');
        md.append("\n## Task Breakdown\n\n");
        for (Map<String, Object> t : tasks) {
            md.append("- ").append(t.get("id")).append(": ").append(t.get("description"));
            List<String> deps = (List<String>) t.get("dependsOn");
            if (deps != null && !deps.isEmpty()) md.append(" (depends on ").append(deps).append(")");
            md.append('\n');
        }
        md.append("\n## Decision Lineage\n\n");
        for (var d : context.decisionLineage()) {
            md.append("- ").append(d.toString()).append('\n');
        }

        Path dir = Path.of(outputDir);
        Files.createDirectories(dir);
        Path file = dir.resolve(context.getRunId() + "-summary.md");
        Files.writeString(file, md.toString());

        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("readmePresent", true);
        artifacts.put("docPath", file.toString());
        artifacts.put("docSizeBytes", Files.size(file));

        context.recordDecision("documentation", "agent:DocumentationAgent", "DOC_GENERATED", "wrote " + file);

        return StageResult.ok("Documentation written to " + file, artifacts);
    }
}
