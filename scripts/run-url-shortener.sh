#!/usr/bin/env bash
# Starts the URL shortener HTTP service on :8080 (override with PORT env var).
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
scripts/compile.sh url-shortener/src/main/java url-shortener/bin
java -cp url-shortener/bin com.schwab.shortener.Main
