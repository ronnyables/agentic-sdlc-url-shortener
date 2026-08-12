package com.schwab.orchestrator.scenarios;

import com.schwab.orchestrator.agents.*;
import com.schwab.orchestrator.core.*;
import com.schwab.orchestrator.guardrails.*;

public final class WorkflowGraphs {

    private WorkflowGraphs() { }

    public static GuardrailEngine buildGuardrails() {
        return new GuardrailEngine()
                .register(new SecurityGuardrail())
                .register(new ComplianceGuardrail("testing.allTestsPassed", "documentation.readmePresent"))
                .register(new ChangeControlGuardrail());
    }

    public static WorkflowGraph buildSdlcGraph(SdlcOptions opts) {
        WorkflowGraph graph = new WorkflowGraph();

        graph.addStage(StageDefinition.builder("requirements", "Requirement Understanding")
                .executor(new RequirementsAgent())
                .maxRetries(1)
                .build());

        String designDependsOn = "requirements";
        if (opts.brownfield) {
            graph.addStage(StageDefinition.builder("codebase-reasoning", "Codebase Reasoning (Brownfield Impact Analysis)")
                    .dependsOn("requirements")
                    .executor(new CodebaseReasoningAgent())
                    .maxRetries(1)
                    .build());
        }

        StageDefinition.Builder designBuilder = StageDefinition.builder("design", "Architecture / Design")
                .executor(new DesignAgent())
                .maxRetries(1)
                .dependsOn(designDependsOn);
        if (opts.brownfield) {
            designBuilder.dependsOn("codebase-reasoning");
        }
        graph.addStage(designBuilder.build());

        graph.addStage(StageDefinition.builder("task-decomposition", "Task Decomposition")
                .dependsOn("design")
                .executor(new TaskDecompositionAgent())
                .maxRetries(1)
                .exitGate((ctx, result) -> {
                    Object count = result.getArtifacts().get("taskCount");
                    if (count instanceof Integer && (Integer) count > 0) {
                        return GateResult.pass();
                    }
                    return GateResult.block("decomposition produced zero tasks - nothing to hand off to implementation");
                })
                .build());

        graph.addStage(StageDefinition.builder("implementation", "Implementation")
                .dependsOn("task-decomposition")
                .entryGate(ctx -> ctx.getArtifact("design.apiChanges") != null
                        ? GateResult.pass()
                        : GateResult.block("no design artifact found in context - refusing to implement against an undefined design"))
                .executor(new ImplementationAgent(opts.simulateSecretLeakOnFirstAttempt))
                .maxRetries(2)
                .retryBackoffMillis(20)
                .guardrails("security")
                .compensatingAction(ctx -> {
                    ctx.putArtifact("implementation.reverted", true);
                    ctx.recordDecision("implementation", "system", "COMPENSATED", "reverted change plan due to downstream failure");
                })
                .build());

        graph.addStage(StageDefinition.builder("testing", "Testing")
                .dependsOn("implementation")
                .executor(new TestingAgent(opts.testClasspath, opts.testClassNames))
                .maxRetries(1)
                .retryBackoffMillis(20)
                .build());

        graph.addStage(StageDefinition.builder("documentation", "Documentation")
                .dependsOn("implementation")
                .executor(new DocumentationAgent(opts.docOutputDir))
                .maxRetries(1)
                .build());

        graph.addStage(StageDefinition.builder("release-readiness", "Release Readiness")
                .dependsOn("testing", "documentation")
                .executor(new ReleaseReadinessAgent())
                .requiresApproval(opts.requireApprovalForRelease)
                .maxRetries(0)
                .guardrails("compliance", "change-control")
                .build());

        return graph;
    }
}
