#!/usr/bin/env bash
# Runs the orchestration engine's unit test suite (graph validation, parallel
# sync, retries, rollback, approval gating, safe-stop, dynamic re-plan,
# guardrails, metrics) against synthetic stages - independent of the
# url-shortener module.
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
scripts/compile.sh orchestrator/src/main/java orchestrator/bin
scripts/compile.sh orchestrator/src/test/java orchestrator/bin -cp orchestrator/bin
java -cp orchestrator/bin com.schwab.orchestrator.testkit.TestRunner \
  com.schwab.orchestrator.WorkflowGraphTest \
  com.schwab.orchestrator.WorkflowEngineTest \
  com.schwab.orchestrator.GuardrailEngineTest \
  com.schwab.orchestrator.MetricsCollectorTest
