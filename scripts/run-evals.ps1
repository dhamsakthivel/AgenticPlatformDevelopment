$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

if (-not $env:OPENAI_API_KEY -or -not $env:OPENAI_CHAT_MODEL) {
    throw "Set OPENAI_API_KEY and OPENAI_CHAT_MODEL before running the evals. Example: `$env:OPENAI_CHAT_MODEL = 'gpt-5-mini'"
}

.\mvnw.cmd -Peval test -pl platform-app -am
Write-Host "The report is in evals/reports/."
