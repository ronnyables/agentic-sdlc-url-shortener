#!/usr/bin/env bash
# Builds the Spring Boot module. Requires Maven + internet access (unlike
# the rest of scripts/, which need only a JDK) - see docs/06-spring-boot-rebuild.md.
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mvn -f url-shortener-spring/pom.xml -q compile
echo "Built url-shortener-spring."
