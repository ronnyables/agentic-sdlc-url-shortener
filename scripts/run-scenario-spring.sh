#!/usr/bin/env bash
# Usage: scripts/run-scenario-spring.sh [greenfield|brownfield|ambiguous]
# Runs an orchestration scenario against url-shortener-spring. Only the
# orchestrator module itself needs to be javac-built (no external deps); the
# TestingAgent stage shells out to `mvn test` on the Spring module, so Maven
# must be on PATH for that stage to succeed (see docs/06-spring-boot-rebuild.md).
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCENARIO="${1:-greenfield}"
case "$SCENARIO" in
  greenfield) CLASS=com.schwab.orchestrator.scenarios.GreenfieldScenario ;;
  brownfield) CLASS=com.schwab.orchestrator.scenarios.BrownfieldScenario ;;
  ambiguous)  CLASS=com.schwab.orchestrator.scenarios.AmbiguousScenario ;;
  *) echo "Unknown scenario '$SCENARIO' - expected greenfield|brownfield|ambiguous" >&2; exit 1 ;;
esac

scripts/compile.sh orchestrator/src/main/java orchestrator/bin
java -cp orchestrator/bin "$CLASS"
