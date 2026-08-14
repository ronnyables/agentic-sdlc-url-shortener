#!/usr/bin/env bash
# Starts the orchestrator's REST API on :8090 (override with PORT env var).
# Requires url-shortener/bin to already be built (the TestingAgent shells out
# to it), so this also builds the url-shortener module first.
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
scripts/compile.sh url-shortener/src/main/java url-shortener/bin
scripts/compile.sh url-shortener/src/test/java url-shortener/bin -cp url-shortener/bin
scripts/compile.sh orchestrator/src/main/java orchestrator/bin
java -cp "orchestrator/bin:url-shortener/bin" com.schwab.orchestrator.web.OrchestratorApiServer
