# タスク計画書: Playwright実ブラウザスクレーパーの完全実装（元リポジトリ 100% 準拠）

- **タスクID**: `20260913-013600_impl_playwright_scrapers_full`
- **合意レベル**: **L3 (大規模 - Playwrightブラウザ自動操作・Bot対策・巡回ワーカー完全実装)**
- **開発ブランチ**: `alpha`
- **目的**:
  元リポジトリ（`D:\programming\repos\MyGitHubRepos\FlightTrackerAI`）で実装されていた Playwright による実ブラウザ自動操作（Google Flights, Skyscanner, Bot対策, スクリーンショット保存, 永続プロファイル）が、ClojureCLR 版においてスタブ（空配列返却）となっている問題を完全に解消し、元リポジトリと同等のスクレイピング機能を 100% 完全移植・稼働させる。

---

## 1. 現状とギャップ分析（元リポジトリ vs ClojureCLR版）

元リポジトリ（F#版）と現在の ClojureCLR 版を全ファイル比較した結果、以下の重大な機能欠落が確認された：

| モジュール | 元リポジトリ (F#) 実装内容 | 現在の ClojureCLR 版の状態 | 判定 |
| :--- | :--- | :--- | :---: |
| **`scraper_common`** | ・`ensurePlaywrightBrowsersInstalled` (Chromium自動導入)<br>・`createContextAsync` (永続プロファイル、ヘッドレス/有頭切替、Stealth/Anti-detection スクリプト注入、タイムゾーン/ロケール設定) | ・パス解決、排他ロック (`scraper-lock`)、価格/時間テキストパーサーのみ実装。<br>・**ブラウザ起動・Stealth・永続コンテキスト生成が未実装**。 | ❌ **未実装** |
| **`google_flights_scraper`** | ・`GotoAsync` + Cookie同意自動クリック<br>・セレクタポーリング待機<br>・スクリーンショット保存 (`doc/work/screenshots/`)<br>・カードDOM要素（価格、航空会社、発着時刻、時間、乗継）の抽出と `FlightOffer` 生成 | ・URL生成 (`build-search-url`) とテキストパース (`parse-offer-element`) のみ実装。<br>・**`scrape-async` 内で実DOM要素を取得せず、常に `[]` を返却するスタブ状態**。 | ❌ **スタブ** |
| **`skyscanner_scraper`** | ・トップページウォームアップ（Referer・自然なマウス移動・Cookie承諾）<br>・Bot検知（PRESS & HOLD）自動検知<br>・Bot検知時スクリーンショット保存<br>・自動長押し試行 (5.5秒)<br>・有頭手動支援待機 (最大60秒)<br>・カードDOM要素からの抽出と `FlightOffer` 生成 | ・URL生成とテキストパースのみ実装。<br>・**`scrape-async` 内で実DOM要素を取得せず、常に `[]` を返却するスタブ状態**。 | ❌ **スタブ** |
| **`scraping_worker`** | ・Playwright オンデマンド起動 (`Playwright.CreateAsync()`)<br>・ブラウザコンテキスト・ページのライフサイクル管理<br>・タスクの `is_headless` と全体設定を合成した実ブラウザ表示制御<br>・スクレイピング実行とログ記録 | ・`gf/scrape-async nil` と **`page` に `nil` を渡して呼び出し**。<br>・Playwright のインスタンス化やブラウザ起動ロジックが一切組み込まれていない。 | ❌ **未結合** |

※ その他のモジュール（Coreドメイン、SQLiteリポジトリ、AI Client、Notification Webhook、Web UI/HTMX/Tailwind）は既に完全実装済み。

---

## 2. 実装仕様とアーキテクチャ設計

### 2.1 スクレイピング共通基盤 (`scraper_common.clj`)
1. **ブラウザバイナリ自動プロビジョニング**:
   - `ensure-playwright-browsers-installed!`:
     - 初回実行時に `Microsoft.Playwright.Program/Main` を呼び出して `install chromium` を実行。
     - `atom` による4状態管理（`:uninstalled`, `:installing`, `:installed`, `:failed`）と失敗時クールダウンにより多重実行・無駄なダウンロード試行を完全抑止。
2. **ブラウザコンテキスト生成 (`create-context-async`)**:
   - `BrowserTypeLaunchPersistentContextOptions` を使用し、`get-browser-profile-dir` (`doc/work/browser_profile/`) を指定。
   - **SingletonLock 残留対策**: 起動直前に古い `SingletonLock` / `SingletonCookie` 等のロックファイルを検知・安全にクリーンアップするフォールバックを組み込み、クラッシュ後の起動失敗を防止。
   - `headless` フラグの反映（有頭時は `SlowMo: 150ms`, `--start-maximized`, `ViewportSize: nil`）。
   - Stealth スクリプトの `AddInitScriptAsync`:
     - `Object.defineProperty(navigator, 'webdriver', {get: () => undefined})`
     - `window.chrome = { runtime: {} }`
     - 言語（`ja-JP, ja`）、ロケール、パーミッションクエリ偽装。
   - 日本語ロケール (`ja-JP`)、タイムゾーン (`Asia/Tokyo`)、BypassCSP, IgnoreHTTPSErrors, Sec-Ch-Ua ヘッダーの適用。
3. **安全なクローズ (`close-context-async`)**:
   - ページ全クローズ ➔ コンテキストクローズ ➔ ブラウザクローズの各段階を個別 `try-catch` で保護し、ゾンビプロセス・メモリリークを完全防止。
   - プロセス終了時フック (`AppDomain.CurrentDomain.ProcessExit`) への登録。

### 2.2 Google Flights スクレーパー (`google_flights_scraper.clj`)
1. 検索ページ遷移: `page.GotoAsync(url, WaitUntilState.DOMContentLoaded, Timeout: 30000ms)`
2. Cookie/同意ダイアログ自動スキップ: `button[aria-label*='同意'], button[aria-label*='Accept']`
3. 検索結果カード待機ポーリング:
   - セレクタ: `li.pIav2d, div[role='listitem'].pIav2d, div.yR1fYc, [class*='pIav2d']`
   - **Clojure `loop/recur`** による非再帰的ポーリング（スタック消費ゼロ）。
4. 画面キャプチャ保存:
   - `doc/work/screenshots/yyyyMMdd-HHmmss_GoogleFlights_[taskId].png`
5. DOM要素テキスト抽出:
   - 価格: `.YMlIz.FpEdX span, span[aria-label*='円'], span[aria-label*='JPY'], [class*='YMlIz']`
   - 航空会社: `.sSHqwe.tPgKwe.ogfYpf span, .Ir0Voe .sSHqwe, [class*='sSHqwe']`
   - 発着時刻: `.dpKdp span, .mv1WYe span, [class*='dpKdp']`
   - 所要時間: `.AdWm1c.gvkrdb, .Ak5kof, [class*='gvkrdb']`
   - 乗継数: `.EfT7Ae .VG3hNb, .EfT7Ae span, [class*='VG3hNb']`
6. `parse-offer-element` による `FlightOffer` マップへの変換とリスト返却。

### 2.3 Skyscanner スクレーパー (`skyscanner_scraper.clj`)
1. トップページ事前ウォームアップ:
   - `https://www.skyscanner.jp/` へのアクセス、自然なマウス移動エミュレーション、Cookie同意受諾。
2. Referer `https://www.skyscanner.jp/` を付与した検索URLへのアクセス。
3. **Bot検知純粋判定ロジック (`detect-bot-challenge`)**:
   - タイトル・本文・CAPTCHA要素の存在に基づく判定を純粋関数として切り出し、単体テストで境界値を網羅。
4. 検索結果カード待機ポーリング:
   - **Clojure `loop/recur`** による安全なループ制御（ワーカー停止フラグ評価組込）。
   - Bot検知（PerimeterX / Cloudflare: PRESS & HOLD）の検出時は検知時スクリーンショット保存 (`yyyyMMdd-HHmmss_Skyscanner_BotChallenge_[taskId].png`)。
   - 自動長押し試行（自然なマウス軌跡ステップ移動 ➔ `MouseDownAsync` ➔ 5.5秒待機 ➔ `MouseUpAsync`）。
   - 有頭ブラウザモード時は手動認証ガイダンス出力と最大60秒待機。
5. カードDOM要素抽出と `parse-offer-element` によるオファー一覧生成。

### 2.4 巡回ワーカー結合 (`scraping_worker.clj`)
1. `execute-task-scraping`:
   - `Playwright/CreateAsync` によるオンデマンドインスタンス確保。
   - 実効ヘッドレスモード判定: `effective-headless = (:is-headless task-item) && (:headless-mode settings)`
   - `scraper-common/create-context-async` による実ブラウザ起動。
   - `context.NewPageAsync()` でページを生成し、`gf/scrape-async` および `ss/scrape-async` にページを渡して実行。
   - `finally` でページクローズ、コンテキストクローズ、Playwright インスタンス破棄を確実に実行。

---

## 3. 実装タスク手順（Step / Phase）

### Phase 1: タスク計画・レビューと合意 (Step 1)
- [x] 本タスク計画書の作成 (`tasks.md`)
- [x] サブエージェント批判的レビューの実施と `reviews.md` への記録
- [x] ユーザーへの計画提示と合意取得 (合意1回目)

### Phase 2: 仕様・設計の同期と合意 (Step 2)
- [x] `doc/spec.md` (3.4節 スクレイピング詳細) の同期（SingletonLock対策、Bot自動解除、Stealth）
- [x] `doc/design_detail.md` (スクレイパー・ワーカー詳細) の同期
- [x] サブエージェント設計レビューの実施（判定: LGTM / 合意推奨）
- [x] ユーザーへの仕様・設計提示と合意取得 (合意2回目)

### Phase 3: TDD 実装・検証サイクル (Step 3)
- [x] **Step 3-①: テスト先行作成 (Red)**
  - `test/.../scraper_common_tests.clj`: プロファイル解決、SingletonLockクリーンアップ、多重インストール防止 atom
  - `test/.../google_flights_scraper_tests.clj`: URL生成、満席/表記揺れパース、抽出ヘルパー、nilページ安全テスト
  - `test/.../skyscanner_scraper_tests.clj`: URL生成、`detect-bot-challenge` 境界値テスト、満席/ロゴ画像/乗継数パース、nilページ安全テスト
  - `test/.../scraping_worker_tests.clj`: ヘッドレス判定合成、ブラウザ起動ライフサイクル保護
- [x] **Step 3-②: 実装 (Green)**
  - `scraper_common.clj`: `ensure-playwright-browsers-installed!`, `create-context-async`, ロッククリーンアップの実装
  - `google_flights_scraper.clj`: DOMポーリング (`loop/recur`)、キャプチャ、セレクタテキスト抽出の実装
  - `skyscanner_scraper.clj`: ウォームアップ、`detect-bot-challenge`、Bot長押し、DOM抽出の実装
  - `scraping_worker.clj`: Playwright 生成、コンテキスト/ページ連携、リソース完全破棄の実装
- [x] **Step 3-③: 全テスト実行 & カバレッジレポート出力**
  - `./scripts/test.ps1` を実行し、全テスト通過 (✔ 全22スイート、540アサーション ALL PASS) およびカバレッジ 94.9% (目標 80% 大幅超過) を確認。
- [x] **Step 3-④: サブエージェント実装レビュー**
  - 実装品質・リソースリーク防止・元リポジトリ準拠性レビュー実施（判定: LGTM / 合意推奨）。
- [ ] **Step 3-⑤: ユーザー最終確認・合意 (合意3回目)**

### Phase 4: ドキュメント同期 & コミット & プッシュ (Step 4)
- [ ] ドキュメント最終同期
- [ ] コミット作成 (`feat: Playwright実ブラウザスクレーパーの完全実装`)
- [ ] `origin alpha` へのプッシュ (合意4回目/完了)

---

## 4. 成功基準 (Acceptance Criteria)
1. `gf/scrape-async` および `ss/scrape-async` が空配列スタブではなく、実際の Playwright ページとセレクタ抽出を実行すること。
2. `scraping_worker.clj` が Playwright インスタンスおよび PersistentContext を生成し、ヘッドレス/有頭モードを正しく制御して巡回を実行すること。
3. Google Flights および Skyscanner の画面キャプチャが `doc/work/screenshots/` に正しく保存されること。
4. Skyscanner の Bot 検知（PRESS & HOLD）に対して自動長押し試行および有頭手動支援待機が動作すること。
5. 全単体・統合テストが通過し、カバレッジ 80% 以上を維持すること。
