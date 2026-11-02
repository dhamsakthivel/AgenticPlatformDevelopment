#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "${ADO_ORG_URL:-}" || -z "${ADO_PROJECT:-}" || -z "${ADO_PAT:-}" ]]; then
  echo "Set ADO_ORG_URL, ADO_PROJECT, and ADO_PAT before seeding Azure DevOps." >&2
  exit 1
fi

./mvnw -pl platform-integrations -am compile exec:java -Dexec.mainClass=dev.qaagent.integrations.ado.SeedAdoStories
