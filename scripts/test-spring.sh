#!/usr/bin/env bash
# Runs the Spring Boot module's full test suite (unit + web-slice + repository
# + full-stack integration tests) via Maven Surefire. Requires Maven.
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mvn -f url-shortener-spring/pom.xml test
