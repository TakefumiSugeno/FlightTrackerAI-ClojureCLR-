# レビュー記録: google-flights-scraping-spec / propose

- **日時**: 2026-10-01
- **フェーズ**: propose
- **レビュアー**: User Agent, SE Agent
- **対象成果物**: `proposal.md`, `specs/flight-search/spec.md`, `design.md`, `tasks.md`, `mockup.html`, `mockup_new_task.html`

## チェックリスト結果

| #   | 観点                                                         | 判定 | コメント                                                                |
| --- | ------------------------------------------------------------ | ---- | ----------------------------------------------------------------------- |
| 1   | Whatが明確かつ500語以内で記述されているか                    | ✅   | 2段階検索、便名抽出、同一便キー、1440x900キャプチャと整合性担保を明確化 |
| 2   | Why（なぜ変えるか・ビジネス価値）が明確か                    | ✅   | 時刻文字列誤認による800万円台パースの不整合解消                         |
| 3   | Non-goalsが明記されているか                                  | ✅   | Skyscannerの全面改修、AI分析モデル変更を除外                            |
| 4   | 受け入れ基準・完了の定義が具体的かつ検証可能か               | ✅   | `specs/` に SHALL 要件および WHEN/THEN シナリオを定義                   |
| 5   | 既存仕様との矛盾がないか                                     | ✅   | 既存のフライト追跡仕様を包含・拡張                                      |
| 6   | 影響範囲が整理されているか                                   | ✅   | Core, Infrastructure, Web, mock の影響を明記                            |
| 7   | UI変更時のモック対応                                         | ✅   | `doc/mock/index.html`, `standalone_new_task.html` 更新済み              |
| 8   | 移行計画が記載されているか                                   | ✅   | DB スキーマの ALTER TABLE 冪等追加とデフォルト値設定                    |
| 9   | アーキテクチャ・設計方針が妥当か                             | ✅   | 2段階 Playwright 操作フローと DOM セレクタ正規表現抽出                  |
| 10  | タスク分解粒度が「2時間以内」か                              | ✅   | ドメイン、スクレイパー、UI、E2E に細分化                                |
| 11  | 依存関係のあるタスクの順序が明示されているか                 | ✅   | ドメイン → スクレイパー → UI → E2E の順序で定義                         |
| 12  | テストファーストの手順（Red-Green-Refactor）が含まれているか | ✅   | 各タスクに Red/Green/Refactor を明記                                    |
| 13  | ソースとテストの 1:1 対応方針が維持されているか              | ✅   | 命名規則 `*_tests.clj` に完全準拠                                       |
| 14  | 非機能要件（タイムアウト、堅牢性）                           | ✅   | セレクタ待機、Cookieスキップ、Forceクリック                             |

## 指摘事項と対応（批判的レビューより引き継ぎ）

### 指摘 1 (User Agent: 時間レンジの指定UI)

- **内容**: スライダー入力は扱いにくいため、早朝/午前/午後/夜間等のプリセット選択とカスタム指定をサポートすべき。
- **重要度**: Major
- **対応方針**: 採用。UIモックおよびドメインにプリセット選択肢（`Any`, `EarlyMorning`, `Morning`, `Afternoon`, `Evening`）を導入。

### 指摘 2 (User Agent: 取得件数と画面キャプチャの整合性)

- **内容**: 上位10件取得時、最安値便（1位）がキャプチャ画像内に明確に見えていないと不整合に見える。
- **重要度**: Critical
- **対応方針**: 採用。安い順ソート完了後、ウィンドウサイズ 1440x900 で最安値便が見える状態で撮影することを設計・仕様に明記。

### 指摘 3 (SE Agent: セレクタ操作の安全性と価格正規表現)

- **内容**: 時刻誤認防止のため `span[aria-label*='円']` および `div.JMc5Xc[aria-label]` から `r"(\d+)\s*円"` で数値抽出すること。またポップアップ干渉防止のため `WaitForSelectorAsync` と `Force = true` を適用すること。
- **重要度**: Critical
- **対応方針**: 採用。`design.md` および `google_flights_scraper.clj` の実装計画に反映。

### 指摘 4 (SE Agent: 便名抽出と同一航空券トラッキング)

- **内容**: 特定便の価格推移を追跡するため、`itinerary` 属性から便名（例: `5J 5055`）を抽出し、`flight_key` を導入すること。
- **重要度**: Major
- **対応方針**: 採用。`specs/` および `design.md` に `flight_key = task_id + flight_numbers + outbound_date` を導入。

### 指摘 5 (User Agent: 新規登録画面 UI モック（モーダル/独立画面）の完全統一)

- **内容**: `mockup.html` と `mockup_new_task.html` でラベル名（必須マーク `*` 等）、タスク名(任意)の列幅（1列 vs 2列）、および「登録して巡回開始」ボタンのアイコン・スタイルに不一致があるため統一すること。
- **重要度**: Major
- **対応方針**: 採用。全ラベル名の一致、タスク名入力欄の 2 列全幅（`col-span-2`）化、飛行機アイコン付与およびボタンスタイルを完全統一。

## 判定

- **LGTM**: true
- **次のアクション**: Propose 合意（L2合意-2）を経て、Apply フェーズの実装（Step 1: ドメイン拡張）へ着手。

---

# レビュー記録: google-flights-scraping-spec / apply / task-1.1

- **日時**: 2026-10-01
- **フェーズ**: apply
- **タスク**: 1.1 `domain_tests.clj` に新規フィールド（`outbound_time_range`, `inbound_time_range`, `max_stops`, `max_results_count`, `flight_number`, `flight_key`）の仕様検証テストを追加し、テスト失敗を確認する (Red)
- **レビュアー**: QA Agent
- **対象成果物**: `test/FlightTrackerAI.Core.Tests/flight_tracker_ai/core/domain_tests.clj`

## チェックリスト結果

| #   | 観点                                                   | 判定 | コメント                                                                    |
| --- | ------------------------------------------------------ | ---- | --------------------------------------------------------------------------- |
| 1   | 仕様書の期待値がテストケースとしてコード化されているか | ✅   | 時間帯レンジ変換、`build-flight-key` 生成、設定拡張のテストを追加           |
| 2   | 正常系・異常系・境界値が考慮されているか               | ✅   | nil入力時のフォールバック（`Unknown`、`:any`）をテスト網羅                  |
| 3   | TDD Red状態が確認できているか                          | ✅   | `./scripts/test.ps1` で未定義シンボルによるコンパイル失敗・テスト失敗を確認 |
| 4   | ソースとテストの 1:1 対応が維持されているか            | ✅   | `domain.clj` ⇔ `domain_tests.clj` に準拠                                    |

## 判定

- **LGTM**: true
- **次のアクション**: Task 1.2（`domain.clj` への実装・Green化）へ着手

---

# レビュー記録: google-flights-scraping-spec / apply / task-1.2

- **日時**: 2026-10-01
- **フェーズ**: apply
- **タスク**: 1.2 `src/FlightTrackerAI.Core/flight_tracker_ai/core/domain.clj` および `dto.clj` に `Task`, `FlightOffer`, `SystemSettings` の各レコード・DTOマッピングを拡張し、単体テストを通す (Green/Refactor)
- **レビュアー**: PG Agent
- **対象成果物**: `src/FlightTrackerAI.Core/flight_tracker_ai/core/domain.clj`, `src/FlightTrackerAI.Core/flight_tracker_ai/core/dto.clj`, `test/FlightTrackerAI.Core.Tests/flight_tracker_ai/core/dto_tests.clj`

## チェックリスト結果

| #   | 観点                                                 | 判定 | コメント                                                                                                 |
| --- | ---------------------------------------------------- | ---- | -------------------------------------------------------------------------------------------------------- |
| 1   | 設計書（`design.md`）および仕様書との整合性          | ✅   | `time-range` 変換、`build-flight-key`、`max-results-count` 等が設計通り正確に反映されている              |
| 2   | コード品質・可読性・命名・DRY・ClojureCLR イディオム | ✅   | Clojureの純粋関数と不変データ構造を活用し、nil安全かつ簡潔に実装されている                               |
| 3   | 境界値・異常系・エラー処理の網羅                     | ✅   | 文字列変換での未知の値に対する `:any` / `Unknown` フォールバック、数値パースの既定値適用が実装されている |
| 4   | ソースとテストの 1:1 対応命名規約遵守                | ✅   | `domain.clj` ⇔ `domain_tests.clj`、`dto.clj` ⇔ `dto_tests.clj` が正しく対応                              |
| 5   | テスト通過（Green）の確認                            | ✅   | 全 22 テストスイート、583 アサーションが通過（Failure 0, Error 0）                                       |

## 判定

- **LGTM**: true
- **次のアクション**: Task 1.3（DBマイグレーションのテスト作成・Red確認）へ着手

---

# レビュー記録: google-flights-scraping-spec / apply / task-1.3

- **日時**: 2026-10-01
- **フェーズ**: apply
- **タスク**: 1.3 `database_tests.clj` にマイグレーション（新規カラム追加および冪等性）の検証テストを追加し、テスト失敗を確認する (Red)
- **レビュアー**: QA Agent
- **対象成果物**: `test/FlightTrackerAI.Infrastructure.Tests/flight_tracker_ai/infrastructure/database_tests.clj`

## チェックリスト結果

| #   | 観点                                                   | 判定 | コメント                                                                                                                                                                                                 |
| --- | ------------------------------------------------------ | ---- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | 仕様書の期待値がテストケースとしてコード化されているか | ✅   | `default_max_results_count`, `outbound_time_range`, `inbound_time_range`, `max_results_count`, `last_lowest_flight_number`, `flight_number`, `flight_key`, `idx_snapshots_flight_key` の検証テストを追加 |
| 2   | 正常系・異常系・境界値が考慮されているか               | ✅   | 新規作成時だけでなく、旧スキーマ（既存DBレコード保持）からのマイグレーション検証テストを網羅                                                                                                             |
| 3   | TDD Red状態が確認できているか                          | ✅   | 未定義カラム `default_max_results_count` による `SqliteException`（Error 2件）でRed状態を確認                                                                                                            |
| 4   | ソースとテストの 1:1 対応が維持されているか            | ✅   | `database.clj` ⇔ `database_tests.clj` に準拠                                                                                                                                                             |

## 判定

- **LGTM**: true
- **次のアクション**: Task 1.4（`database.clj` へのスキーマ拡張・マイグレーション実装とGreen化）へ着手

---

# レビュー記録: google-flights-scraping-spec / apply / task-1.4

- **日時**: 2026-10-01
- **フェーズ**: apply
- **タスク**: 1.4 `src/FlightTrackerAI.Infrastructure/flight_tracker_ai/infrastructure/database.clj` に SQLite DB スキーマ拡張・マイグレーション処理を実装し、テストを通す (Green/Refactor)
- **レビュアー**: PG Agent
- **対象成果物**: `src/FlightTrackerAI.Infrastructure/flight_tracker_ai/infrastructure/database.clj`

## チェックリスト結果

| #   | 観点                                                 | 判定 | コメント                                                                                                    |
| --- | ---------------------------------------------------- | ---- | ----------------------------------------------------------------------------------------------------------- |
| 1   | 設計書（`design.md`）および仕様書との整合性          | ✅   | `tasks`, `flight_snapshots`, `system_settings` への各カラム追加およびインデックス作成が完全に反映されている |
| 2   | コード品質・可読性・命名・DRY・ClojureCLR イディオム | ✅   | `column-exists?` を用いた冪等な ALTER TABLE 実行、およびインデックス作成順序の安全設計がなされている        |
| 3   | 境界値・異常系・エラー処理の網羅                     | ✅   | 既存DBの旧スキーマからの安全な移行とデフォルト値設定が動作確認済み                                          |
| 4   | ソースとテストの 1:1 対応命名規約遵守                | ✅   | `database.clj` ⇔ `database_tests.clj` に準拠                                                                |
| 5   | テスト通過（Green）の確認                            | ✅   | 全 22 テストスイート、593 アサーションが通過（Failure 0, Error 0）                                          |

## 判定

- **LGTM**: true
- **次のアクション**: Task 2.1（Google Flights スクレイパーのオフライン単体テスト作成・Red確認）へ着手

---

# レビュー記録: google-flights-scraping-spec / apply / task-2.1

- **日時**: 2026-10-01
- **フェーズ**: apply
- **タスク**: 2.1 `google_flights_scraper_tests.clj` に実DOM HTMLサンプル（`doc/work/GoogleFlightサンプル/`）に基づくオフライン単体テスト（`li.pIav2d` からの価格パース、時刻誤認防止、`itinerary` からの便名抽出、`flight_key` 算出）を追加し、テスト失敗を確認する (Red)
- **レビュアー**: QA Agent
- **対象成果物**: `test/FlightTrackerAI.Infrastructure.Tests/flight_tracker_ai/infrastructure/google_flights_scraper_tests.clj`

## チェックリスト結果

| #   | 観点                                                   | 判定 | コメント                                                                                                                                  |
| --- | ------------------------------------------------------ | ---- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | 仕様書の期待値がテストケースとしてコード化されているか | ✅   | `itinerary` からの便名抽出（直行便・乗継便）、正規表現による価格パースと時刻誤認防止、`flight_key` 算出、実HTMLファイル解析のテストを追加 |
| 2   | 正常系・異常系・境界値が考慮されているか               | ✅   | 時刻文字列（`8:50 – 15:15` や `12:50発 17:30着`）、空値・nilに対するガード、800万円台誤認が発生しないことを検証                           |
| 3   | TDD Red状態が確認できているか                          | ✅   | `No such var: gf/parse-flight-number-from-itinerary` によるコンパイル失敗・Red状態を確認                                                  |
| 4   | ソースとテストの 1:1 対応が維持されているか            | ✅   | `google_flights_scraper.clj` ⇔ `google_flights_scraper_tests.clj` に準拠                                                                  |

## 判定

- **LGTM**: true
- **次のアクション**: Task 2.2（`google_flights_scraper.clj` への価格パース・便名抽出・オファー生成ロジック実装とGreen化）へ着手

---

# レビュー記録: google-flights-scraping-spec / apply / task-2.2

- **日時**: 2026-10-01
- **フェーズ**: apply
- **タスク**: 2.2 `src/FlightTrackerAI.Infrastructure/flight_tracker_ai/infrastructure/google_flights_scraper.clj` に価格パース正規表現、便名抽出、オファー生成ロジックを実装し、単体テストを通す (Green)
- **レビュアー**: PG Agent
- **対象成果物**: `src/FlightTrackerAI.Infrastructure/flight_tracker_ai/infrastructure/google_flights_scraper.clj`, `src/FlightTrackerAI.Core/flight_tracker_ai/core/domain.clj`

## チェックリスト結果

| #   | 観点                                                 | 判定 | コメント                                                                                                                                 |
| --- | ---------------------------------------------------- | ---- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | 設計書（`design.md`）および仕様書との整合性          | ✅   | `parse-flight-number-from-itinerary`, `parse-google-flights-price`, `extract-cards-from-html`, `flight-key` 生成が設計通り実装されている |
| 2   | コード品質・可読性・命名・DRY・ClojureCLR イディオム | ✅   | マルチアリティ `parse-offer-element` による後方互換性維持、正規表現の明確化、ロケール非依存の ISO 日付フォーマット適用                   |
| 3   | 境界値・異常系・エラー処理の網羅                     | ✅   | 時刻混在テキスト（`8:50 – 15:15` 等）での価格誤認防止、nil・空文字セーフ、異常値（800万円台等）の防止が検証済み                          |
| 4   | ソースとテストの 1:1 対応命名規約遵守                | ✅   | `google_flights_scraper.clj` ⇔ `google_flights_scraper_tests.clj` に準拠                                                                 |
| 5   | テスト通過（Green）の確認                            | ✅   | 全 22 テストスイート、617 アサーションが通過（Failure 0, Error 0）                                                                       |

## 判定

- **LGTM**: true
- **次のアクション**: Task 2.3（Playwright 2段階検索のモック/結合テスト作成・Red確認）へ着手

---

# レビュー記録: google-flights-scraping-spec / apply / task-2.3

- **日時**: 2026-10-01
- **フェーズ**: apply
- **タスク**: 2.3 `google_flights_scraper_tests.clj` に Playwright 2段階検索（基本URLアクセス、経由地数フィルターダイアログ操作、安い順ソート、Viewport 1440x900 定義、最安値キャプチャ）のモック/結合テストを追加する (Red)
- **レビュアー**: QA Agent
- **対象成果物**: `test/FlightTrackerAI.Infrastructure.Tests/flight_tracker_ai/infrastructure/google_flights_scraper_tests.clj`

## チェックリスト結果

| #   | 観点                                                   | 判定 | コメント                                                                                                                         |
| --- | ------------------------------------------------------ | ---- | -------------------------------------------------------------------------------------------------------------------------------- |
| 1   | 仕様書の期待値がテストケースとしてコード化されているか | ✅   | Viewport 1440x900 定義、経由地数フィルター・安い順ソートヘルパーのnil安全性、最大取得件数（`max-results-count`）制限テストを追加 |
| 2   | 正常系・異常系・境界値が考慮されているか               | ✅   | nil ページ受け取り時の例外スルー防止と、指定件数通りの抽出制限を検証                                                             |
| 3   | TDD Red状態が確認できているか                          | ✅   | `No such var: gf/viewport-width` によるコンパイル失敗・Red状態を確認                                                             |
| 4   | ソースとテストの 1:1 対応が維持されているか            | ✅   | `google_flights_scraper.clj` ⇔ `google_flights_scraper_tests.clj` に準拠                                                         |

## 判定

- **LGTM**: true
- **次のアクション**: Task 2.4（`google_flights_scraper.clj` への 2段階 Playwright 巡回・フィルター操作・キャプチャ保存フロー実装とGreen化）へ着手

---

# レビュー記録: google-flights-scraping-spec / apply / task-2.4

- **日時**: 2026-10-01
- **フェーズ**: apply
- **タスク**: 2.4 `src/FlightTrackerAI.Infrastructure/flight_tracker_ai/infrastructure/google_flights_scraper.clj` に 2段階 Playwright 巡回・フィルター操作・キャプチャ保存フローを実装し、テストを通す (Green/Refactor)
- **レビュアー**: PG Agent
- **対象成果物**: `src/FlightTrackerAI.Infrastructure/flight_tracker_ai/infrastructure/google_flights_scraper.clj`, `src/FlightTrackerAI.Infrastructure/flight_tracker_ai/infrastructure/flight_repository.clj`

## チェックリスト結果

| #   | 観点                                                 | 判定 | コメント                                                                                                                                                      |
| --- | ---------------------------------------------------- | ---- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | 設計書（`design.md`）および仕様書との整合性          | ✅   | Viewport 1440x900、経由地数フィルター（`DirectOnly`, `OneStop`）、安い順ソート、`max-results-count` 制限、`flight_key` 付与、キャプチャ保存が設計通り完全実装 |
| 2   | コード品質・可読性・命名・DRY・ClojureCLR イディオム | ✅   | 非同期操作の `scraper-common/await-task`、ポーリング待機、nil安全なヘルパー関数分割、クリーンなエラーハンドリング                                             |
| 3   | 境界値・異常系・エラー処理の網羅                     | ✅   | ページ遷移失敗・要素待機タイムアウト・キャプチャ失敗時の安全なフォールバック、DB永続化での `flight_number`/`flight_key` 保存                                  |
| 4   | ソースとテストの 1:1 対応命名規約遵守                | ✅   | `google_flights_scraper.clj` ⇔ `google_flights_scraper_tests.clj`、`flight_repository.clj` ⇔ `flight_repository_tests.clj` に準拠                             |
| 5   | テスト通過（Green）の確認                            | ✅   | 全 22 テストスイート、622 アサーションが通過（Failure 0, Error 0）                                                                                            |

## 判定

- **LGTM**: true
- **次のアクション**: Task 3.1（Web UI / API 連携のレンダリングテスト作成・Red確認）へ着手

---

# レビュー記録: google-flights-scraping-spec / apply / task-3.1

- **日時**: 2026-10-01
- **フェーズ**: apply
- **タスク**: 3.1 `task_views_tests.clj` に新規登録フォーム（時間レンジ、経由地数、取得件数）および一覧・詳細での便名表示のレンダリングテストを追加する (Red)
- **レビュアー**: QA Agent
- **対象成果物**: `test/FlightTrackerAI.Web.Tests/flight_tracker_ai/web/views/task_views_tests.clj`

## チェックリスト結果

| #   | 観点                                                   | 判定 | コメント                                                                                                                                     |
| --- | ------------------------------------------------------ | ---- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | 仕様書の期待値がテストケースとしてコード化されているか | ✅   | 時間帯レンジ選択肢（`Morning`, `Evening` 等）、経由地数（`maxStops`）、巡回取得件数（`maxResultsCount`）、および便名バッジ表示のテストを追加 |
| 2   | 正常系・異常系・境界値が考慮されているか               | ✅   | nil・空文字便名時の nil 返却、複数レンジ属性の属性値検証                                                                                     |
| 3   | TDD Red状態が確認できているか                          | ✅   | `task_views` 未作成による `FileNotFoundException` でRed状態を確認                                                                            |
| 4   | ソースとテストの 1:1 対応が維持されているか            | ✅   | `task_views.clj` ⇔ `task_views_tests.clj` の 1:1 対応を維持                                                                                  |

## 判定

- **LGTM**: true
- **次のアクション**: Task 3.2（`task_views.clj` および `modals.clj`, `api_controller.clj` への UI 実装とパラメータ連携）へ着手

---

# レビュー記録: google-flights-scraping-spec / apply / task-3.2

- **日時**: 2026-10-01
- **フェーズ**: apply
- **タスク**: 3.2 `src/FlightTrackerAI.Web/flight_tracker_ai/web/views/task_views.clj` および `modals.clj`, `dashboard.clj`, `api_controller.clj`, `task_repository.clj` を更新し、HTMLモック（`doc/mock/`）に準拠した UI レンダリングとパラメータ受け渡しを実装する (Green)
- **レビュアー**: PG Agent
- **対象成果物**: `src/FlightTrackerAI.Web/flight_tracker_ai/web/views/task_views.clj`, `src/FlightTrackerAI.Web/flight_tracker_ai/web/views/modals.clj`, `src/FlightTrackerAI.Web/flight_tracker_ai/web/views/dashboard.clj`, `src/FlightTrackerAI.Web/flight_tracker_ai/web/controllers/api_controller.clj`, `src/FlightTrackerAI.Infrastructure/flight_tracker_ai/infrastructure/task_repository.clj`, `src/FlightTrackerAI.Core/flight_tracker_ai/core/domain.clj`

## チェックリスト結果

| #   | 観点                                                                               | 判定 | コメント                                                                                                                                                                 |
| --- | ---------------------------------------------------------------------------------- | ---- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 1   | 設計書（`design.md`）および UIモック（`doc/mock/` または `mockup.html`）との整合性 | ✅   | `mockup_new_task.html` & `mockup.html` に完全準拠し、時間帯レンジ・経由地数・巡回時取得件数コントロール、タスク名入力欄の 2 列全幅（`col-span-2`）、便名バッジ表示を実装 |
| 2   | コード品質・可読性・命名・DRY・ClojureCLR イディオム                               | ✅   | `task-views` によるフォーム部品・便名バッジのモジュール分割、`domain` での変換関数エイリアス（`time-range->string`, `string->time-range`）、クリーンなパラメータパース   |
| 3   | 境界値・異常系・エラー処理の網羅                                                   | ✅   | 片道選択時の到着時間帯非表示、便名nil/空文字時のフォールバック、数値パース失敗時の既定値適用、DB永続化でのNULLセーフを網羅                                               |
| 4   | ソースとテストの 1:1 対応命名規約遵守                                              | ✅   | `task_views.clj` ⇔ `task_views_tests.clj`、`api_controller.clj` ⇔ `api_controller_tests.clj` の対応を遵守                                                                |
| 5   | テスト通過（Green）の確認                                                          | ✅   | 全 23 テストスイート、645 アサーションが通過（Failure 0, Error 0）                                                                                                       |

## 判定

- **LGTM**: true
- **次のアクション**: Task 3.3（自動フォーマットの実行と静的検証確認）へ着手

---

# レビュー記録: google-flights-scraping-spec / apply / task-3.3

- **日時**: 2026-10-01
- **フェーズ**: apply
- **タスク**: 3.3 自動フォーマット（`dotnet format`, `npx prettier --write`）を実行し、静的検証エラーがないことを確認する
- **レビュアー**: PG Agent
- **対象成果物**: プロジェクト全体、`openspec/`、`doc/mock/`

## チェックリスト結果

| #   | 観点                                     | 判定 | コメント                                                                                              |
| --- | ---------------------------------------- | ---- | ----------------------------------------------------------------------------------------------------- |
| 1   | dotnet format によるフォーマット検証     | ✅   | `FlightTrackerAI.slnx` に対して実行、構文エラー・未フォーマット問題なし                               |
| 2   | Prettier による Markdown / HTML 検証     | ✅   | `openspec/**/*.md`, `openspec/**/*.html`, `doc/mock/**/*.{html,css}` に対して Prettier 実行・整形完了 |
| 3   | OpenSpec アーティファクトの構文検証      | ✅   | `openspec validate google-flights-scraping-spec` パス（valid）                                        |
| 4   | 全テストスイートの通過状態が維持されたか | ✅   | テストスイート 23 件、645 アサーション全 Green                                                        |

## 判定

- **LGTM**: true
- **次のアクション**: Step 4（E2E 検証・カバレッジ達成およびレポート確認）へ着手

---

# レビュー記録: google-flights-scraping-spec / apply / task-4.1-4.2

- **日時**: 2026-10-01
- **フェーズ**: apply
- **タスク**: 4.1 E2E テストを実行し上位10件データ取得とキャプチャ最安値金額の一致検証、4.2 全テスト通過およびコードカバレッジ 80% 以上の確認
- **レビュアー**: QA Agent
- **対象成果物**: `test/FlightTrackerAI.Web.Tests/flight_tracker_ai/web/integration/integration_flow_tests.clj`, `doc/work/TestResults/latest/TestResults.html`, `doc/work/TestResults/latest/CoverageReport.html`

## チェックリスト結果

| #   | 観点                                                 | 判定 | コメント                                                                                                                                                |
| --- | ---------------------------------------------------- | ---- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | 仕様妥当性検証テスト（E2Eシナリオ）                  | ✅   | ゼロ設定・初回起動（0件）➔ 新規登録（時間帯レンジ・経由地・取得件数）➔ 上位10件抽出 ➔ 最安値便名・価格特定 ➔ ダッシュボード・詳細モーダル表示を完全網羅 |
| 2   | 回帰テスト                                           | ✅   | 既存の全23テストスイート、656アサーションがすべて成功（Failure 0, Error 0）                                                                             |
| 3   | ゼロ設定・初回起動検証                               | ✅   | 初期状態0件時に「監視中のタスクはありません」および案内ボタンが正しくレンダリングされることをアサーション検証                                           |
| 4   | 上位10件取得 & キャプチャ最安値とデータの一致確認    | ✅   | 実DOMサンプルから上位10件が抽出され、最安値（¥33,650）、最安便名（5J 5055）、航空会社（セブパシフィック航空）がキャプチャ・データ間で100%一致を検証     |
| 5   | UIモック再現性・エビデンス（便名バッジ・最安値表示） | ✅   | ダッシュボードカードおよび詳細モーダルの便名列・便名バッジ（`5J 5055`）、最安値表示、目標達成バッジがデータと完全に同期して表示されることを検証         |
| 6   | カバレッジ基準（80%以上）達成・エビデンス出力済みか  | ✅   | `./scripts/test.ps1` 実行により最新レポート出力完了。全体コードカバレッジ **94.9%** を達成（基準 80% を大幅クリア、全モジュール 90% 以上）              |

## 判定

- **LGTM**: true
- **次のアクション**: Apply フェーズ完了。Archive フェーズ（Phase 3: specs 同期、総括レビュー、アーカイブ）へ進む準備完了。

---

# レビュー記録: google-flights-scraping-spec / archive

- **日時**: 2026-10-01
- **フェーズ**: archive
- **対象成果物**: 変更全体の成果物（`openspec/specs/flight-search/spec.md`, `src/`, `test/`, `doc/mock/`, `handover.md`）
- **レビュアー**: User Agent, SE Agent, PG Agent, QA Agent（全役割）

## 1. デルタ仕様のメイン仕様への反映

| #   | 観点                                                                                        | 判定 | コメント                                                                                    |
| --- | ------------------------------------------------------------------------------------------- | ---- | ------------------------------------------------------------------------------------------- |
| 1   | `openspec sync specs` が実行済みで、デルタ仕様が `openspec/specs/` に正しく反映されているか | ✅   | `openspec/specs/flight-search/spec.md` に 4 つの ADDED Requirements が正しく同期・作成済み  |
| 2   | メイン仕様の整合性（矛盾・重複・抜け）がないか                                              | ✅   | 2段階検索、便名抽出、同一航空券キー、価格厳格パース、取得件数遵守の全要件が矛盾なく定義済み |
| 3   | 仕様変更の履歴・理由が追跡可能か                                                            | ✅   | proposal, design, spec delta, commit ログにより変更理由（価格誤認解消・便名追跡）が追跡可能 |

## 2. テスト・品質エビデンス

| #   | 観点                                                                                             | 判定 | コメント                                                                |
| --- | ------------------------------------------------------------------------------------------------ | ---- | ----------------------------------------------------------------------- |
| 4   | 全テスト（単体・結合・E2E）が通過しているか (`./scripts/test.ps1`)                               | ✅   | 全 23 テストスイート、656 アサーションがすべて成功（Fail: 0, Error: 0） |
| 5   | カバレッジ基準（80%以上）を達成しているか                                                        | ✅   | 全体コードカバレッジ **94.9%** を達成（全モジュール 90% 以上）          |
| 6   | テスト合否レポート・カバレッジレポート（`doc/work/TestResults/latest/`）が確認・記録されているか | ✅   | `TestResults.html` および `CoverageReport.html` 出力確認・検証完了      |
| 7   | .NETソリューションビルド・コンパイルがエラーなしで通過しているか                                 | ✅   | 警告 0、エラー 0 でビルド成功                                           |

## 3. UIモック・ドキュメント同期

| #   | 観点                                                                                           | 判定 | コメント                                                                                             |
| --- | ---------------------------------------------------------------------------------------------- | ---- | ---------------------------------------------------------------------------------------------------- |
| 8   | **UIモック同期**: UI変更があった場合、`doc/mock/` 配下の最新モックが確定成果物と整合しているか | ✅   | `mockup_new_task.html` & `mockup.html` と実装（フォーム部品、タスク名2列全幅、便名バッジ等）完全一致 |
| 9   | README・補足資料等の関連ドキュメントが同期更新済みか                                           | ✅   | 仕様・成果物は OpenSpec 正本および `handover.md` に同期整理済み                                      |
| 10  | README・CHANGELOG・移行ガイド等のユーザー向けドキュメントが更新されているか                    | ✅   | `handover.md` に移行手順（自動マイグレーション）とロールバック手順を文書化                           |
| 11  | API仕様・インターフェース定義書等が実装と整合しているか                                        | ✅   | API コントローラーおよび DTO マッピングと整合                                                        |

## 4. 技術的負債・既知の課題・申し送り事項

| #   | 観点                                                           | 判定 | コメント                                                                        |
| --- | -------------------------------------------------------------- | ---- | ------------------------------------------------------------------------------- |
| 12  | 既知の課題・技術的負債が `handover.md` に整理記録されているか  | ✅   | Google Flights DOM クラス変更への追従、Skyscanner への横展開を記録              |
| 13  | Applyフェーズでの保留指摘が `handover.md` に引き継がれているか | ✅   | 保留指摘なし、将来課題として `handover.md` に整理                               |
| 14  | 将来的な改善・リファクタ候補が整理されているか                 | ✅   | Skyscanner便名抽出、セレクタ外部設定化等を明記                                  |
| 15  | 運用・監視・ログ・メトリクスの観点で追加すべき項目がないか     | ✅   | ヘッドレス/有頭ブラウザ巡回ログ、キャプチャ保存ログ（1440x900）が整備されている |

## 5. 変更サマリ・リリース可否

| #   | 観点                                                                           | 判定 | コメント                                                          |
| --- | ------------------------------------------------------------------------------ | ---- | ----------------------------------------------------------------- |
| 16  | 変更サマリ（何が変わったか・なぜ・影響範囲・テスト結果）が記録されているか     | ✅   | `handover.md` および本レビュー記録に詳細記録済み                  |
| 17  | レビュー指摘の対応状況（採用/保留/却下・理由・対応コミット）が網羅されているか | ✅   | Propose / Apply 全タスクのレビュー指摘が解消されコミット済み      |
| 18  | 破壊的変更がある場合、移行手順・ロールバック手順が文書化されているか           | ✅   | NULL許容カラム追加による後方互換性維持、移行手順を文書化          |
| 19  | セキュリティ・プライバシー・コンプライアンスの観点で懸念がないか               | ✅   | APIキー等の秘密情報漏洩なし、テストエビデンスは Git 除外済み      |
| 20  | パフォーマンス・スケーラビリティへの影響が許容範囲内か                         | ✅   | SQLite インデックス追加（`idx_snapshots_flight_key`）により最適化 |
| 21  | 自動フォーマットが全対象ファイルに適用されているか                             | ✅   | `dotnet format`, Prettier 実行済み                                |

## 総合判定

- **LGTM**: true
- **次のアクション**: ユーザー最終合意確認後、`openspec archive` を実行しリモートリポジトリへ push。
