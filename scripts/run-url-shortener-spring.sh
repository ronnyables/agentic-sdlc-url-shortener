#!/usr/bin/env bash
# Starts the Spring Boot URL shortener on :8080 (embedded H2 by default).
# Pass "postgres" as $1 to use the postgres profile (requires `docker compose up -d` first).
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/url-shortener-spring"
if [ "${1:-}" = "postgres" ]; then
  mvn spring-boot:run -Dspring-boot.run.profiles=postgres
else
  mvn spring-boot:run
fi
