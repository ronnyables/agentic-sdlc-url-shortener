#!/usr/bin/env bash
# Usage: scripts/run-scenario.sh [greenfield|brownfield|ambiguous]
# Runs one of the three end-to-end orchestration scenarios and writes a full
# execution trace to docs/generated/.
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCENARIO="${1:-greenfield}"
case "$SCENARIO" in
  greenfield) CLASS=com.schwab.orchestrator.scenarios.GreenfieldScenario ;;
  brownfield) CLASS=com.schwab.orchestrator.scenarios.BrownfieldScenario ;;
  ambiguous)  CLASS=com.schwab.orchestrator.scenarios.AmbiguousScenario ;;
  *) echo "Unknown scenario '$SCENARIO' - expected greenfield|brownfield|ambiguous" >&2; exit 1 ;;
esac

scripts/compile.sh url-shortener/src/main/java url-shortener/bin
scripts/compile.sh url-shortener/src/test/java url-shortener/bin -cp url-shortener/bin
scripts/compile.sh orchestrator/src/main/java orchestrator/bin
java -cp "orchestrator/bin:url-shortener/bin" "$CLASS"
