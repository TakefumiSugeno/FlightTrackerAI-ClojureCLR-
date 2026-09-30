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
