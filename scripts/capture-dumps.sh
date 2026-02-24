#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

INTERVAL_MS="${1:-250}"
DURATION_MS="${2:-30000}"
LOG_FILE="${3:-build/logs/ygba-capture.log}"

echo "[capture] root=$ROOT_DIR"
echo "[capture] intervalMs=$INTERVAL_MS durationMs=$DURATION_MS logFile=$LOG_FILE"

./gradlew --no-daemon captureDumps \
  -PdumpIntervalMs="$INTERVAL_MS" \
  -PdumpDurationMs="$DURATION_MS" \
  -PlogFile="$LOG_FILE"
