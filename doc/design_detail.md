# 詳細設計書 (Detailed Design) - FlightTrackerAI (100% ClojureCLR on .NET 10)

本書は、「FlightTrackerAI」の内部アーキテクチャ、100% ClojureCLR (.NET 10) ドメイン設計、データベーススキーマ、フロントエンド実装方式（Hiccup風 HTML DSL / HTMX）、スクレイピングエンジン、AI連携、およびUIモックとの整合性を定義します。
※万が一、ClojureCLR (.clj) での動作が不可能な機能が生じた場合は、代替言語として F# (.fs) を採用します（C# は不採用）。

---

## 1. 全体アーキテクチャ構成

「**Functional Core, Imperative Shell**（関数型コア・命令型シェル）」パターンを採用し、純粋関数で書かれた堅牢なビジネスロジックと、外部I/O（Web、Playwright、DB、HTTPクライアント）を明確に分離します。すべてのソースおよびテストは **100% ClojureCLR (.clj)** で構成します。

```text
FlightTrackerAI(ClojureCLR)/
├── deps.edn                           # Clojure CLI 依存・クラスパス定義 (cljr 互換)
├── dotnet-tools.json                  # .NET ローカルツール (clojure.cljr, clojure.main)
├── FlightTrackerAI.slnx               # .NET 10 ソリューション (NuGet パッケージ解決・AOT定義)
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
│   └── FlightTrackerAI.Web/           # Web UI & HTTP サーバーホスト (純粋 ClojureCLR)
│       ├── Program.fs                 # .NET 10 実行ブートストラップホスト
│       ├── flight_tracker_ai/web/
│       │   ├── views/
│       │   │   ├── html_dsl.clj       # 純粋関数 Hiccup 風 HTML レンダリングエンジン
│       │   │   ├── layout.clj         # 基本HTML骨格・Tailwind・Chart.js
│       │   │   ├── dashboard.clj      # カード表示 & Excel風リスト・AI入力エリア
│       │   │   └── modals.clj         # 旅程タイムライン、設定、新規/編集、クイックメモ
│       │   ├── controllers/
│       │   │   └── api_controller.clj # JSON REST API エンドポイント
│       │   └── server.clj             # (-main) エントリーポイント & HTTP リクエストディスパッチャ
│       └── FlightTrackerAI.Web.fsproj
│
└── test/
    ├── test_runner.clj                # Clojure 製テストランナー & HTML レポート自動生成
    ├── FlightTrackerAI.Core.Tests/
    │   ├── domain_tests.clj
    │   ├── dto_tests.clj
    │   ├── validation_tests.clj
    │   └── analysis_tests.clj
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
    │   └── Fixtures/                  # オフライン HTML フィクスチャ
    └── FlightTrackerAI.Web.Tests/
        ├── views/
        │   ├── html_dsl_tests.clj
        │   ├── layout_tests.clj
        │   ├── dashboard_tests.clj
        │   └── modals_tests.clj
        ├── controllers/
        │   └── api_controller_tests.clj
        ├── server_tests.clj
        └── integration/
            └── integration_flow_tests.clj
```

---

## 2. フロントエンドおよび HTTP ホスティング設計 (100% ClojureCLR)

### 2.1 採用アーキテクチャ: `Hiccup風 HTML DSL (Clojure)` + `HTMX` + `FontAwesome` + `Tailwind CSS`

- **サーバーサイド レンダリング (SSR)**:
  - Clojure 標準のデータ構造（ベクタ・マップ・キーワード）を用いた **Hiccup 風 HTML 生成エンジン (`html_dsl.clj`)** を採用。
  - マークアップをすべて純粋関数（`[:div {:class "..."} ...]`）として記述。ドメインデータ構造から安全・高速に HTML 文字列へ変換。
  - `(h/raw "...")` を安全に展開し、`<script>` や生HTMLを一切破損せずにブラウザへ供給。
- **動的更新 & 画面対話**:
  - **HTMX 1.9.12**: ページ全体の再読み込みを行わず、タスクの登録・削除・即時実行・一時停止・フィルタリング時にサーバーから返却される HTML フラグメント（ダッシュボード全体やカード単体）を部分置換。
- **UIライブラリ & スタイル標準 (元リポジトリ `FlightTrackerAI` 完全準拠)**:
  - **FontAwesome 6.5.1 (`all.min.css`)**:
    - 元リポジトリと同一のアイコンスタック。CSS アイコンフォント（`<i class="fa-solid fa-plane"></i>` 等）であるため、HTMX による動的 DOM 部分置換時にも JavaScript の再展開処理が一切不要であり、即座かつ安定してレンダリングされます。
  - **Tailwind CSS (CDN)**:
    - レスポンシブ、ユーティリティファーストのスタイリング。空の旅をイメージしたスカイブルー・スレート基調のデザイン。
  - **Chart.js (CDN)**:
    - 旅程詳細モーダル内での価格推移チャート（巡回日時と最安値の折れ線グラフ、目標価格ライン）の動的描画。
  - **トースト通知システム (Toast)**:
    - 画面右上に操作成功・情報・警告・エラーメッセージを自動スライドイン表示。
- **クライアント側インタラクティブ処理**:
  - AI 自然言語解析時の別タブ展開 / プログレス表示と `/tasks/new` へのパラメータ自動入力遷移。
  - 表示切り替え（カード表示 ⇔ テーブル表示）のシームレスな切替。

### 2.2 純粋 ClojureCLR HTTP サーバー (`server.clj`)

ClojureCLR から .NET の標準 HTTP サーバー（`System.Net.HttpListener`）を直接起動・管理します（C# コード不要）。

```clojure
(ns flight-tracker-ai.web.server
  (:require [flight-tracker-ai.web.controllers.api-controller :as api]
            [flight-tracker-ai.web.views.dashboard :as dash]
            [flight-tracker-ai.web.views.layout :as layout]
            [flight-tracker-ai.web.views.modals :as modals]
            [flight-tracker-ai.infrastructure.database :as db]
            [flight-tracker-ai.infrastructure.task-repository :as task-repo]
            [flight-tracker-ai.infrastructure.scraping-worker :as worker]
            [flight-tracker-ai.infrastructure.app-logger :as logger])
  (:import [System Uri]
           [System.Net HttpListener HttpListenerContext]
           [System.IO File StreamReader]
           [System.Text Encoding]
           [System.Threading Thread ThreadPool WaitCallback]))

(defn ascii-safe-header? [s]
  (and (string? s) (boolean (re-matches #"^[\x20-\x7E]+$" s))))

(defn write-response
  ([^HttpListenerResponse resp status content-type body-str]
   (write-response resp status content-type body-str nil))
  ([^HttpListenerResponse resp status content-type body-str headers]
   (try
     (set! (.StatusCode resp) status)
     (set! (.ContentType resp) content-type)
     (when (map? headers)
       (doseq [[k v] headers]
         (let [k-str (name k)
               v-str (str v)]
           (when (and (ascii-safe-header? k-str) (ascii-safe-header? v-str))
             (.AddHeader resp k-str v-str)))))
     (let [bytes (.GetBytes Encoding/UTF8 (or body-str ""))
           output (.OutputStream resp)]
       (set! (.ContentLength64 resp) (long (count bytes)))
       (.Write output bytes 0 (count bytes))
       (.Close output))
     (catch Exception _ nil))))

(defn handle-request [^String connection-string ^HttpListenerContext ctx]
  (let [req (.Request ctx)
        resp (.Response ctx)
        method (.HttpMethod req)
        raw-url (.RawUrl req)
        body-str (read-body (.InputStream req))]
    (try
      (cond
        (and (= method "GET") (or (= raw-url "/") (= raw-url "/index.html")))
        (let [tasks (task-repo/get-all-tasks connection-string)
              content (dash/render-dashboard-content tasks)
              full-html (layout/base-layout "ダッシュボード" content)]
          (write-response resp 200 "text/html; charset=utf-8" full-html))

        (and (= method "GET") (.StartsWith raw-url "/tasks/new"))
        (let [uri (Uri. (str "http://localhost" raw-url))
              query (api/parse-query-string (.Query uri))
              content (modals/render-standalone-new-task-page query)
              full-html (layout/base-layout "新規タスク登録" content)]
          (write-response resp 200 "text/html; charset=utf-8" full-html))

        (and (= method "GET") (= raw-url "/favicon.svg"))
        (let [fav (first (filter #(File/Exists %) ["wwwroot/favicon.svg" "src/FlightTrackerAI.Web/wwwroot/favicon.svg"]))]
          (if fav
            (write-response resp 200 "image/svg+xml" (File/ReadAllText fav))
            (write-response resp 404 "text/plain" "Not Found")))

        (.StartsWith raw-url "/api/")
        (let [res (api/handle-api-request connection-string method raw-url body-str)]
          (write-response resp (:status res) (:content-type res) (:body res) (:headers res)))

        :else
        (write-response resp 404 "text/plain; charset=utf-8" "Not Found"))
      (catch Exception ex
        (logger/error-ex "Server" "リクエストハンドリング例外" ex)
        (write-response resp 500 "text/plain; charset=utf-8" (str "Internal Server Error: " (.Message ex)))))))

(defn start-server [^String connection-string ^String port]
  (let [listener (HttpListener.)
        prefix (str "http://localhost:" port "/")]
    (.Add (.Prefixes listener) prefix)
    (.Start listener)
    (logger/info "Server" (str "FlightTrackerAI サーバーが起動しました: " prefix))
    (let [t (Thread.
              (gen-delegate System.Threading.ThreadStart []
                (while (.IsListening listener)
                  (try
                    (let [ctx (.GetContext listener)]
                      (ThreadPool/QueueUserWorkItem
                        (gen-delegate WaitCallback [state]
                          (handle-request connection-string state))
                        ctx))
                    (catch Exception _ nil)))))]
      (set! (.IsBackground t) true)
      (.Start t)
      listener)))

(defn -main [& args]
  (let [port (or (first args) "5121")
        conn-str "Data Source=flight_tracker.db"]
    (db/initialize-database conn-str)
    (worker/start-worker! conn-str)
    (let [listener (start-server conn-str port)]
      (println (str "Server running on http://localhost:" port "/ (Press Enter to stop)"))
      (read-line)
      (.Stop listener)
      (worker/stop-worker!))))
```

### 2.3 モーダルナビゲーション・ライフサイクル設計 (Modal Navigation & Lifecycle)

クライアント側のイベントモデルと HTMX の通信を整合させるため、以下の状態管理と排他制御を導入します:

1. **HTMX 宣言的ロード**:
   - 新規登録、編集、旅程詳細、メモ、全体設定、ログの各モーダルは、`:hx-get` + `:hx-target="#modal-container"` によってサーバーから HTML フラグメントを取得し、`#modal-container` へ注入。
   - 不要なインライン `fetch` を廃止し、HTMX による標準的・宣言的な部分描画に統一。

2. **モーダル開閉ステート管理 (`layout.clj`)**:
   - モーダルが開かれた際は、背景スクロールを抑止（`overflow-hidden`）。
   - クローズ契機:
     - モーダル右上の「✕」ボタンまたはフッターの「キャンセル / 閉じる」ボタン。
     - モーダル背面の半透明オーバーレイクリック。
     - ESC キー押下（日本語 IME 変換中 `isComposing` を検知して誤閉鎖を防止）。
     - フォーム保存成功時にサーバーから返却されるレスポンスヘッダー `HX-Trigger: closeModal` の受信。
   - `closeModal` イベント受信時に `#modal-container` 内の DOM を空にし、背面スクロールを復帰。

### 2.4 HTTP ヘッダー伝搬・レスポンス設計 (HTTP Header & Error Handling)

1. **カスタムレスポンスヘッダーの ASCII 安全な出力 (`server.clj`)**:
   - .NET `HttpListenerResponse.AddHeader` の HTTP/1.1 ASCII 準拠制約を満たすため、`server.clj` の `write-response` でヘッダーマップ（例: `{"HX-Trigger" "closeModal"}`）を安全に設定。
   - 正規表現 `^[\x20-\x7E]+$` により ASCII 安全性を検証した上で `.AddHeader` を実行。

2. **正常系と異常系の HTMX swap 分離設計 (`api_controller.clj`)**:
   - モーダル内フォームの `:hx-target` はモーダル自身（または `#modal-container`）とし、親画面の破壊を防止。
   - **正常終了時 (200 OK)**:
     - レスポンスヘッダー: `{"HX-Trigger" "closeModal"}`
     - レスポンスボディ: `<div id="dashboard-container" hx-swap-oob="outerHTML">...最新ダッシュボードHTML...</div>` または最新ダッシュボードフラグメント。
     - クライアント側で確実にモーダルをクローズし、タスク一覧が最新状態に更新される。
   - **バリデーションエラー・送信失敗時 (400 Bad Request / 200 エラー表示)**:
     - `HX-Trigger: closeModal` は出力せず、インライン赤字エラーメッセージを含めたモーダル HTML を返却。モーダルおよびユーザー入力値を完全に維持。

### 2.5 元リポジトリ完全準拠 UIコンポーネント詳細設計

1. **AI 自然言語入力バー (`Dashboard.fs` 準拠)**:
   - 自然言語による旅程検索指示（例:「来月の連休に東京から福岡へ行きたい、予算2万円」）を入力。
   - 入力内容を `POST /api/ai/parse` へ送信。
   - クライアント側スクリプトにより、解析中は別タブまたはローディングインジケータでプログレスを表示し、解析結果（出発地、目的地、日付、予算等）をクエリパラメータとして `/tasks/new?origin=HND&destination=FUK...` へ自動引き渡し・画面遷移。

2. **ダッシュボードカード 5大アクション (`Dashboard.fs` 準拠)**:
   - 各カードの上部ヘッダーまたはアクションエリアに配置:
     1. **一時停止 / 再開トグル**: `POST /api/tasks/{id}/toggle-status` (アイコン: `fa-pause` / `fa-play`, `hx-target="#dashboard-container"`)
     2. **即時巡回 (ヘッドレス)**: `POST /api/tasks/{id}/run?headless=true` (アイコン: `fa-sync`, バックグラウンドで Playwright を即時起動)
     3. **ブラウザ手動支援巡回**: `POST /api/tasks/{id}/run?headless=false` (アイコン: `fa-robot` or `fa-globe`, Bot判定やCAPTCHA解除のためブラウザ画面を表示して巡回)
     4. **編集モーダル**: `GET /api/tasks/{id}/modal` (アイコン: `fa-edit`, `hx-target="#modal-container"`)
     5. **削除**: `DELETE /api/tasks/{id}` (アイコン: `fa-trash`, 確認ダイアログ付き, `hx-target="#dashboard-container"`)

3. **テーブル表示モード (`renderTaskTable`)**:
   - カード表示と一覧テーブル表示を切り替え可能。
   - テーブルカラム: ステータス、往復/片道、出発地-目的地、日程、最安価格、目標価格、更新日時、操作アクション。

4. **旅程詳細モーダル (`Modals.fs` 準拠)**:
   - **タスクサマリー**: ルート、旅行種別、日程、最安値、目標価格、最終更新日時。
   - **実DBオファーデータ一覧テーブル**:
     - Google Flights および Skyscanner の巡回結果（DB `flight_snapshots` テーブルの実データ）をバインド。
     - 各行に「航空会社名」「便名」「出発/到着時刻」「所要時間」「経由数」「総額価格」「公式予約リンクボタン」を完全表示。
   - **価格推移チャート (Chart.js)**:
     - 過去のスナップショットデータから日時と価格の折れ線グラフを動的描画。目標価格がある場合は水平破線で目標ラインを重ねて表示。

5. **クイックメモモーダル (`#memoModal`)**:
   - タスクごとに自由なメモを保存・閲覧可能（`POST /api/tasks/{id}/memo`）。

6. **全体設定モーダル (`#settingsModal`)**:
   - 巡回ワーカー間隔（時間）、グローバル Discord Webhook URL、OpenRouter API Key、モデル名の更新。

7. **スタンドアロン新規登録画面 (`/tasks/new`) & 手動登録モーダルの共通設計**:
   - **共通フォーム部品化 (`render-task-form-fields`)**:
     - 手動登録モーダル (`render-task-modal`) とスタンドアロン画面 (`render-standalone-new-task-page`) の双方で、全12項目の入力フィールドを共通コンポーネント関数 `render-task-form-fields` から生成。
     - 入力12項目: 旅行タイプ (`tripType`)、出発地 (`origin`)、目的地 (`destination`)、往路出発日 (`outboundDate`、本日以降 `min` ガード)、復路出発日 (`inboundDate`、本日以降 `min` ガード)、許容乗継回数 (`maxStops`)、巡回間隔 (`checkIntervalHours`、システム設定連動)、目標アラート価格 (`targetPriceJpy`)、タスク名 (`title`、未入力時は自動補完)、構造化メモ (`userNotes`)、Webhook通知 (`useDefaultWebhook`)、有頭ブラウザ巡回 (`showBrowser`)。
   - **Webhook通知の無効化制御 (`notification.clj` 連携)**:
     - ユーザーが `useDefaultWebhook` のチェックを外した場合、タスクの `:notification-webhook-url` に `"DISABLED"` を格納。
     - `notification.clj` の巡回通知ワーカーは、`(= (:notification-webhook-url task) "DISABLED")` の場合、グローバル Webhook へのフォールバックを停止し、通知を完全にスキップ。
   - **コントローラ入力処理の一元化 (`parse-task-form`)**:
     - `api_controller.clj` において、`POST /api/tasks` と `POST /api/tasks/standalone` の入力抽出・正規化・バリデーション・TaskItem 生成ロジックを `parse-task-form` 関数に一元化。
     - スタンドアロン登録でエラーが発生した場合は、白画面にせず入力値を引き渡して `render-standalone-new-task-page` をエラーメッセージ付きで再レンダリング。

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
|                            | タスク名                           | `:title`                              | `tasks.title` (TEXT)                           |    OK    |
|                            | 優先航空会社                       | `:preferred-airlines`                 | `tasks.preferred_airlines` (TEXT/JSON)         |    OK    |
|                            | **ユーザーメモ / 要望**            | `:user-notes`                         | `tasks.user_notes` (TEXT)                      |    OK    |
|                            | Webhook通知設定                    | `:notification-webhook-url`           | `tasks.webhook_url` (TEXT ※OFF時 'DISABLED')   |    OK    |
|                            | 有頭ブラウザ巡回 (手動支援)        | `:is-headless`                        | `tasks.is_headless` (INTEGER 0:有頭, 1:無頭)   |    OK    |
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

`Microsoft.Data.Sqlite` では、オープン直後に以下を実行します:

- `PRAGMA foreign_keys = ON;` (外部キー制約の有効化)
- `PRAGMA busy_timeout = 5000;` (WAL モード下の並行書き込みロック待機タイムアウト)

### 5.2 物理テーブルスキーマ

```sql
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;
PRAGMA synchronous = NORMAL;

CREATE TABLE IF NOT EXISTS system_settings (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    default_check_interval_hours INTEGER NOT NULL DEFAULT 12,
    default_webhook_url TEXT,
    openrouter_api_key TEXT,
    enable_google_flights INTEGER NOT NULL DEFAULT 1,
    enable_skyscanner INTEGER NOT NULL DEFAULT 1,
    updated_at TEXT NOT NULL
);

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
    is_headless INTEGER NOT NULL DEFAULT 1,
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
    segments_json TEXT NOT NULL,
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

Playwright による実ブラウザ自動操作は、元リポジトリ（F#版）の堅牢な仕様を 100% ClojureCLR に移植し、Clojure の特性（`loop/recur`、atom 状態管理、純粋関数分離）を生かして設計します。

### 6.1 スクレイピング共通基盤 (`scraper_common.clj`)

1. **非同期タスク同期 (`await-task`)**:
   ```clojure
   (defn await-task [^Task task]
     (when task
       (.GetResult (.GetAwaiter (.ConfigureAwait task false)))))
   ```
2. **多重起動・排他ロック (`scraper-lock`)**:
   - `SemaphoreSlim(1, 1)` によるプロセス内スレッド排他制御。
   - `with-scraper-lock` マクロによる確実な解放。
3. **Chromium 自動プロビジョニング (`ensure-playwright-browsers-installed!`)**:
   - `(defonce ^:private browser-installed-state (atom :uninstalled))`
   - 初回呼び出し時に `Microsoft.Playwright.Program/Main` を引数 `(into-array String ["install" "chromium"])` で実行。
   - 状態を `:installing` ➔ `:installed` (または `:failed`) へ遷移させ、失敗時はクールダウン時間を設けて連続失敗・無駄なネットワーク試行を防止。
4. **ブラウザ永続コンテキスト生成 (`create-context-async`)**:
   - 保存先: `doc/work/browser_profile/`
   - **SingletonLock クリーンアップ**:
     - 起動直前に、古い `SingletonLock`、`SingletonCookie`、`SingletonSocket` の存在を検証。
     - ロック中の Chromium プロセスが存在しないことを確認した上で、安全にファイルを削除し、クラッシュ後の再起動不能（デッドロック）を完全に防止。
   - オプション構築:
     - `BrowserTypeLaunchPersistentContextOptions`
     - ヘッドレス設定: `options.Headless = Nullable headless`
     - 引数: `--disable-blink-features=AutomationControlled`, `--disable-infobars`, `--no-sandbox`, `--window-size=1440,900`
     - 有頭時追加: `options.SlowMo = Nullable 150.0`, 引数 `--start-maximized`, `options.ViewportSize = null`
     - ロケール: `ja-JP`, タイムゾーン: `Asia/Tokyo`, `BypassCSP = true`, `IgnoreHTTPSErrors = true`
     - UserAgent & Sec-Ch-Ua ヘッダー
   - **Stealth スクリプト注入 (`AddInitScriptAsync`)**:
     ```javascript
     Object.defineProperty(navigator, 'webdriver', {get: () => undefined});
     window.chrome = { runtime: {} };
     Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });
     Object.defineProperty(navigator, 'languages', { get: () => ['ja-JP', 'ja', 'en-US', 'en'] });
     const originalQuery = window.navigator.permissions.query;
     window.navigator.permissions.query = (parameters) => (
         parameters.name === 'notifications' ?
             Promise.resolve({ state: Notification.permission }) :
             originalQuery(parameters)
     );
     ```
5. **安全なクローズ (`close-context-async`)**:
   - `context.Pages` の全ページクローズ ➔ `context.CloseAsync` ➔ `context.Browser.CloseAsync` を個別 `try-catch` で安全に実行。
   - `AppDomain.CurrentDomain.ProcessExit` イベントハンドラへの登録によるプロセス終了時の確実な解放。

### 6.2 Google Flights スクレーパー (`google_flights_scraper.clj`)

1. **検索URL生成 (`build-search-url`)**:
   - 片道: `https://www.google.com/travel/flights?q=Flights%20to%20[DEST]%20from%20[ORIG]%20on%20[DATE]&hl=ja&curr=JPY`
   - 往復: `https://www.google.com/travel/flights?q=Flights%20to%20[DEST]%20from%20[ORIG]%20on%20[OB]%20through%20[IB]&hl=ja&curr=JPY`
2. **ブラウザ自動巡回 (`scrape-async`)**:
   - `page.GotoAsync(url, PageGotoOptions(WaitUntil = DOMContentLoaded, Timeout = 30000))`
   - Cookie同意スキップ: `button[aria-label*='同意'], button[aria-label*='Accept']` を検知・クリック。
   - **検索結果カード待機ポーリング (`loop/recur`)**:
     - セレクタ: `li.pIav2d, div[role='listitem'].pIav2d, div.yR1fYc, [class*='pIav2d']`
     - 500ms 間隔で最大 10 回ポーリング。再帰ではなく `loop/recur` によりスタック消費ゼロを保証。
   - **画面キャプチャ保存**:
     - `doc/work/screenshots/yyyyMMdd-HHmmss_GoogleFlights_[taskId].png` に保存。
   - **カードDOM要素テキスト抽出**:
     - 価格: `.YMlIz.FpEdX span, span[aria-label*='円'], span[aria-label*='JPY'], [class*='YMlIz']`
     - 航空会社: `.sSHqwe.tPgKwe.ogfYpf span, .Ir0Voe .sSHqwe, [class*='sSHqwe']`
     - 発着時刻: `.dpKdp span, .mv1WYe span, [class*='dpKdp']`
     - 所要時間: `.AdWm1c.gvkrdb, .Ak5kof, [class*='gvkrdb']`
     - 乗継数: `.EfT7Ae .VG3hNb, .EfT7Ae span, [class*='VG3hNb']`
   - `parse-offer-element` による `FlightOffer` マップ変換とリスト返却。

### 6.3 Skyscanner スクレーパー (`skyscanner_scraper.clj`)

1. **検索URL生成 (`build-search-url`)**:
   - 片道: `https://www.skyscanner.jp/transport/flights/[orig]/[dest]/[yyMMdd]/?adultsv2=1&cabinclass=economy&currency=JPY`
   - 往復: `https://www.skyscanner.jp/transport/flights/[orig]/[dest]/[yyMMdd]/[yyMMdd]/?adultsv2=1&cabinclass=economy&currency=JPY`
2. **Bot検知純粋関数 (`detect-bot-challenge`)**:
   - 引数: `[title body-text px-element-found?]`
   - 判定: `px-element-found?` または `title` に `"robot"` / `"person or a robot"`、または `body-text` に `"PRESS & HOLD"` が含まれる場合に `true` を返却。
   - 単体テストで境界値を完全網羅（テスト容易性の確保）。
3. **ブラウザ自動巡回 (`scrape-async`)**:
   - **ステップ 1: トップページ事前ウォームアップ**:
     - `https://www.skyscanner.jp/` へのアクセス（`DOMContentLoaded`, 20秒）。
     - 自然なマウス移動エミュレーション（`Mouse.MoveAsync(150, 250)` ➔ `Mouse.MoveAsync(350, 450)`）。
     - Cookie同意ボタン受諾（`#accept-cookie-button` 等）。
   - **ステップ 2: Referer 付き検索遷移**:
     - Referer: `https://www.skyscanner.jp/`
     - `page.GotoAsync(url, PageGotoOptions(Referer = ..., Timeout = 60000))`
   - **ステップ 3: カード待機ポーリング & Bot自動解除 (`loop/recur`)**:
     - セレクタ: `div[data-testid='flight-card'], [data-testid='itinerary-card'], div[class*='FlightCard_']`
     - 各イテレーションでカード存在確認。カード未出現時は `detect-bot-challenge` で検証。
     - **Bot 検知時 (PRESS & HOLD)**:
       - 画面キャプチャ保存 (`yyyyMMdd-HHmmss_Skyscanner_BotChallenge_[taskId].png`)。
       - 自然なマウス軌跡移動（複数ステップで中心座標へ移動）。
       - 長押し試行: `MouseDownAsync` ➔ 5.5秒待機 ➔ `MouseUpAsync`。
       - 有頭ブラウザモード時はユーザー手動解除ガイダンスを出力し、最大 60 回（60秒）待機。
       - ワーカー停止フラグを評価し、待機中であっても即時中断可能。
   - **ステップ 4: 画面キャプチャ保存 & カードDOM抽出**:
     - キャプチャ保存 (`yyyyMMdd-HHmmss_Skyscanner_[taskId].png`)。
     - 価格、航空会社（`img[alt]` フォールバック対応）、所要時間、乗継数、時刻を抽出して `FlightOffer` リストを返却。

### 6.4 巡回ワーカー結合 (`scraping_worker.clj`)

1. **実効ヘッドレス判定**:
   ```clojure
   (let [effective-headless (and (:is-headless task-item) (:headless-mode settings))]
     ...)
   ```
2. **Playwright ライフサイクルと安全な巡回パイプライン**:
   - `Playwright/CreateAsync` によるオンデマンドインスタンス確保。
   - `scraper-common/with-scraper-lock` による多重起動排他制御。
   - `scraper-common/create-context-async` による実ブラウザ起動。
   - `context.NewPageAsync()` によるページ生成。
   - Google Flights / Skyscanner の巡回実行、結果保存（`flight_snapshots`, `task_run_logs`）、最安値判定、Webhook 通知。
   - `finally` 節における確実なリソース破棄:
     ```clojure
     (try
       (scraper-common/await-task (.CloseAsync page))
       (catch Exception _ nil))
     (scraper-common/close-context-async context)
     (.Dispose playwright)
     ```

### 6.5 テスト容易性とアーキテクチャ分離規約

- **決定論的単体テストの維持**:
  - パース関数（`parse-price-jpy`, `parse-duration-minutes`, `parse-offer-element`）および Bot検知判定（`detect-bot-challenge`）は純粋関数として単体テストで 100% 検証。
  - 実ブラウザを使用するテストは単体テスト（`./scripts/test.ps1`）では実行せず、環境変数 `FLIGHT_TRACKER_RUN_E2E=1` の場合のみ実行される統合テストとして分離。
  - これにより、オフライン・CI環境での高速・決定論的なテスト通過（カバレッジ 80% 以上）を担保。

---

## 7. テストアーキテクチャ & HTML レポート出力規約

### 7.1 Clojure 製テストランナー (`test/test_runner.clj`)

`clojure.test` の全テストスイートを実行し、以下の 2 つの視覚的 HTML レポートを生成します:

1. **テストケース合否レポート (`doc/work/TestResults/TestResults.html`)**:
   - 各テストケース名、OK(✔)/NG(❌)、所要時間、アサーション差分、スタックトレースを明示。
2. **コードカバレッジレポート (`doc/work/CoverageReport/index.html`)**:
   - 各モジュール・公開関数の実行カバレッジ（網羅率%）、未実行・実行済みコードを可視化（目標 80% 以上）。
