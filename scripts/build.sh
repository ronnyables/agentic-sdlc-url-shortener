#!/usr/bin/env bash
# Builds both modules. No Maven, no external dependencies, no internet
# access required - just a JDK 11+ on PATH (or JAVA_HOME set).
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> Building url-shortener (main + test)"
scripts/compile.sh url-shortener/src/main/java url-shortener/bin
scripts/compile.sh url-shortener/src/test/java url-shortener/bin -cp url-shortener/bin

echo "==> Building orchestrator (main + test)"
scripts/compile.sh orchestrator/src/main/java orchestrator/bin
scripts/compile.sh orchestrator/src/test/java orchestrator/bin -cp orchestrator/bin

echo "==> Build complete."
