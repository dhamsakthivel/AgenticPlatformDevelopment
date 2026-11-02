$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

if (-not $env:ADO_ORG_URL -or -not $env:ADO_PROJECT -or -not $env:ADO_PAT) {
    throw "Set ADO_ORG_URL, ADO_PROJECT, and ADO_PAT before seeding Azure DevOps."
}

.\mvnw.cmd -pl platform-integrations -am compile exec:java "-Dexec.mainClass=dev.qaagent.integrations.ado.SeedAdoStories"
