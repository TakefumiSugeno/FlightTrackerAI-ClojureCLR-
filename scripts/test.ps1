$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  FlightTrackerAI - ClojureCLR Test & Coverage Pipeline" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. ソリューションビルド (依存アセンブリ最新化)
Write-Host "`n[1/3] .NET 10 ソリューションをビルドしています..." -ForegroundColor Yellow
dotnet build FlightTrackerAI.slnx --nologo -v q
if ($LASTEXITCODE -ne 0) {
    Write-Error "ビルドに失敗しました。"
    exit 1
}

# 2. CLOJURE_LOAD_PATH 設定
$env:CLOJURE_LOAD_PATH = "src/FlightTrackerAI.Core;test/FlightTrackerAI.Core.Tests;src/FlightTrackerAI.Infrastructure;test/FlightTrackerAI.Infrastructure.Tests;src/FlightTrackerAI.Web;test/FlightTrackerAI.Web.Tests;test"

# 3. テストランナー実行 (clojure.main test/test_runner.clj)
Write-Host "`n[2/3] ClojureCLR 全テストスイートを実行中..." -ForegroundColor Yellow
dotnet clojure.main -i test/test_runner.clj
$testExitCode = $LASTEXITCODE

# 4. レポート生成確認
Write-Host "`n[3/3] テストレポート出力検証..." -ForegroundColor Yellow
$latestDir = "doc/work/TestResults/latest"
$resultsPath = Join-Path $latestDir "TestResults.html"
$coveragePath = Join-Path $latestDir "CoverageReport.html"

# 最新の日時フォルダを取得して表示
$historyDirs = Get-ChildItem "doc/work/TestResults" -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^\d{8}-\d{6}$' } | Sort-Object Name -Descending
if ($historyDirs -and $historyDirs.Count -gt 0) {
    Write-Host "✔ 実行履歴レポート: $($historyDirs[0].FullName)" -ForegroundColor Cyan
}

if (Test-Path $resultsPath) {
    Write-Host "✔ テスト合否レポート (Latest): $resultsPath" -ForegroundColor Green
} else {
    Write-Warning "テスト合否レポートが生成されていません: $resultsPath"
}

if (Test-Path $coveragePath) {
    Write-Host "✔ カバレッジレポート (Latest): $coveragePath" -ForegroundColor Green
} else {
    Write-Warning "カバレッジレポートが生成されていません: $coveragePath"
}

if ($testExitCode -ne 0) {
    Write-Error "テスト実行で失敗またはエラーが検知されました。"
    exit $testExitCode
}

Write-Host "`n✔ 全テストパス & レポート生成完了！" -ForegroundColor Green
exit 0
