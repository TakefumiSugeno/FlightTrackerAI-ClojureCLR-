# Proposal: Google Flights スクレイピング仕様の刷新と不整合修正

## Why

Google Flights の自動巡回において、スクリーンショット上の金額とスクレイピングされた金額が一致しない重大な不整合（時刻文字列 `8:50 – 15:15` を誤認して `8,501,515円` とパースするバグ等）が発生していました。
提供された実画面 HTML サンプルの DOM 解析結果に基づき、2段階検索（基本条件入力＋画面上フィルター操作）、料金安い順ソート、便名（Flight Number）の完全抽出、および同一航空券判定キー（`flight_key`）を導入することで、取得データとキャプチャ画像の完全一致および安定した価格推移追跡を実現します。

## What Changes

- **2段階検索 & 画面上フィルター操作**:
  - 1段階目: 出発地・目的地・出発日/到着日および時間帯レンジ（`outbound_time_range`, `inbound_time_range`）による URL 遷移。
  - 2段階目: 画面上の経由地数フィルター（`max_stops`: 指定なし / 直行便のみ / 1箇所まで）のクリック適用。
  - ソート順: 「料金が安い順」を選択・適用。
- **便名（Flight Number）の完全抽出 & 同一便トラッキング**:
  - カード要素の `itinerary` 属性から便名（例: `5J 5055`, `NH 869`）を抽出し、乗継便は `➔` で連結。
  - 同一航空券判定キー（`flight_key = task_id + flight_numbers + outbound_date`）により、特定便の価格変動を100%確実に追跡。
- **パースの堅牢化 & 整合性キャプチャ**:
  - `li.pIav2d` 要素および `span[aria-label*='円']` / `div.JMc5Xc[aria-label]` から正規表現 `r"(\d+)\s*円"` で価格を抽出し、時刻の誤取得を完全排除。
  - 画面キャプチャを固定ウィンドウサイズ（1440x900）で、最安値便（1位）が画面上部に明確に見える状態で保存。
- **データモデル & UI 拡張**:
  - 巡回取得件数（`max_results_count`: デフォルト 10 件、1〜50件）のサポート。
  - DB スキーマ拡張（`tasks`, `task_run_logs`, `flight_snapshots`, `system_settings`）。
  - Web UI モック（新規タスク登録画面、タスク詳細・一覧）への反映。

## Non-goals

- Skyscanner スクレイパーの全面改修（本変更は Google Flights スクレイパーおよび共通データモデルを主対象とします）。
- AI 買い時分析モデル（OpenRouter 連携プロンプト等）のロジック変更。
- モバイル専用ビューの追加。

## Capabilities

### New Capabilities

- `flight-search`: Google Flights の2段階検索、料金安い順ソート、便名完全抽出、同一航空券キー追跡、および画面キャプチャ整合性担保。

### Modified Capabilities

<!-- 既存の openspec/specs/ が空のため、新規ケーパビリティとして定義 -->

## Impact

- **Affected Code / Modules**:
  - `FlightTrackerAI.Core`: `domain.clj`（`Task`, `FlightOffer`, `FlightSnapshot` に時間レンジ、便名、`flight_key`、取得件数を追加）
  - `FlightTrackerAI.Infrastructure`: `google_flights_scraper.clj`（2段階検索・堅牢パース）、`database.clj`（マイグレーション）
  - `FlightTrackerAI.Web`: `views/task_views.clj`, `routes.clj`
  - `doc/mock/`: `index.html`, `standalone_new_task.html`
- **Dependencies**: 既存の Playwright、SQLite (Microsoft.Data.Sqlite) のみで完結し、新規外部依存の追加はありません。
