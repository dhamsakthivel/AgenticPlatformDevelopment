#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "${OPENAI_API_KEY:-}" || -z "${OPENAI_CHAT_MODEL:-}" ]]; then
  echo "Set OPENAI_API_KEY and OPENAI_CHAT_MODEL before running the evals."
  echo "Example: export OPENAI_CHAT_MODEL=gpt-5-mini"
  exit 1
fi

./mvnw -Peval test -pl platform-app -am
echo "The report is in evals/reports/."
