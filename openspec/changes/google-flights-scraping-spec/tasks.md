# Tasks: Google Flights スクレイピング仕様の刷新と不整合修正

## 1. ドメインおよびデータアクセスの拡張 (TDD)

- [ ] 1.1 `domain_tests.clj` に新規フィールド（`outbound_time_range`, `inbound_time_range`, `max_stops`, `max_results_count`, `flight_number`, `flight_key`）の仕様検証テストを追加し、テスト失敗を確認する (Red)
- [ ] 1.2 `src/FlightTrackerAI.Core/flight_tracker_ai/core/domain.clj` に `Task`, `FlightOffer`, `FlightSnapshot` の各レコードおよびファクトリ関数を拡張し、単体テストを通す (Green/Refactor)
- [ ] 1.3 `database_tests.clj` にマイグレーション（新規カラム追加および冪等性）の検証テストを追加し、テスト失敗を確認する (Red)
- [ ] 1.4 `src/FlightTrackerAI.Infrastructure/flight_tracker_ai/infrastructure/database.clj` に SQLite DB スキーマ拡張・マイグレーション処理を実装し、テストを通す (Green/Refactor)

## 2. Google Flights スクレイパーの全面改訂 (TDD)

- [ ] 2.1 `google_flights_scraper_tests.clj` に実DOM HTMLサンプル（`doc/work/GoogleFlightサンプル/`）に基づくオフライン単体テスト（`li.pIav2d` からの価格パース、時刻誤認防止、`itinerary` からの便名抽出、`flight_key` 算出）を追加し、テスト失敗を確認する (Red)
- [ ] 2.2 `src/FlightTrackerAI.Infrastructure/flight_tracker_ai/infrastructure/google_flights_scraper.clj` に価格パース正規表現、便名抽出、オファー生成ロジックを実装し、単体テストを通す (Green)
- [ ] 2.3 `google_flights_scraper_tests.clj` に Playwright 2段階検索（基本URLアクセス、経由地数フィルターダイアログ操作、安い順ソート、Viewport 1440x900 定義、最安値キャプチャ）のモック/結合テストを追加する (Red)
- [ ] 2.4 `src/FlightTrackerAI.Infrastructure/flight_tracker_ai/infrastructure/google_flights_scraper.clj` に 2段階 Playwright 巡回・フィルター操作・キャプチャ保存フローを実装し、テストを通す (Green/Refactor)

## 3. Web UI / API 連携および結合検証

- [ ] 3.1 `task_views_tests.clj` に新規登録フォーム（時間レンジ、経由地数、取得件数）および一覧・詳細での便名表示のレンダリングテストを追加する (Red)
- [ ] 3.2 `src/FlightTrackerAI.Web/flight_tracker_ai/web/views/task_views.clj` および `routes.clj` を更新し、HTMLモック（`doc/mock/`）に準拠した UI レンダリングとパラメータ受け渡しを実装する (Green)
- [ ] 3.3 自動フォーマット（`dotnet format`, `npx prettier --write`）を実行し、静的検証エラーがないことを確認する

## 4. E2E 検証・カバレッジ達成およびレポート確認

- [ ] 4.1 E2E テストを実行し、上位10件のフライトデータ取得と保存された画面キャプチャの最安値金額が 100% 一致することを検証する
- [ ] 4.2 `./scripts/test.ps1` を実行し、全テスト通過（`doc/work/TestResults/latest/TestResults.html`）およびコードカバレッジ 80% 以上（`doc/work/TestResults/latest/CoverageReport.html`）を確認する
