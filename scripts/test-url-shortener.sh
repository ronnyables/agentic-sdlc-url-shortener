#!/usr/bin/env bash
# Runs the url-shortener's real (zero-dependency) test suite: unit tests for
# the encoder/service/rate-limiter plus a full-stack HTTP integration test.
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
scripts/compile.sh url-shortener/src/main/java url-shortener/bin
scripts/compile.sh url-shortener/src/test/java url-shortener/bin -cp url-shortener/bin
java -cp url-shortener/bin com.schwab.shortener.testkit.TestRunner \
  com.schwab.shortener.Base62EncoderTest \
  com.schwab.shortener.RateLimiterTest \
  com.schwab.shortener.UrlShortenerServiceTest \
  com.schwab.shortener.HttpApiIntegrationTest
