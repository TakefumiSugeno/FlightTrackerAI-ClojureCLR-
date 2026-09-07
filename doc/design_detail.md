# 詳細設計書 (Detailed Design) - FlightTrackerAI (ClojureCLR on .NET 10)

本書は、「FlightTrackerAI」の内部アーキテクチャ、ClojureCLR (.NET 10) ドメイン設計、データベーススキーマ、フロントエンド実装方式（Hiccup風 HTML DSL / HTMX）、スクレイピングエンジン、AI連携、およびUIモックとの整合性を定義します。

---

## 1. 全体アーキテクチャ構成

「**Functional Core, Imperative Shell**（関数型コア・命令型シェル）」パターンを採用し、純粋関数で書かれた堅牢なビジネスロジックと、外部I/O（Web、Playwright、DB、HTTPクライアント）を明確に分離します。

```text
FlightTrackerAI(ClojureCLR)/
├── deps.edn                           # Clojure CLI 依存・クラスパス定義 (cljr 互換)
├── dotnet-tools.json                  # .NET ローカルツール (clojure.cljr, clojure.main)
├── FlightTrackerAI.slnx               # .NET 10 ソリューション
├── src/
│   ├── FlightTrackerAI.Core/          # ドメイン型、バリデーション、純粋関数
│   │   ├── flight_tracker_ai/core/
│   │   │   ├── domain.clj             # 基本型・データ構造・状態定義
│   │   │   ├── dto.clj                # JSON/DBシリアライズ用DTOおよび変換関数
│   │   │   ├── validation.clj         # 入力検証（境界値・日付・IATA）
│   │   │   └── analysis.clj           # 価格変動・最安値計算・エラー理由変換
│   │   └── FlightTrackerAI.Core.csproj
│   │
│   ├── FlightTrackerAI.Infrastructure/ # 外部I/O・アダプター
│   │   ├── flight_tracker_ai/infrastructure/
│   │   │   ├── app_logger.clj         # ログ出力
│   │   │   ├── scraper_common.clj     # await-task, with-scraper-lock, Playwright 共通基盤
│   │   │   ├── database.clj           # SQLite接続 (WAL, FK有効化, busy_timeout)・マイグレーション
│   │   │   ├── settings_repository.clj # システム設定 CRUD
│   │   │   ├── task_repository.clj    # 監視タスク CRUD
│   │   │   ├── flight_repository.clj  # 便スナップショット・巡回ログ保存
│   │   │   ├── notification.clj       # Discord / Slack Webhook通知
│   │   │   ├── ai_client.clj          # OpenRouter API クライアント
│   │   │   ├── google_flights_scraper.clj # Google Flights スクレイピング & パース
│   │   │   ├── skyscanner_scraper.clj # Skyscanner スクレイピング & パース
│   │   │   └── scraping_worker.clj    # 定期巡回・排他制御・手動支援連携
│   │   └── FlightTrackerAI.Infrastructure.csproj
│   │
│   └── FlightTrackerAI.Web/           # Web UI & API ホスト
│       ├── flight_tracker_ai/web/
│       │   ├── views/
│       │   │   ├── html_dsl.clj       # 純粋関数 Hiccup 風 HTML レンダリングエンジン
│       │   │   ├── layout.clj         # 基本HTML骨格・Tailwind・Chart.js
│       │   │   ├── dashboard.clj      # カード表示 & Excel風リスト・AI入力エリア
│       │   │   └── modals.clj         # 旅程タイムライン、設定、新規/編集、クイックメモ
│       │   ├── controllers/
│       │   │   └── api_controller.clj # JSON REST API エンドポイント
│       │   └── server.clj             # Ringライクなリクエスト/レスポンスハンドラー配線
│       ├── Program.cs                 # ASP.NET Core Kestrel エントリポイント & HttpContext 変換
│       └── FlightTrackerAI.Web.csproj
│
└── test/
    ├── FlightTrackerAI.Core.Tests/
    │   ├── domain_tests.clj
    │   ├── dto_tests.clj
    │   ├── validation_tests.clj
    │   ├── analysis_tests.clj
    │   └── DomainTests.cs             # xUnit テストブリッジ (個別テストケース動的列挙)
    ├── FlightTrackerAI.Infrastructure.Tests/
    │   ├── app_logger_tests.clj
    │   ├── database_tests.clj
    │   ├── settings_repository_tests.clj
    │   ├── task_repository_tests.clj
    │   ├── flight_repository_tests.clj
    │   ├── notification_tests.clj
    │   ├── ai_client_tests.clj
    │   ├── scraper_common_tests.clj
    │   ├── google_flights_scraper_tests.clj
    │   ├── skyscanner_scraper_tests.clj
    │   ├── scraping_worker_tests.clj
    │   ├── Fixtures/                  # オフライン HTML フィクスチャ
    │   └── InfrastructureTests.cs     # xUnit テストブリッジ (個別テストケース動的列挙)
    └── FlightTrackerAI.Web.Tests/
        ├── views/
        │   ├── layout_tests.clj
        │   ├── dashboard_tests.clj
        │   └── modals_tests.clj
        ├── controllers/
        │   └── api_controller_tests.clj
        ├── server_tests.clj
        ├── integration/
        │   └── integration_flow_tests.clj
        └── WebTests.cs                # xUnit テストブリッジ (個別テストケース動的列挙)
```

### 1.1 ビルド・依存関係および `.clj` ファイル配置規約

- **依存性の正本**: 各 `.csproj` (NuGet) をビルドおよびパッケージ依存性の正本とします。`deps.edn` は Clojure CLI ツールとの連携用補助設定として整合させます。
- **.clj ファイルの出力配置**: 各 `.csproj` に `<None Update="**\*.clj" CopyToOutputDirectory="PreserveNewest" />` を定義し、ビルド成果物ディレクトリ（`bin/`）へ `.clj` ファイルが確実にコピーされ、ClojureCLR の `CLOJURE_LOAD_PATH` から透過的にロードできるようにします。

---

## 2. フロントエンド実装方式および Web ホスティング設計

### 2.1 採用アーキテクチャ: `Hiccup風 HTML DSL (Clojure)` + `HTMX`

- **サーバーサイド レンダリング (SSR)**:
  - Clojure 標準のデータ構造（ベクタ・マップ・キーワード）を用いた **Hiccup 風 HTML 生成エンジン (`html_dsl.clj`)** を採用。
  - マークアップをすべて純粋関数（`[:div {:class "..."} ...]`）として記述。ドメインデータ構造から安全・高速に HTML 文字列へ変換。
- **動的更新 & 画面対話**:
  - **HTMX**: ページ全体の再読み込みを行わず、タスクの登録・削除・即時実行・フィルタリング時にサーバーから返却される HTML フラグメント（`/fragments/*`）を部分置換。
- **クライアント側インタラクティブ処理**:
  - Excel風テーブルのインクリメンタル絞り込み・ソート、アクティブフィルタチップバーの同期、および Chart.js との連携を Vanilla JS で軽量に実装。
  - 表示切り替え（カード ⇔ リスト）を行っても、絞り込み状態や検索語句が破棄されずシームレスに維持されるクライアント状態管理を担保。

### 2.2 ASP.NET Core Minimal API ⇔ ClojureCLR ハンドラー配線

C# の `Program.cs` は薄い Minimal API ホストとして振る舞い、受信した `HttpContext` を Ring 互換のリクエストマップに変換して ClojureCLR の `flight-tracker-ai.web.server/app` ハンドラーへ委譲します。

```csharp
// Program.cs のルーティングブリッジ概念
app.Map("{*path}", async (HttpContext ctx) => {
    var reqMap = Bridge.ToRingRequest(ctx);
    var resMap = await Task.Run(() => ClojureRuntime.InvokeHandler(reqMap));
    await Bridge.WriteRingResponseAsync(ctx, resMap);
});
```

---

## 3. ドメイン設計 (Domain Models in ClojureCLR)

```clojure
(ns flight-tracker-ai.core.domain
  (:import [System Guid DateTimeOffset DateOnly Char]))

;; IATAコード: 3文字の英字
(defn create-iata-code [s]
  (let [trimmed (when s (.ToUpperInvariant (.Trim (str s))))]
    (if (and (= 3 (count trimmed))
             (every? #(Char/IsLetter %) trimmed))
      {:ok trimmed}
      {:error "IATAコードは3文字の英字である必要があります"})))

;; 目標達成バッジ判定（派生状態）
(defn target-achieved? [task]
  (and (= :active (:status task))
       (:target-price-jpy task)
       (:last-lowest-price-jpy task)
       (<= (:last-lowest-price-jpy task) (:target-price-jpy task))))
```

---

## 4. UIモックとドメイン設計の1:1 整合性対応表 (Cross-Verification Matrix)

| UI画面・コンポーネント     | UIモックの表示・入力項目           | ClojureCLR ドメインキー               | SQLite カラム定義                              | 整合確認 |
| :------------------------- | :--------------------------------- | :------------------------------------ | :--------------------------------------------- | :------: |
| **ヘッダー**               | 巡回ワーカー稼働ステータス         | `worker-status`                       | N/A (メモリ常駐状態)                           |    OK    |
| **有頭手動支援ガイダンス** | PRESS & HOLD 解除案内 (UC-10)      | `manual-challenge-event`              | N/A (ランタイム通知)                           |    OK    |
| **全体設定モーダル**       | 全体デフォルト巡回間隔 (12h)       | `:default-check-interval-hours`       | `system_settings.default_check_interval_hours` |    OK    |
|                            | グローバル Webhook URL             | `:default-webhook-url`                | `system_settings.default_webhook_url`          |    OK    |
|                            | OpenRouter API Key                 | `:openrouter-api-key`                 | `system_settings.openrouter_api_key`           |    OK    |
|                            | プロバイダー有効化                 | `:enable-google-flights / skyscanner` | `enable_google_flights / skyscanner`           |    OK    |
| **タスク登録 / 編集**      | 出発地 (都市名/IATA)               | `:origin`                             | `tasks.origin` (TEXT)                          |    OK    |
|                            | 目的地 (都市名/IATA)               | `:destination`                        | `tasks.destination` (TEXT)                     |    OK    |
|                            | 旅行タイプ (往復/片道)             | `:trip-type`                          | `tasks.trip_type` (TEXT)                       |    OK    |
|                            | 往路・復路出発日                   | `:outbound-date / :inbound-date`      | `tasks.outbound_date / inbound_date`           |    OK    |
|                            | 許容乗継回数                       | `:max-stops`                          | `tasks.max_stops` (TEXT)                       |    OK    |
|                            | 目標アラート価格 (JPY)             | `:target-price-jpy`                   | `tasks.target_price_jpy` (INTEGER)             |    OK    |
|                            | 巡回間隔                           | `:check-interval-hours`               | `tasks.check_interval_hours` (INTEGER)         |    OK    |
|                            | 優先航空会社                       | `:preferred-airlines`                 | `tasks.preferred_airlines` (TEXT/JSON)         |    OK    |
|                            | **ユーザーメモ / 要望**            | `:user-notes`                         | `tasks.user_notes` (TEXT)                      |    OK    |
| **クイックメモ編集**       | メモ直接更新 (UC-09)               | `:user-notes`                         | `tasks.user_notes` (TEXT)                      |    OK    |
| **タスクカード / 一覧**    | 区間・空港通称名                   | `:origin / :destination` + 空港名     | `tasks.origin / destination`                   |    OK    |
|                            | 日程・発着時刻                     | `:trip-type` + 発着時刻               | `tasks.outbound_date / inbound_date`           |    OK    |
|                            | ユーザーメモ・要望表示             | `:user-notes`                         | `tasks.user_notes` (TEXT)                      |    OK    |
|                            | ステータス (達成/監視/停止/エラー) | `:status` + `target-achieved?`        | `tasks.status` (TEXT)                          |    OK    |
|                            | 最安航空会社まとめ (往/復)         | `:last-lowest-airlines`               | `tasks.last_lowest_airlines` (TEXT)            |    OK    |
|                            | 現在最安値 (JPY)                   | `:last-lowest-price-jpy`              | `tasks.last_lowest_price_jpy` (INTEGER)        |    OK    |
|                            | 最安提供ソース (Google/Skyscanner) | `:last-lowest-provider`               | `tasks.last_lowest_provider` (TEXT)            |    OK    |
|                            | データ取得日時 (年+日時 JST)       | `:last-checked-at`                    | `tasks.last_checked_at` (TEXT)                 |    OK    |
| **詳細: 旅程タイムライン** | 往路/復路区分                      | `:leg-index` (0: 往, 1: 復)           | `segments_json -> LegIndex`                    |    OK    |
|                            | 各区間航空会社・便名               | `:marketing-airline / :flight-number` | `segments_json -> MarketingAirline`            |    OK    |
|                            | 発着時刻 (ローカル時刻, (+1)表記)  | `:departure-time / :arrival-time`     | `segments_json -> DepartureTime`               |    OK    |
|                            | AI買い時分析コメントキャッシュ     | `:ai-analysis-summary`                | `task_run_logs.ai_analysis_summary`            |    OK    |
|                            | 各便金額 (総額)                    | `:price-jpy`                          | `flight_snapshots.price_jpy` (INTEGER)         |    OK    |
|                            | 予約リンク                         | `:booking-url`                        | `flight_snapshots.booking_url` (TEXT)          |    OK    |

---

## 5. データベース物理設計 (SQLite Schema & Connection Scoping)

### 5.1 接続スコープ規約 (Connection-Scoped PRAGMA)

`Microsoft.Data.Sqlite` では、接続ごとに PRAGMA を明示適用する必要があります。
`database.clj` の接続ファクトリにおいて、オープン直後に以下を実行します:

- `PRAGMA foreign_keys = ON;` (外部キー制約の有効化)
- `PRAGMA busy_timeout = 5000;` (WAL モード下の並行書き込みロック待機タイムアウト)

### 5.2 物理テーブルスキーマ

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
    last_lowest_provider TEXT,
    ai_analysis_summary TEXT
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
    ai_analysis_summary TEXT,
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

## 6. スクレイピングアーキテクチャ & Interop 設計

### 6.1 非同期 Task 解決 & 排他制御マクロ (`scraper_common.clj`)

```clojure
(ns flight-tracker-ai.infrastructure.scraper-common
  (:import [System.Threading.Tasks Task]
           [System.Threading SemaphoreSlim]))

;; Task<T> または ValueTask<T> の安全な解決 (デッドロック防止)
(defn await-task [^Task task]
  (.GetResult (.GetAwaiter (.ConfigureAwait task false))))

;; 巡回実行の排他制御 (SemaphoreSlim 1, 1)
(defonce scraper-lock (SemaphoreSlim. 1 1))

;; 排他制御マクロ
(defmacro with-scraper-lock [& body]
  `(do
     (await-task (.WaitAsync scraper-lock))
     (try
       ~@body
       (finally
         (.Release scraper-lock)))))
```

### 6.2 Web API 即時巡回時の非ブロッキング応答

Web API（`POST /api/tasks/:id/run`）が呼ばれた際、`scraper-lock` が既にロック中の場合は HTTP 接続をブロックせず、直ちに `409 Conflict`（「現在別の巡回が実行中です。完了後に再試行してください」）を返却し、画面側で即座に待機トーストを表示します。

### 6.3 2段階ステルス巡回 & 有頭手動支援連携 (UC-10)

1. **事前ウォームアップ**: 公式トップページ（`https://www.skyscanner.jp/`）への初期アクセス、Cookie 同意バナーの自動受諾。
2. **Referer 保持ナビゲーション**: 確立されたコンテキストを維持したまま検索結果ページへ遷移。
3. **Bot 検知時の自動試行 & 有頭手動支援連携**:
   - `PRESS & HOLD` チャレンジ検知時、ボタン要素の中心座標を特定し自動長押し（5.5秒）を試行。
   - 解除できない場合、有頭ブラウザモード（`IsHeadless = false`）で最大60秒待機。
   - **WebUI通知連携**: ワーカーが手動支援待機に入った際、WebUIへステータス通知（「認証チャレンジを検知しました。画面上のブラウザで長押しを解除してください（残り◯秒）」）を発行。解除完了時に「認証完了。巡回を再開します」と復帰通知。

---

## 7. テストアーキテクチャ & レポート出力規約

### 7.1 xUnit テストブリッジによる個別テストケース展開 (`TestResults.html`)

`AGENTS.md` の合否一覧規約に準拠するため、C# のテストブリッジクラス（`DomainTests.cs` 等）は、xUnit の `[Theory] [MemberData]` を用いて Clojure の `clojure.test` に定義された各テスト関数（var）を動的に列挙・個別実行します。
これにより、`TestResults.html` 上で各テストケース名、OK(✔)/NG(❌)、所要時間が個別に可視化されます。

### 7.2 コードカバレッジ収集戦略 (`CoverageReport/index.html`)

ClojureCLR ソースコードのカバレッジを Coverlet で収集するため、ビルド時に AOT コンパイル（`compile`）を実施して物理アセンブリ (`.dll`) と PDB シンボルを生成し、インストルメンテーション対象とします。また、全公開関数に対する正常・境界・異常系のテストケース網羅率 100% を品質基準とします。
