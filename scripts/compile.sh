#!/usr/bin/env bash
# Compiles a source tree into an output directory. Uses `javac` if it's on
# PATH (the normal case on any real JDK install). Falls back to invoking the
# compiler through the javax.tools API (via Compile.java) for the rare case
# of a JRE-like runtime that ships jdk.compiler but no standalone `javac`
# binary - this is exactly the situation this project was built and verified
# under (see ../docs/04-testing-and-tradeoffs.md, "Build Environment Note").
set -euo pipefail
SRC_DIR="$1"
OUT_DIR="$2"
shift 2 || true
mkdir -p "$OUT_DIR"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if command -v javac >/dev/null 2>&1; then
  find "$SRC_DIR" -name '*.java' > /tmp/_sources_$$.txt
  javac -d "$OUT_DIR" "$@" @/tmp/_sources_$$.txt
  rm -f /tmp/_sources_$$.txt
else
  echo "javac not found on PATH; falling back to embedded-compiler mode via Compile.java" >&2
  java "$SCRIPT_DIR/Compile.java" "$SRC_DIR" "$OUT_DIR" "$@"
fi
