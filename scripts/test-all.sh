#!/usr/bin/env bash
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
scripts/test-url-shortener.sh
scripts/test-orchestrator.sh
