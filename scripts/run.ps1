param(
    [string]$Port = "5000",
    [switch]$NoBuild
)

$ErrorActionPreference = "Stop"

if (-not $NoBuild) {
    Write-Host "Building FlightTrackerAI solution..." -ForegroundColor Cyan
    dotnet build FlightTrackerAI.slnx --nologo -v q
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Build failed."
        exit 1
    }
}

$env:CLOJURE_LOAD_PATH = "$PSScriptRoot/../src/FlightTrackerAI.Core;$PSScriptRoot/../src/FlightTrackerAI.Infrastructure;$PSScriptRoot/../src/FlightTrackerAI.Web;$PSScriptRoot/../src/FlightTrackerAI.Web/bin/Debug/net10.0"

Write-Host "Starting FlightTrackerAI Web Server on http://localhost:$Port/ ..." -ForegroundColor Green
dotnet clojure.main -m flight-tracker-ai.web.server $Port
