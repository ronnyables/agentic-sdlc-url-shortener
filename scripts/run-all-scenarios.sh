#!/usr/bin/env bash
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
scripts/run-scenario.sh greenfield
scripts/run-scenario.sh brownfield
scripts/run-scenario.sh ambiguous
