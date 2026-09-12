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

### 2.1 採用アーキテクチャ: `Hiccup風 HTML DSL (Clojure)` + `HTMX`

- **サーバーサイド レンダリング (SSR)**:
  - Clojure 標準のデータ構造（ベクタ・マップ・キーワード）を用いた **Hiccup 風 HTML 生成エンジン (`html_dsl.clj`)** を採用。
  - マークアップをすべて純粋関数（`[:div {:class "..."} ...]`）として記述。ドメインデータ構造から安全・高速に HTML 文字列へ変換。
- **動的更新 & 画面対話**:
  - **HTMX**: ページ全体の再読み込みを行わず、タスクの登録・削除・即時実行・フィルタリング時にサーバーから返却される HTML フラグメント（`/fragments/*`）を部分置換。
- **UIライブラリ & スタイル標準 (モック原典準拠)**:
  - **Lucide Icons**: モック原典と完全一致させるため、`<script src="https://unpkg.com/lucide@latest"></script>` を採用。FontAwesome は全廃し、細線でモダンな航空券ダッシュボード表現に統一。
  - **HTMX ライフサイクル連携**: HTMX による動的 DOM 差し替え（`hx-swap`, OOB swap）時にもアイコンが正常に SVG 展開されるよう、以下のグローバルフックを `layout.clj` に設置:
    ```javascript
    document.addEventListener("htmx:afterSwap", function () {
      if (window.lucide) lucide.createIcons();
    });
    document.addEventListener("DOMContentLoaded", function () {
      if (window.lucide) lucide.createIcons();
    });
    ```
  - **Tailwind CSS カスタムパレット**: `<script>` 内で `tailwind.config` を定義し、モック原典と同一の `skyline` カラーパレット（`#0284c7`, `#0369a1`, `#075985`, `#0c4a6e`, `#082f49` 等）を提供。
- **クライアント側インタラクティブ処理**:
  - Excel風テーブルのインクリメンタル絞り込み・ソート、アクティブフィルタチップバーの同期、および Chart.js との連携を Vanilla JS で軽量に実装。
  - 表示切り替え（カード ⇔ リスト）を行っても、絞り込み状態や検索語句が破棄されずシームレスに維持されるクライアント状態管理を担保。

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

クライアント側のイベントモデルとブラウザ履歴を整合させるため、以下の状態管理と排他制御を導入します:

1. **二重フェッチの完全排除**:
   - `dashboard.clj` 内の全ボタン（新規登録・編集・メモ・詳細）から `:hx-get` と `:onclick` の同居を廃止し、HTMX による宣言的ロード（`:hx-get` + `:hx-target="#modal-container"`）に統一。
   - インライン JS での重複 fetch を全廃し、1クリック＝1リクエストを保証。

2. **History API 排他制御ステートマシン**:
   - クライアント側で `window.__modalState = { isOpen: false, isNavigatingBack: false }` を保持。
   - **初期ロード時の URL サニタイズ**: ページ読み込み時に URL に `#modal` が残存している場合は `history.replaceState(null, '', window.location.pathname)` でクリーンアップし、履歴破損を防止。
   - **モーダルオープン時**:
     - `htmx:afterSwap`（ターゲットが `#modal-container` かつモーダル要素存在時）を検知。
     - `!window.__modalState.isOpen` の場合、`history.pushState({ modalOpen: true }, '', '#modal')` を発行し、`isOpen = true`。
     - 既に開いている状態でのモーダル差し替え（連続展開）時は `history.replaceState` を適用。
     - `document.body.classList.add('overflow-hidden')` で背面スクロールをロック。
   - **ブラウザ戻る操作 (`popstate`) 時**:
     - `window.__modalState.isOpen` の場合、DOM 上のモーダルを消去し、`isOpen = false` に設定（`history.back()` は呼ばない）。
     - `document.body.classList.remove('overflow-hidden')` でスクロールロック解除。
   - **UI 操作（×ボタン、キャンセル、背景クリック、ESCキー、保存成功）時**:
     - `window.__modalState.isOpen` の場合、`history.state?.modalOpen` または `location.hash === '#modal'` を確認の上、`history.back()` を発行して履歴を整合。直後の `popstate` はフラグにより二重処理を抑止。

3. **ダーティフォーム保護 & IME安全機構**:
   - フォーム各入力項目の「初期表示時の値からの変更有無」を追跡し、変更がある場合のみ背景クリックによる即時破棄を抑止。明示的な「キャンセル」ボタンまたは「×」ボタン押下によってのみクローズ可能とする。
   - `keydown` イベント監視時、`e.isComposing || e.keyCode === 229` の場合は ESC キー押下であってもモーダルクローズを抑止し、日本語入力変換中の意図しない消去を保護。

### 2.4 HTTP ヘッダー伝搬・レスポンス設計 (HTTP Header & Error Handling)

1. **カスタムレスポンスヘッダーの ASCII 安全な出力 (`server.clj`)**:
   - .NET `HttpListenerResponse.AddHeader` の HTTP/1.1 ASCII 準拠制約を満たすため、`server.clj` の `write-response` でヘッダーマップ（例: `{"HX-Trigger" "closeModal"}`）を安全に設定。
   - 正規表現 `^[\x20-\x7E]+$` により ASCII 安全性を検証した上で `.AddHeader` を実行。日本語等のマルチバイト文字列はヘッダー値に含めず、レスポンス HTML ボディ側で渡す。

2. **正常系と異常系の HTMX swap 分離設計 (`api_controller.clj`)**:
   - モーダル内フォームの `:hx-target` はモーダル自身（または `#modal-container`）とし、親画面の破壊を防止。
   - **正常終了時 (200 OK)**:
     - レスポンスヘッダー: `{"HX-Trigger" "closeModal"}`
     - レスポンスボディ: `<div id="dashboard-container" hx-swap-oob="outerHTML">...最新ダッシュボードHTML...</div>`
     - クライアント側 `document.body.addEventListener('closeModal', ...)` が発火し、確実にモーダルをクローズ。
   - **バリデーションエラー・送信失敗時 (400 Bad Request / 200 エラー表示)**:
     - `HX-Trigger: closeModal` は出力せず、インライン赤字エラーメッセージを含めたモーダル HTML を返却。モーダルおよびユーザー入力値を完全に維持し、最初のエラー項目へ自動フォーカスを誘導。

### 2.5 モック完全準拠フロントエンド・コンポーネント詳細設計

1. **新規タスク登録・タスク編集モーダル (`#newTaskModal` / `#editModal`)**:
   - **旅行タイプトグルスイッチ**:
     - `往復` (`RoundTrip`) / `片道` (`OneWay`) の2ボタン切替。
     - 片道選択時は復路出発日入力コンテナ（`#inboundDateContainer`）を `display: none` に動的制御。
   - **主要空港サジェスト (`<datalist id="airportsList">`)**:
     - 国内主要空港（HND, NRT, KIX, ITM, FUK, CTS）および主要国際空港（CDG, LHR, LAX, SFO, HNL, BKK, SIN, TPE）を datalist に配備。
     - サーバー側の `extract-iata-code` 関数により、「`HND - 東京(羽田)`」形式の文字列から先頭の3文字 IATA コードを抽出・サニタイズしてドメインモデルへ格納。
   - **許容乗継回数 (Max Stops) セレクト**:
     - `Any` (乗継制限なし・最安重視・推奨)、`1` (1回乗継まで)、`DirectOnly` (直行便のみ・0回乗継)。
   - **巡回間隔セレクト**:
     - `default` (全体設定に従う)、`3` (3時間ごと)、`6` (6時間ごと)、`12` (12時間ごと)、`24` (24時間ごと)。
   - **優先航空会社 & メモ**:
     - `preferredAirlines`（テキスト入力、カンマ区切り可）および `userNotes`（複数行テキストエリア）。
   - **Discord Webhook 通知**:
     - チェックボックス `useDefaultWebhook`。

2. **Excel風一覧リストの複合ドロップダウンフィルタ・ソートロジック (`#listView`)**:
   - **状態変数**:
     - `currentStatusFilter` (`all`, `active`, `paused`, `error`, `empty`)
     - `currentRouteFilter` (`all`, または選択されたルート)
     - `currentAirlineFilter` (`all`, または選択された航空会社)
     - `currentSortPriceOrder` (`none`, `asc`, `desc`)
   - **ドロップダウン制御**:
     - `#statusDropdown`, `#routeDropdown`, `#airlineDropdown` の開閉・トグル制御およびドキュメント外側クリックによる自動閉鎖。
   - **アクティブフィルタチップスバー連動**:
     - `#chipStatus`, `#chipRoute`, `#chipAirline` の表示・個別解除ボタン（✕）および「全解除」ボタンによる即時クリア。
   - **価格ソート機能**:
     - `#btnSortPrice` クリック時に `asc` ➔ `desc` ➔ `none` を巡回し、DOM 上の `tableRow` 要素の `data-price` 属性に基づき並び替え。

3. **専用削除確認モーダル (`#deleteModal`)**:
   - ブラウザ標準の `confirm()` ダイアログを全廃。
   - モックと同一の赤い警告アイコン（`alert-triangle`）、対象タスクのルート名、データ完全削除の注意文言を表示。
   - 「キャンセル」でモーダルを閉じ、「削除する」ボタンで `DELETE /api/tasks/{id}` を呼び出して安全に削除完了・OOB更新。

4. **詳細モーダル (`#detailModal`)**:
   - AI Advice Box（買い時サマリー）。
   - 旅程タイムライン: 往路セクションおよび復路セクションにおいて、区間ごとの航空会社バッジ、便名、発着時刻、乗継待ち時間をカード階層表示。
   - 価格推移チャート (Chart.js): 期間選択タブ (3日, 7日, 14日, 全期間) と Google Flights / Skyscanner / 目標価格の折れ線比較。
   - 複数便比較テーブル: 各候補便の所要時間・乗継・総額価格・予約リンク表示。

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

---

## 7. テストアーキテクチャ & HTML レポート出力規約

### 7.1 Clojure 製テストランナー (`test/test_runner.clj`)

`clojure.test` の全テストスイートを実行し、以下の 2 つの視覚的 HTML レポートを生成します:

1. **テストケース合否レポート (`doc/work/TestResults/TestResults.html`)**:
   - 各テストケース名、OK(✔)/NG(❌)、所要時間、アサーション差分、スタックトレースを明示。
2. **コードカバレッジレポート (`doc/work/CoverageReport/index.html`)**:
   - 各モジュール・公開関数の実行カバレッジ（網羅率%）、未実行・実行済みコードを可視化（目標 80% 以上）。
