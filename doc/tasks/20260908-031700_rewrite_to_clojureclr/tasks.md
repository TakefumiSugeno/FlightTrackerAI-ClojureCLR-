# タスク計画: ClojureCLR (.NET 10) へのシステム全面リライト

## 1. タスク概要

- **タスクID**: `20260908-031700_rewrite_to_clojureclr`
- **合意レベル**: L3 (大規模: ClojureCLR (.NET 10) へのアーキテクチャ再構築・全面リライト)
- **対象成果物**:
  - ドキュメント群: `request.md`, `AGENTS.md`, `doc/workflow.md`, `doc/spec.md`, `doc/design_detail.md`, `doc/mock/index.html`
  - CLI・プロジェクト構成: `deps.edn`, `dotnet-tools.json` (`Clojure.Cljr`, `clojure.main`), `FlightTrackerAI.slnx`
  - ソースコード: `src/FlightTrackerAI.Core/`, `src/FlightTrackerAI.Infrastructure/`, `src/FlightTrackerAI.Web/`
  - テストコード: `test/FlightTrackerAI.Core.Tests/`, `test/FlightTrackerAI.Infrastructure.Tests/`, `test/FlightTrackerAI.Web.Tests/`
  - テスト・ビルドスクリプト: `scripts/test.ps1`, `scripts/install-browsers.ps1`
  - レポート出力: `doc/work/TestResults/TestResults.html`, `doc/work/CoverageReport/index.html`

## 2. 作業項目 (Work Items)

- [x] **WI-01: ドキュメントおよび規約の ClojureCLR (.NET 10) 最適化更新 (Step 2)**
  - `AGENTS.md` の言語・命名規則・フォーマッタ記述を ClojureCLR (.NET 10) 仕様に更新
  - `request.md`, `doc/spec.md`, `doc/design_detail.md` の技術スタック、アーキテクチャ図、ドメイン定義、モジュール構成を ClojureCLR 仕様へ更新
  - UIモック (`doc/mock/index.html`) と ClojureCLR 実装の 1:1 整合性維持確認
  - 「ユーザー」「SE/PG」ロールによるドキュメントレビュー実施・合意

- [ ] **WI-02: ClojureCLR (.NET 10) プロジェクト構造・ビルド・テスト・レポート実行基盤の構築 (Step 3)**
  - `deps.edn` の定義および .NET ローカルツール `cljr` (`Clojure.Cljr`), `Clojure.Main` の配線
  - .NET 10 ソリューションおよびプロジェクト構成（C# ソースコード `.cs` は一切含まず、万一の代替時のみ F#）
  - `Clojure` (1.12.2) NuGet パッケージおよび依存ライブラリ (`Microsoft.Data.Sqlite`, `Microsoft.Playwright`) の導入
  - ソースコード (`src/`) とテストコード (`test/`) の 1:1 対応ディレクトリ構造確立
  - Clojure 製テストランナー (`test/test_runner.clj`) の構築
  - `scripts/test.ps1` の整備（`doc/work/TestResults/TestResults.html` および `doc/work/CoverageReport/index.html` を出力）

- [ ] **WI-03: `FlightTrackerAI.Core` の ClojureCLR 実装 & テスト (TDD, 1:1)**
  - `domain.clj` ⇔ `domain_tests.clj`:
    - IATAコード検証 (`create-iata-code`, `iata-code-value`)
    - 旅行タイプ (`one-way`, `round-trip`)
    - 乗継回数 (`direct-only`, `max-stops`, `any-stops`)
    - タスク状態 (`active`, `paused`, `running`, `completed`, `error`)、目標達成バッジ派生判定
    - プロバイダー (`google-flights`, `skyscanner`)
    - フライトセグメント、タスク、スナップショット、システム設定のデータ構造
  - `validation.clj` ⇔ `validation_tests.clj`:
    - タスク登録・更新パラメータ検証（出発地・目的地、日付前後関係、同日発着、目標価格0/負数境界値、巡回間隔、URL）
  - `analysis.clj` ⇔ `analysis_tests.clj`:
    - 最安値抽出、価格変動トレンド分析、最安提供プロバイダー判定、時差加味所要時間計算
    - 機械的エラーから人間向けエラー理由（タイムアウト、Bot検知等）への変換ロジック
  - `dto.clj` ⇔ `dto_tests.clj`:
    - SQLite用レコード / JSONシリアライズ変換

- [ ] **WI-04: `FlightTrackerAI.Infrastructure` の ClojureCLR 実装 & テスト (TDD, 1:1)**
  - `app_logger.clj` ⇔ `app_logger_tests.clj`: ログ出力
  - `scraper_common.clj` ⇔ `scraper_common_tests.clj`:
    - 安全な非同期 Task 解決ユーティリティ (`await-task`, `await-task-result`)
    - Playwright ブラウザコンテキスト・ステルス・アンチBot処理
    - テスト実行用分離プロファイルディレクトリ設定
  - `database.clj` ⇔ `database_tests.clj`: SQLite WALモード、外部キー制約、冪等マイグレーション実行
  - `settings_repository.clj` ⇔ `settings_repository_tests.clj`: システム全体設定 CRUD
  - `task_repository.clj` ⇔ `task_repository_tests.clj`: 監視タスク CRUD、状態遷移
  - `flight_repository.clj` ⇔ `flight_repository_tests.clj`: 便スナップショット・巡回ログ保存、履歴取得
  - `notification.clj` ⇔ `notification_tests.clj`: Discord / Slack Webhook 通知ペイロード生成・送信
  - `ai_client.clj` ⇔ `ai_client_tests.clj`: OpenRouter API 連携（自然言語パース、買い時分析要約、429/タイムアウト異常系処理）
  - `google_flights_scraper.clj` ⇔ `google_flights_scraper_tests.clj`: オフライン HTML フィクスチャを用いたパーサー単体テスト
  - `skyscanner_scraper.clj` ⇔ `skyscanner_scraper_tests.clj`: オフライン HTML フィクスチャを用いたパーサー単体テスト、2段階巡回
  - `scraping_worker.clj` ⇔ `scraping_worker_tests.clj`:
    - バックグラウンド定期巡回、`SemaphoreSlim` による排他制御テスト
    - Bot検知（PRESS & HOLD）時の有頭手動支援（最大60秒待機）とWebUI誘導状態通知連携
    - 自動完了・パージ処理

- [ ] **WI-05: `FlightTrackerAI.Web` の ClojureCLR 実装 & テスト (TDD, 1:1)**
  - `views/layout.clj` ⇔ `views/layout_tests.clj`: 基本レイアウト（Tailwind CSS, Chart.js, HTMX, トースト通知領域）
  - `views/dashboard.clj` ⇔ `views/dashboard_tests.clj`:
    - カード表示、Excel風リスト表示、フィルターバー、アクティブフィルタチップバー
    - AIアシスタント入力エリア（テンプレート挿入ボタン群）
    - 登録タスク0件時の空状態（Empty State）画面
    - 翌日到着 `(+1)` マークおよび JST タイムスタンプのレンダリング
  - `views/modals.clj` ⇔ `views/modals_tests.clj`:
    - 旅程タイムラインモーダル、システム設定モーダル、新規/編集モーダル
    - クイックメモ編集モーダル (`openQuickNoteModal`)
    - 手動支援ガイダンス通知（PRESS & HOLD 解除カウントダウン案内）
  - `controllers/api_controller.clj` ⇔ `controllers/api_controller_tests.clj`:
    - REST API (`/api/tasks`, `/api/settings`, `/api/ai/*`)
    - メモ部分更新 API (`PATCH /api/tasks/:id/notes`)
    - 即時巡回実行 API（排他ロック時の適切な待機メッセージ・トースト連携）
    - エラータスクの「今すぐ再試行」API
  - `server.clj` ⇔ `server_tests.clj`:
    - 100% ClojureCLR による HTTP サーバーホスト（System.Net.HttpListener）、ルーティング、静的ファイル配信

- [ ] **WI-06: 結合・E2Eテスト検証 & レポート確認**
  - `test/FlightTrackerAI.Web.Tests/integration/integration_flow_tests.clj`:
    - AI入力 ➔ タスク登録 ➔ スクレイピング結果保存 ➔ 分析 ➔ Webhook通知 ➔ UI取得の一連フロー検証
  - テストケース合否レポート (`doc/work/TestResults/TestResults.html`) の全件合格確認
  - コードカバレッジレポート (`doc/work/CoverageReport/index.html`) の 80% 以上達成確認

- [ ] **WI-07: ドキュメント事後同期 & ユーザー最終確認 (Step 4)**
  - 実装差分のドキュメント反映
  - 全成果物コミット、ユーザー最終合意取得

## 3. 受入基準 (Acceptance Criteria)

1. **言語・プラットフォーム**:
   - ソースコードおよびテストコードに C# (`.cs`) を一切含まず、全て ClojureCLR (.NET 10, `.clj`) で実装されていること（万が一 ClojureCLR で動作不可能な機能が生じた場合のみ F# を代替採用）。
   - .NET 10 ランタイム上で正常に実行可能であること。
2. **1:1 テスト対応**: `src/` 配下の全モジュールに対応するテストが `test/` 配下に 1:1 で配置されていること。
3. **テストレポート品質**:
   - `doc/work/TestResults/TestResults.html` で全テストが合格（OK）すること。
   - `doc/work/CoverageReport/index.html` でコードカバレッジが 80% 以上を維持していること（または全公開関数の正常・境界・異常系 100% 網羅）。
4. **仕様・UI適合性**:
   - 元リポジトリの機能要件（Google Flights/Skyscanner、SQLite WAL、OpenRouter AI、Webhook通知、カード/リスト表示、旅程タイムライン）を完全に満たしていること。
   - レビューで指摘された有頭手動支援UI連携、AIアシスタント登録フロー、クイックメモ編集、表示切替時のフィルタ状態同期、エラー再試行導線が実装されていること。
5. **レビュー完了**: サブエージェント批判的レビュー（ユーザーロール・SE/PGロール）で Critical / Major 指摘が全て解消されていること。
