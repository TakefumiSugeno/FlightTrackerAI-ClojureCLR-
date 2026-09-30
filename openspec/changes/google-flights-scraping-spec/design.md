# Design: Google Flights スクレイピング仕様の刷新と不整合修正

## Context

詳細は `proposal.md` を参照。
現行の ClojureCLR 版 Playwright スクレイパーにおいて、検索結果カードの価格パース処理が時刻文字列（例: `8:50 – 15:15`）の数値を誤って抽出してしまい、数百万〜数千万円の異常な金額として記録される問題が発生していました。
提供された実画面 HTML サンプル（`doc/work/GoogleFlightサンプル/`）の DOM 解析結果により、安定したセレクタ構成、便名抽出の属性（`itinerary`）、および画面上フィルター操作手順が判明しました。

## Goals / Non-Goals

**Goals:**

- 2段階検索（基本URLアクセス ➔ 画面上フィルター・ソート操作）による正確な検索条件の反映。
- `li.pIav2d` 要素および `aria-label` からの正規表現抽出による価格パースの 100% 精度担保。
- カードの `itinerary` 属性から各レグの便名（例: `5J 5055`）を抽出し、同一航空券キー `flight_key` を導入して特定便の価格変動を確実にトラッキング可能にする。
- スクリーンショット撮影サイズを 1440x900 に固定し、画面上部に最安値便（1位）が見えている状態での撮影・保存。
- 既存タスクとの後方互換性を保った SQLite DB スキーママイグレーション。

**Non-Goals:**

- Skyscanner スクレイパーのパース処理の刷新（Google Flights を優先対応）。
- Google Flights 内部の protobuf base64 エンコード URL 生成の実装（Playwright の画面上フィルター操作で十分安定するため見送り）。

## Decisions

### 1. 2段階 Playwright 操作フロー

- **決定内容**:
  1. 第1段階: `https://www.google.com/travel/flights?q=...` へ遷移し、`DOMContentLoaded` でロード完了を待機。Cookie 同意ダイアログがあれば自動スキップ。
  2. 第2段階:
     - 経由地数フィルター: `max_stops` が `DirectOnly` または `OneStop` の場合、画面上部の「経由地数」ボタン（`button[aria-label*='経由地数']`）をクリックし、該当ラジオ選択肢を `ClickAsync(Force=true)`。
     - ソート順: 「並べ替え」ボタン（`button[aria-label*='並べ替え'], button[aria-label*='フライト順']`）をクリックし、「料金が安い順」を選択。
- **代替案と却下理由**:
  - _URL パラメータのみでの指定_: Google Flights の URL では時間レンジや経由地数の指定が特殊な protobuf エンコードを必要とし、仕様変更に脆いため却下。Playwright での画面上操作の方が直感的かつ高い互換性を維持できる。

### 2. DOM セレクタと価格抽出正規表現

- **決定内容**:
  - カード要素: `li.pIav2d`（一意なフライトカードコンテナ）。
  - 価格抽出: `span[aria-label*='円']` の属性値、または `div.JMc5Xc[aria-label]`（例: `"往復の合計金額 34527 円～"`）から正規表現 `r"(\d+)\s*円"` で数値抽出。
- **理由**:
  - 発着時刻や所要時間（「7時間25分」等）のテキストノードと完全に分離されたメタ属性から抽出することで、時刻誤認バグを根絶できる。

### 3. 便名（Flight Number）抽出と同一便トラッキングキー (`flight_key`)

- **決定内容**:
  - 便名抽出: カード要素の `itinerary` 属性（例: `NRT-MNL-5J-5055-20270227`）から正規表現で航空会社コードと便名（例: `5J 5055`）を抽出。複数区間乗継時は `5J 5065 ➔ 5J 2516` のように連結。
  - 同一航空券キー: `flight_key = [task-id]_[flight-numbers-summary]_[outbound-date]` を生成して `flight_snapshots` に永続化。
- **理由**:
  - 航空券価格は「どの便か」に強く依存するため、単なる最安値だけでなく同一便の推移を追跡できるようにする。

### 4. ウィンドウサイズ統一とキャプチャ整合性

- **決定内容**:
  - Playwright の `ViewportSize` を幅 1440px × 高さ 900px で統一。
  - フィルター・ソート完了後、1位の便が画面上部に表示された状態で `ScreenshotAsync` を実行（`doc/work/screenshots/yyyyMMdd-HHmmss_GoogleFlights_[taskId].png`）。
- **理由**:
  - 画像と取得データ（最安値）の完全一致を保証し、目視確認時にも最安値便が必ず画面内に収まるようにする。

### 5. DB スキーマ拡張

- **決定内容**:
  - `tasks` テーブル: `outbound_time_range`, `inbound_time_range`, `max_results_count`, `last_lowest_flight_number` を追加。
  - `flight_snapshots` テーブル: `flight_number`, `flight_key` を追加。インデックス `idx_snapshots_flight_key` を新設。
  - `system_settings` テーブル: `default_max_results_count`, `headless_mode` を追加。
  - `database.clj` に `ALTER TABLE ... ADD COLUMN ...` を冪等に追加。

### 6. UI モックとの整合

- **決定内容**:
  - 本変更の Single Source of Design として、[mockup.html](file:///D:/programming/repos/MyGitHubRepos/FlightTrackerAI-ClojureCLR-/openspec/changes/google-flights-scraping-spec/mockup.html) および [mockup_new_task.html](file:///D:/programming/repos/MyGitHubRepos/FlightTrackerAI-ClojureCLR-/openspec/changes/google-flights-scraping-spec/mockup_new_task.html)（および `doc/mock/`）に、出発/到着時間レンジ選択、経由地数、取得件数のフォームコントロールを追加・配置済み。

## Risks / Trade-offs

- **[Google Flights の DOM 構造変化リスク]** → `span[aria-label*='円']` だけでなく `div.JMc5Xc` や `.YMlIz.FpEdX` をフォールバックとして保持。
- **[フィルターダイアログのポップアップ干渉]** → Cookie 承諾ダイアログを事前に確実に処理し、`Force = true` クリックおよびアニメーション完了待機を設ける。
- **[既存データの後方互換性]** → 新規カラムにはデフォルト値（`max_results_count = 10`, `outbound_time_range = 'Any'` 等）を付与し、既存レコードで NULL エラーが発生しないよう保護。

## Migration Plan

1. `database.clj` のマイグレーションスクリプトにより、DB 起動時に安全にカラムを追加。
2. ロールバック時は旧スキーマへの影響がないよう、新規カラムは NULL 許容またはデフォルト値付きとする。
