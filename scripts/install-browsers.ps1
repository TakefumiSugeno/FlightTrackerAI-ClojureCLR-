# Playwright ブラウザバイナリ (Chromium) のインストールスクリプト
$ErrorActionPreference = "Stop"

$rootDir = Split-Path -Parent $PSScriptRoot
Write-Host "Playwright Chromium ブラウザバイナリをインストールしています..." -ForegroundColor Cyan

dotnet build (Join-Path $rootDir "src/FlightTrackerAI.Web/FlightTrackerAI.Web.fsproj")
$playwrightScript = Join-Path $rootDir "src/FlightTrackerAI.Web/bin/Debug/net10.0/playwright.ps1"

if (Test-Path $playwrightScript) {
    pwsh $playwrightScript install chromium
    Write-Host "Chromium のインストールが正常に完了しました！" -ForegroundColor Green
} else {
    Write-Host "playwright.ps1 が見つかりませんでした。ビルド出力を確認してください。" -ForegroundColor Red
}
