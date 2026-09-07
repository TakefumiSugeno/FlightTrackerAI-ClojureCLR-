# 詳細設計書 (Detailed Design) - FlightTrackerAI

本書は、「FlightTrackerAI」の内部アーキテクチャ、F#ドメイン型定義、データベーススキーマ、フロントエンド実装方式（F# ViewEngine / Fable）、スクレイピングエンジン、AI連携、およびUIモックとの整合性を定義します。

---

## 1. 全体アーキテクチャ構成

「**Functional Core, Imperative Shell**（関数型コア・命令型シェル）」パターンを採用し、純粋関数で書かれた堅牢なビジネスロジックと、外部I/O（Web、Playwright、DB、HTTPクライアント）を明確に分離します。

```text
FlightTrackerAI/
├── src/
│   ├── FlightTrackerAI.Core/              # [Core - net10.0] ドメイン型、バリデーション、純粋関数
│   │   ├── Domain.fs                      # 基本型・エンティティ・判別共用体・セグメント定義
│   │   ├── Dto.fs                         # JSON/DBシリアライズ用DTOおよび変換関数
│   │   ├── Validation.fs                  # 入力検証・Result型パイプライン
│   │   └── Analysis.fs                    # 価格変動・最安値計算・トレンド分析
│   │
│   ├── FlightTrackerAI.Infrastructure/    # [Infrastructure - net10.0] 外部I/O・アダプター
│   │   ├── Database.fs                    # SQLite接続 (WAL, FK有効化)・設定管理・CRUD・パージ
│   │   ├── PlaywrightManager.fs           # ブラウザシングルトン・Stealth・Cookie管理
│   │   ├── Scrapers/
│   │   │   ├── GoogleFlightsScraper.fs    # Google Flights スクレイピング & セグメントパース
│   │   │   └── SkyscannerScraper.fs       # Skyscanner スクレイピング & セグメントパース
│   │   ├── OpenRouterClient.fs            # OpenRouter API クライアント
│   │   └── WebhookNotifier.fs             # Discord / Slack Webhook通知
│   │
│   └── FlightTrackerAI.Web/               # [Entrypoint & Web UI - net10.0]
│       ├── Views/                         # Giraffe ViewEngine (F# サーバーサイドHTML DSL)
│       │   ├── Layout.fs                  # 基本HTML骨格・Tailwind・Chart.js
│       │   ├── Dashboard.fs               # カード表示 & Excel風リストテーブル
│       │   └── Components.fs              # 旅程タイムライン、フィルタバー、モーダル
│       ├── Client/                        # (オプション) Fable (F# to JS) / Alpine.js 対話処理
│       ├── ApiHandlers.fs                 # JSON REST API エンドポイント (`/api/*`)
│       ├── FragmentHandlers.fs            # HTMX フラグメントエンドポイント (`/fragments/*`)
│       ├── BackgroundWorker.fs            # 定期巡回（デフォルト12h）・自動完了・パージ常駐サービス
│       └── Program.fs                     # アプリケーション起動・DI設定
│
└── test/
    ├── FlightTrackerAI.Core.Tests/        # ドメインロジック単体テスト
    ├── FlightTrackerAI.Infrastructure.Tests/ # DB・パース検証テスト
    └── FlightTrackerAI.Infrastructure.Tests/Fixtures/ # オフラインHTMLフィクスチャ
```

---

## 2. フロントエンド実装方式の検討・選定

フロントエンドにおける「F#コード資産の最大活用」と「軽量・高速・高保守性」を実現するため、以下の方式を採用します:

### 2.1 採用アーキテクチャ: `Giraffe ViewEngine` + `HTMX` (+ `Fable` 拡張)

- **サーバーサイド レンダリング (SSR)**:
  - F# 標準の HTML DSL である **Giraffe ViewEngine**（または **Falco**）を採用。
  - HTMLマークアップをすべて F# の型安全な関数（`div [_class "..."] [ ... ]`）として記述。これにより、ドメイン型（`FlightTask`, `FlightOffer`, `FlightSegment`）の変更が即座にコンパイル時エラーとして検知され、テンプレートの型不整合を完全に防止。
- **動的更新 & 画面対話**:
  - **HTMX**: ページ全体の再読み込みを行わず、タスクの登録・削除・即時実行・フィルタリング時にサーバーから返却される F# HTML フラグメント（`/fragments/*`）を部分置換。
- **クライアント側インタラクティブ処理 (Fable 連携)**:
  - Excel風テーブルのインクリメンタル絞り込み・ソート、および Chart.js との連携において、必要に応じて **Fable**（F# から JavaScript へのトランスパイル）を利用。
  - F# で書いたドメインロジック（価格ソート、日付計算、フィルタリング関数）をクライアント・サーバー双方で 100% 共有可能。

---

## 3. ドメイン型定義 (Domain Models)

```fsharp
namespace FlightTrackerAI.Core

open System

/// 空港・都市コード (3レターIATAコード)
type IataCode = private IataCode of string
module IataCode =
    let create (s: string) =
        let trimmed = s.Trim().ToUpperInvariant()
        if trimmed.Length = 3 && trimmed |> Seq.forall Char.IsLetter then
            Ok (IataCode trimmed)
        else
            Error "IATAコードは3文字の英字である必要があります"
    let value (IataCode code) = code

/// 許容乗継回数
type MaxStops =
    | DirectOnly
    | MaxStops of int
    | AnyStops

/// 旅行タイプ
type TripType =
    | OneWay of Outbound: DateOnly
    | RoundTrip of Outbound: DateOnly * Inbound: DateOnly

/// タスク状態
type TaskStatus =
    | Active
    | Paused
    | Running
    | Completed
    | Error of message: string

/// スクレイピング対象プロバイダー
type ScrapingProvider =
    | GoogleFlights
    | Skyscanner

/// 各区間のフライト詳細セグメント（乗継・往復別社対応）
type FlightSegment = {
    LegIndex: int                     // 0: 往路, 1: 復路
    SegmentIndex: int                 // 乗継区間順 (0, 1, 2...)
    DepartureAirport: string          // 例: "HND"
    ArrivalAirport: string            // 例: "SIN"
    MarketingAirline: string          // 販売航空会社 (例: "全日空")
    OperatingAirline: string option   // 運航会社 (コードシェア時, 例: "シンガポール航空")
    FlightNumber: string option       // 例: "NH841"
    DepartureTime: DateTimeOffset     // 出発空港の現地ローカル日時 (タイムゾーンOffset保持)
    ArrivalTime: DateTimeOffset       // 到着空港の現地ローカル日時 (タイムゾーンOffset保持)
    FlightDurationMinutes: int        // フライト時間 (分: タイムゾーン跨ぎを考慮した実飛行時間)
    LayoverMinutesNext: int option    // トランジット時間 (分: 同一空港での次便までの待ち時間)
}

/// 監視タスクエンティティ
type FlightTask = {
    Id: Guid
    Title: string
    Origin: IataCode
    Destination: IataCode
    TripType: TripType
    MaxStops: MaxStops
    PreferredAirlines: string list
    TargetPriceJpy: int option
    CheckIntervalHours: int             // 個別指定 (未指定時はグローバル設定)
    NotificationWebhookUrl: string option
    UserNotes: string option            // ユーザー自由メモ・要望・制約 (Markdown/YAML対応)
    Status: TaskStatus
    ConsecutiveFailures: int
    CreatedAt: DateTimeOffset
    UpdatedAt: DateTimeOffset
    LastCheckedAt: DateTimeOffset option
    LastLowestPriceJpy: int option
    LastLowestAirlines: string option   // 最新最安値の航空会社まとめ表記 (例: "ANA + SQ")
    LastLowestProvider: ScrapingProvider option // 最安値を提示したプロバイダー (GoogleFlights / Skyscanner)
}

/// 収集された便情報スナップショット（1回の巡回で上位複数便を保存）
type FlightOffer = {
    Id: Guid
    TaskId: Guid
    RunLogId: Guid
    Provider: ScrapingProvider
    AirlinesSummary: string            // 例: "エールフランス", "JAL / AF", "ANA + SQ"
    DepartureTime: DateTimeOffset
    ArrivalTime: DateTimeOffset
    TotalDurationMinutes: int          // 総所要時間 (分)
    StopsCount: int                    // 乗継回数
    Segments: FlightSegment list       // 往路・復路・乗継の全詳細セグメント
    PriceJpy: int                      // 日本円総額
    BookingUrl: string
    CapturedAt: DateTimeOffset         // データ取得日時
}

/// システム全体設定エンティティ
type SystemSettings = {
    DefaultCheckIntervalHours: int     // デフォルト巡回間隔（初期値: 12時間）
    DefaultWebhookUrl: string option   // グローバル Discord / Slack Webhook
    OpenRouterApiKey: string option    // AI支援用 API キー
    EnableGoogleFlights: bool          // Google Flights 有効フラグ
    EnableSkyscanner: bool             // Skyscanner 有効フラグ
}
```

---

## 4. UIモックとドメイン設計の1:1 整合性対応表 (Cross-Verification Matrix)

UIモック（`doc/mock/index.html`）に存在する全画面要素・フォーム項目・表示項目が、ドメイン型およびSQLiteスキーマと 100% 整合していることを検証・定義します。

| UI画面・コンポーネント     | UIモックの表示・入力項目               | F# ドメイン型・フィールド                                   | SQLite カラム定義                              | 整合確認 |
| :------------------------- | :------------------------------------- | :---------------------------------------------------------- | :--------------------------------------------- | :------: |
| **ヘッダー**               | 巡回ワーカー稼働ステータス             | `BackgroundWorker` 状態                                     | N/A (メモリ常駐状態)                           |    OK    |
| **全体設定モーダル**       | 全体デフォルト巡回間隔 (12h)           | `SystemSettings.DefaultCheckIntervalHours`                  | `system_settings.default_check_interval_hours` |    OK    |
|                            | グローバル Webhook URL                 | `SystemSettings.DefaultWebhookUrl`                          | `system_settings.default_webhook_url`          |    OK    |
|                            | OpenRouter API Key                     | `SystemSettings.OpenRouterApiKey`                           | `system_settings.openrouter_api_key`           |    OK    |
|                            | プロバイダー有効化 (Google/Skyscanner) | `SystemSettings.EnableGoogleFlights / Skyscanner`           | `enable_google_flights / skyscanner`           |    OK    |
| **タスク登録 / 編集**      | 出発地 (都市名/IATA)                   | `FlightTask.Origin` (`IataCode`)                            | `tasks.origin` (TEXT)                          |    OK    |
|                            | 目的地 (都市名/IATA)                   | `FlightTask.Destination` (`IataCode`)                       | `tasks.destination` (TEXT)                     |    OK    |
|                            | 旅行タイプ (往復/片道)                 | `FlightTask.TripType` (`OneWay / RoundTrip`)                | `tasks.trip_type` (TEXT)                       |    OK    |
|                            | 往路・復路出発日                       | `TripType.Outbound / Inbound` (`DateOnly`)                  | `tasks.outbound_date / inbound_date`           |    OK    |
|                            | 許容乗継回数                           | `FlightTask.MaxStops` (`DirectOnly / 1 / Any`)              | `tasks.max_stops` (TEXT)                       |    OK    |
|                            | 目標アラート価格 (JPY)                 | `FlightTask.TargetPriceJpy` (`int option`)                  | `tasks.target_price_jpy` (INTEGER)             |    OK    |
|                            | 巡回間隔                               | `FlightTask.CheckIntervalHours` (`int`)                     | `tasks.check_interval_hours` (INTEGER)         |    OK    |
|                            | 優先航空会社                           | `FlightTask.PreferredAirlines` (`string list`)              | `tasks.preferred_airlines` (TEXT/JSON)         |    OK    |
|                            | **ユーザーメモ / 要望**                | `FlightTask.UserNotes` (`string option`)                    | `tasks.user_notes` (TEXT)                      |    OK    |
| **タスクカード / 一覧**    | 区間・空港通称名                       | `Origin` / `Destination` + 空港名                           | `tasks.origin / destination`                   |    OK    |
|                            | 日程・発着時刻                         | `TripType` + 発着時刻                                       | `tasks.outbound_date / inbound_date`           |    OK    |
|                            | ユーザーメモ・要望表示                 | `FlightTask.UserNotes` (`string option`)                    | `tasks.user_notes` (TEXT)                      |    OK    |
|                            | ステータス (達成/監視/停止/エラー)     | `FlightTask.Status` (`TaskStatus`)                          | `tasks.status` (TEXT)                          |    OK    |
|                            | 最安航空会社まとめ (往/復)             | `FlightTask.LastLowestAirlines` (`string option`)           | `tasks.last_lowest_airlines` (TEXT)            |    OK    |
|                            | 現在最安値 (JPY)                       | `FlightTask.LastLowestPriceJpy` (`int option`)              | `tasks.last_lowest_price_jpy` (INTEGER)        |    OK    |
|                            | **最安提供ソース (Google/Skyscanner)** | `FlightTask.LastLowestProvider` (`ScrapingProvider option`) | `tasks.last_lowest_provider` (TEXT)            |    OK    |
|                            | **データ取得日時 (年+日時 JST)**       | `FlightTask.LastCheckedAt` (`DateTimeOffset option`)        | `tasks.last_checked_at` (TEXT)                 |    OK    |
| **詳細: 旅程タイムライン** | 往路/復路区分                          | `FlightSegment.LegIndex` (`0: 往, 1: 復`)                   | `segments_json -> LegIndex`                    |    OK    |
|                            | 出発/到着空港                          | `FlightSegment.DepartureAirport / ArrivalAirport`           | `segments_json -> DepartureAirport`            |    OK    |
|                            | 各区間航空会社・便名                   | `FlightSegment.MarketingAirline / FlightNumber`             | `segments_json -> MarketingAirline`            |    OK    |
|                            | 発着時刻                               | `FlightSegment.DepartureTime / ArrivalTime`                 | `segments_json -> DepartureTime`               |    OK    |
|                            | **フライト時間**                       | `FlightSegment.FlightDurationMinutes` (`int`)               | `segments_json -> FlightDurationMinutes`       |    OK    |
|                            | **トランジット時間**                   | `FlightSegment.LayoverMinutesNext` (`int option`)           | `segments_json -> LayoverMinutesNext`          |    OK    |
|                            | **総所要時間**                         | `FlightOffer.TotalDurationMinutes` (`int`)                  | `flight_snapshots.total_duration_minutes`      |    OK    |
|                            | **各便金額 (総額)**                    | `FlightOffer.PriceJpy` (`int`)                              | `flight_snapshots.price_jpy` (INTEGER)         |    OK    |
|                            | データ取得日時                         | `FlightOffer.CapturedAt` (`DateTimeOffset`)                 | `flight_snapshots.captured_at` (TEXT)          |    OK    |
|                            | 予約リンク                             | `FlightOffer.BookingUrl` (`string`)                         | `flight_snapshots.booking_url` (TEXT)          |    OK    |

---

## 5. データベース物理設計 (SQLite Schema)

```sql
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;
PRAGMA synchronous = NORMAL;

-- システム全体設定テーブル (単一レコード管理)
CREATE TABLE IF NOT EXISTS system_settings (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    default_check_interval_hours INTEGER NOT NULL DEFAULT 12,
    default_webhook_url TEXT,
    openrouter_api_key TEXT,
    enable_google_flights INTEGER NOT NULL DEFAULT 1,
    enable_skyscanner INTEGER NOT NULL DEFAULT 1,
    updated_at TEXT NOT NULL
);

-- 監視タスクテーブル
CREATE TABLE IF NOT EXISTS tasks (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    origin TEXT NOT NULL,
    destination TEXT NOT NULL,
    trip_type TEXT NOT NULL,
    outbound_date TEXT NOT NULL,
    inbound_date TEXT,
    preferred_airlines TEXT,
    max_stops TEXT NOT NULL,
    target_price_jpy INTEGER,
    check_interval_hours INTEGER NOT NULL DEFAULT 12,
    webhook_url TEXT,
    user_notes TEXT,
    status TEXT NOT NULL DEFAULT 'Active',
    error_message TEXT,
    consecutive_failures INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    last_checked_at TEXT,
    last_lowest_price_jpy INTEGER,
    last_lowest_airlines TEXT,
    last_lowest_provider TEXT
);

-- 巡回ログテーブル
CREATE TABLE IF NOT EXISTS task_run_logs (
    id TEXT PRIMARY KEY,
    task_id TEXT NOT NULL,
    started_at TEXT NOT NULL,
    completed_at TEXT,
    is_success INTEGER NOT NULL,
    error_message TEXT,
    offers_found_count INTEGER NOT NULL DEFAULT 0,
    lowest_price_jpy INTEGER,
    lowest_airlines TEXT,
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
);

-- 便価格スナップショットテーブル
CREATE TABLE IF NOT EXISTS flight_snapshots (
    id TEXT PRIMARY KEY,
    task_id TEXT NOT NULL,
    run_log_id TEXT NOT NULL,
    provider TEXT NOT NULL,
    airlines_summary TEXT NOT NULL,
    departure_time TEXT NOT NULL,
    arrival_time TEXT NOT NULL,
    total_duration_minutes INTEGER NOT NULL,
    stops_count INTEGER NOT NULL,
    segments_json TEXT NOT NULL,       -- 全区間詳細セグメントのJSON配列
    price_jpy INTEGER NOT NULL,
    booking_url TEXT NOT NULL,
    captured_at TEXT NOT NULL,
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    FOREIGN KEY (run_log_id) REFERENCES task_run_logs(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status);
CREATE INDEX IF NOT EXISTS idx_snapshots_task_captured ON flight_snapshots(task_id, captured_at);
CREATE INDEX IF NOT EXISTS idx_snapshots_run_log ON flight_snapshots(run_log_id);
CREATE INDEX IF NOT EXISTS idx_run_logs_task ON task_run_logs(task_id);
```

---

## 6. スクレイピングアーキテクチャ & アンチBot対策設計

### 6.1 ブラウザプロファイル永続化と排他制御

- **ユーザーデータディレクトリ**: `doc/work/browser_profile/` を Chromium の `userDataDir` として指定。
- **永続化対象**: セッションCookie（PerimeterX `_px3`, `_pxhd` 等）、localStorage、サイト認証状態。
- **ファイルロック競合防止**: `SemaphoreSlim(1, 1)` を用いた巡回実行の排他制御を行い、バックグラウンド巡回と手動即時実行ボタンの衝突（`SingletonLock` 例外）を完全防止。

### 6.2 2段階ステルス巡回シーケンス

1. **事前ウォームアップ**: 公式トップページ（`https://www.skyscanner.jp/`）への初期アクセス、Cookie 同意バナーの自動受諾、自然なマウス移動シミュレーション。
2. **Referer 保持ナビゲーション**: 確立されたコンテキストを維持したまま、`Referer: "https://www.skyscanner.jp/"` を付与して検索結果ページへ遷移。
3. **Bot 検知時の自動試行 & 手動支援フォールバック**:
   - `PRESS & HOLD` チャレンジ画面検知時、ボタン要素の中心座標を特定し、`Mouse.DownAsync` ➔ 5秒ホールド ➔ `Mouse.UpAsync` の自動解除を試行。
   - 有頭ブラウザモード（`IsHeadless = false`）時は最大60秒待機し、ユーザーが1クリック長押し解除すれば即座に認証トークンがプロファイルに保存され、次回の定期ヘッドレス巡回に引き継がれる。
