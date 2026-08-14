# Orchestration Model — Requirement-by-Requirement

The assignment's Workflow Orchestration requirement is a checklist of nine capabilities. This document maps each one to the specific implementation and the test/scenario evidence that it actually works, not just that the code exists.

## "Explicit dependency graph with entry/exit gates"

`WorkflowGraph` (`orchestrator/core/WorkflowGraph.java`) holds `StageDefinition`s with explicit `dependsOn` sets, validates the graph is acyclic (`validate()`, Kahn's-algorithm-based), and exposes `topologicalLayers()`. Every stage has an `entryGate` (`Gate`, evaluated before execution) and an `exitGate` (`ExitGate`, evaluated after a successful result, before the stage is allowed to commit). Concrete examples in `scenarios/WorkflowGraphs.java`:

- `implementation`'s entry gate refuses to run if no `design` artifact exists in context, independent of the DAG edge itself.
- `task-decomposition`'s exit gate blocks if zero tasks were produced.

Evidence: `WorkflowGraphTest` (6 tests) — cycle detection, unknown-dependency rejection, layering.

## "Sequential and parallel paths with synchronization"

The scheduler recomputes readiness every round (see `01-architecture.md`); stages in the same round run concurrently on a bounded thread pool and a multi-dependency stage naturally synchronizes on all of them. In the SDLC graph, `testing` and `documentation` both depend only on `implementation` and run in parallel; `release-readiness` depends on both and joins.

Evidence: `WorkflowEngineTest#testParallelStagesBothCompleteBeforeSynchronizingDependent` asserts `a` precedes both `b1`/`b2`, and both precede `c`. The greenfield scenario's captured trace shows `testing` and `documentation` both completing before `release-readiness` is ever attempted (`docs/generated/greenfield-*-trace.txt`).

## "Preserve cross-stage context and decision lineage"

`WorkflowContext` (one instance per run) holds an artifact bag (`putArtifact`/`getArtifact`, namespaced `stageId.key`) and an append-only `List<DecisionRecord>` (`recordDecision`). Every agent writes to both. The full lineage is printed at the end of every scenario run and written to `docs/generated/*-trace.txt`.

Evidence: the ambiguous scenario's decision lineage shows the *same* `release-readiness` stage approved twice under two different underlying designs — direct proof context and lineage survive a re-plan.

## "Enforce human approval checkpoints for high-impact actions"

`StageDefinition.requiresApproval(true)` on `release-readiness` — the highest-impact action in the graph (shipping). When reached, the engine sets the stage to `WAITING_APPROVAL`, records a `PendingApproval`, and `advance()` returns with `RunStatus.PAUSED_FOR_APPROVAL` instead of executing anything further downstream. `WorkflowEngine.decideApproval(stageId, approved, approver, comment)` resumes it. Approval is **versioned**: `invalidate()` bumps a per-stage `planVersion`, and the gate re-checks for an `APPROVED#<currentVersion>` decision, so a stale approval from before a re-plan does not silently carry over.

Evidence: `WorkflowEngineTest#testApprovalGateHoldsAndResumesOnApproval`, `#testApprovalRejectionFailsStageAndSkipsDependents`. All three scenarios pause for real and are resumed by a scripted (clearly-labeled) "simulated reviewer decision" — see `03-scenarios.md`.

## "Bounded retries, fallback, rollback, and safe-stop controls"

- **Bounded retries**: `StageDefinition.maxRetries` + `retryBackoffMillis`, exponential backoff capped at 2^6. `executeWithRetries()` in `WorkflowEngine`.
- **Fallback**: `StageDefinition.fallbackExecutor`, invoked once after retries are exhausted, before the stage is declared permanently failed.
- **Rollback**: a permanently failed stage triggers `cascadeRollback()`, which walks direct dependencies, runs `CompensatingAction` on any that are `isRollbackable()` and currently `SUCCEEDED`, and recurses further upstream.
- **Safe-stop**: `EngineConfig.maxTotalFailuresBeforeSafeStop` — once the run's aggregate failure count crosses this, `checkSafeStop()` marks every remaining `PENDING`/`STALE` stage `SKIPPED` and halts the run with `RunStatus.SAFE_STOPPED`, regardless of whether those stages would have otherwise succeeded.

Evidence: `WorkflowEngineTest#testTransientFailureRecoversViaRetry`, `#testExhaustedRetriesFailsStageAndSkipsDependents`, `#testRollbackCascadeRunsCompensatingActionOnUpstreamSuccess`, `#testSafeStopHaltsRemainingWorkAfterFailureThreshold`. The brownfield scenario exercises retry + rollback together against a **real** failing test (not a mock) — see `03-scenarios.md`.

## "Policy guardrails for security, compliance, and change control"

Three guardrails, registered by name and opted into per stage (`orchestrator/guardrails/`):

- `SecurityGuardrail` — regex-scans a stage's output artifacts for patterns that look like hardcoded credentials (`api_key=`, `password=`, PEM private key headers). Applied to `implementation`.
- `ComplianceGuardrail` — blocks a stage unless specific upstream artifacts are present and truthy (e.g., `testing.allTestsPassed`, `documentation.readmePresent`). Applied to `release-readiness`.
- `ChangeControlGuardrail` — independently re-verifies a recorded human-approval decision exists for any stage marked `requiresApproval`, as a belt-and-suspenders check separate from the engine's own approval gate. Applied to `release-readiness`.

A guardrail failure is treated as a non-retryable-cause failed attempt (retrying won't fix a policy violation on its own, but the *agent* can produce different output on the next attempt — see below).

Evidence: `GuardrailEngineTest` (5 tests). The ambiguous scenario deliberately makes `ImplementationAgent`'s first draft look like it contains a leaked API key; `SecurityGuardrail` blocks it, the stage retries, the second attempt is clean, and the guardrail passes — a real block-then-recover cycle, not a hypothetical one (see captured trace: `implementation` shows 2 attempts, `mttr=40ms`).

## "Audit-grade observability and traceability"

`AuditTrail` is an append-only, thread-safe log of `AuditEvent`s (timestamped, typed, actor-attributed) covering every state transition: `RUN_STARTED`, `STAGE_ENTERED`, `STAGE_ATTEMPT_STARTED/FAILED`, `STAGE_RETRY_SCHEDULED`, `STAGE_FALLBACK_INVOKED`, `EXIT_GATE_BLOCKED`, `GUARDRAIL_BLOCKED`, `STAGE_SUCCEEDED/FAILED/SKIPPED`, `STAGE_WAITING_APPROVAL/APPROVED/REJECTED`, `STAGE_ROLLED_BACK`, `STAGE_INVALIDATED`, `SAFE_STOP_TRIGGERED`, `RUN_COMPLETED`. Every scenario run writes its full timeline to `docs/generated/*-trace.txt`, and the REST API exposes it live at `GET /runs/{runId}/audit`.

## "Track reliability metrics: success rate, retry/rollback frequency, MTTR, end-to-end latency"

`MetricsCollector.collect(RunState)` computes exactly these four, plus raw counts (succeeded/failed/rolled-back/skipped) and total retries, from per-stage `StageRuntimeInfo` (attempt/retry counters, first-attempt and terminal timestamps) and the run's start/completion timestamps. MTTR is the mean, across stages that failed at least once and eventually succeeded, of `(terminalAt - firstAttemptAt)`.

Evidence: `MetricsCollectorTest` (2 tests). Real numbers from an actual run (ambiguous scenario, first pass): `stages=7 succeeded=7 failed=0 rolledBack=0 skipped=0 successRate=100.0% retries=1 retryFreq=0.14 rollbackFreq=0.00 mttr=40ms e2eLatency=489ms`.

## "Dynamically re-plan when upstream outputs change, while maintaining governance"

`WorkflowEngine.invalidate(stageId, reason)` marks a stage and every transitive dependent `STALE`. Because `STALE` is treated identically to `PENDING` by the readiness check, the very next `advance()` re-executes them with no special-cased "replan mode." Governance is maintained through the run because approval is versioned (see above) — a re-planned `release-readiness` cannot silently reuse an old approval.

Evidence: `WorkflowEngineTest#testInvalidateReexecutesStaleStagesOnReplan`. The ambiguous scenario is the end-to-end demonstration: after the first full run completes and is approved, a human clarifies the original vague requirement; `invalidate("requirements", ...)` is called; `requirements` through `release-readiness` all re-execute with the clarified text; `design.apiChanges` changes from a generic analytics extension to a specific `?since=`/`?groupBy=day` API; and `release-readiness` requires — and gets — a fresh approval (`APPROVED#1`).
