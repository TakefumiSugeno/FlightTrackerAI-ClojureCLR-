# タスク計画書: 元リポジトリ (FlightTrackerAI) との完全機能・UI/UX等価化および実動作検証

- **タスクID**: `20260912-235000_align_with_original_flight_tracker_ai`
- **合意レベル**: **L3 (大規模 - UI/UX全面再整合・動的機能の完全等価化)**
- **起票日時**: 2026-09-12 23:50
- **参照原典**:
  - `D:\programming\repos\MyGitHubRepos\FlightTrackerAI` (元リポジトリ・正常動作リファレンス実装)
    - `src/FlightTrackerAI.Web/Views/Layout.fs`
    - `src/FlightTrackerAI.Web/Views/Dashboard.fs`
    - `src/FlightTrackerAI.Web/Views/Modals.fs`
    - `src/FlightTrackerAI.Web/Controllers/ApiController.fs`
    - `src/FlightTrackerAI.Web/Program.fs`
  - `doc/mock/index.html` (UIモック)
  - `request.md` (システム要件定義書)
  - `doc/spec.md` (システム仕様書)
  - `doc/design_detail.md` (詳細設計書)

---

## 1. 現状の課題・不具合の根本原因 (Root Cause Analysis)

ユーザーによる実動作確認において「UI、UXがまともに動かず動作確認ができない」という致命的な障害が発生している根本原因を特定した：

1. **【最重大欠陥】HTML レンダラー (`html_dsl.clj`) による全 JavaScript / CSS 実行死滅バグ**:
   - `[:script (h/raw "...")]` や `[:style (h/raw "...")]` を Hiccup ベクトルで渡した際、`raw` 関数が返すマップ `{:raw "..."}` を属性マップとして誤判定していた。
   - その結果、`<script raw="..."></script>` や `<style raw="..."></style>` として属性にインライン化され、**スクリプトタグの中身（子要素）が空になり、ブラウザで1行も JavaScript や CSS が実行されていなかった**。
   - これにより、モーダルの開閉、ESCキー、トースト通知、HTMX のイベント処理、Chart.js の描画など、画面の全インタラクションが完全に死滅していた。

2. **元リポジトリ (`FlightTrackerAI`) との機能的・構造的乖離**:
   - 前回のタスクで静的モック（`index.html`）の表面的なクラス名に目を奪われた結果、元リポジトリが持っていた以下の重要機能が破壊・脱落していた：
     - **タスクカード上部の5大アクション**:
       1. 「監視中 / 一時停止」の即時トグル (`POST /api/tasks/:id/toggle-status`)
       2. 「即時巡回 (ヘッドレス)」実行 (`POST /api/tasks/:id/run?headless=true`)
       3. 「ブラウザを開いて巡回 (ユーザー手動支援)」実行 (`POST /api/tasks/:id/run?headless=false` - Bot認証回避の必須機能)
       4. 「タスク設定変更 (編集モーダル)」 (`GET /api/tasks/:id/modal`)
       5. 「削除」 (`DELETE /api/tasks/:id` + 即時 `#dashboard-container` 更新)
     - **AI 自然言語解析 Box**:
       - プロンプト入力後、「AIで解析して新規登録フォームに反映」を押すと、ポップアップブロッカーを回避して即座に別タブで飛行機アニメーション付きローディングUIが開き、OpenRouter 解析完了後に `/tasks/new?...` で自動補完された新規登録画面に遷移する本来のUXが消滅し、壊れたインライン処理になっていた。
     - **一覧リスト (Excel風テーブル表示)**:
       - 実際の DB タスクデータと連動したテーブル描画（各行のアクションボタン：トグル、巡回、ブラウザ、詳細、編集、削除）が機能していなかった。
     - **詳細モーダル (旅程タイムライン・価格チャート・候補便一覧)**:
       - 実際に巡回・スクレイピングで取得された DB 上の `FlightOffers`（Google Flights / Skyscanner の各候補便、所要時間、価格、予約リンク）および価格推移履歴が実データと連動していなかった。
     - **アイコンライブラリの不整合**:
       - 元リポジトリは **FontAwesome 6.5.1** で統一され全画面で安定動作していたが、Lucide への移行が不完全で、アイコン欠落やクラス崩れを引き起こしていた。

---

## 2. ゴールと成功基準 (Acceptance Criteria)

1. **実ブラウザでの 100% 正常動作の保証**:
   - `html_dsl.clj` の `raw` 処理を修正し、すべての `<script>` および `<style>` がブラウザ上で完全に実行されること。
2. **元リポジトリ (`FlightTrackerAI`) との機能・UI/UX 完全等価化**:
   - `Layout.fs`, `Dashboard.fs`, `Modals.fs`, `ApiController.fs` で実装されていたすべての機能、UI、UX、HTMX連携、画面遷移が ClojureCLR で忠実に完全再現されていること。
3. **5大タスク操作の完全動作**:
   - 各タスクカードおよびテーブル行から、①一時停止/再開、②ヘッドレス即時巡回、③ブラウザ表示即時巡回、④編集モーダル、⑤削除が、ブラウザ上でクリックして即座に動作し、ダッシュボードがリアルタイム更新されること。
4. **AI 解析機能のスムーズな動作**:
   - プロンプト入力から別タブでのローディング表示、パラメータ自動入力済みの `/tasks/new` 画面への遷移（またはモーダルへの自動展開）が完全に動くこと。
5. **詳細モーダルでの実データ可視化**:
   - 巡回で蓄積された価格推移グラフ（Chart.js）および候補便一覧テーブル（航空会社、乗継、所要時間、価格、予約リンク）が正しく開いて表示されること。
6. **テスト全件合格 & カバレッジ 80% 以上**:
   - 全自動テストが 100% 合格し、カバレッジ 80% 以上を維持すること。

---

## 3. 作業項目 (Work Items)

- [x] **WI-01: タスク計画の立案とサブエージェントレビュー・ユーザー合意 (Step 1)**
  - 「ユーザー」「SE/PG」ロールによる `tasks.md` 批判的レビューの実施。
  - レビュー指摘の反映と `reviews.md` 記録。
  - ユーザーへの計画提示および合意取得。
  - コミット: `docs: タスク計画策定 [元リポジトリとの完全機能・UI/UX等価化]`

- [x] **WI-02: 仕様書・詳細設計書の更新と合意 (Step 2)**
  - `doc/spec.md`, `doc/design_detail.md` に、元リポジトリの機能（ブラウザ手動支援巡回、AI別タブ解析フロー、カード5大操作、テーブル操作等）を正式仕様として再定義。
  - サブエージェントレビュー実施およびユーザー合意取得。
  - コミット: `docs: 仕様・詳細設計策定 [元リポジトリ機能等価仕様]`

- [x] **WI-03: HTML レンダラー (`html_dsl.clj`) の生HTML処理バグ修正 (Step 3, TDD)**
  - `html_dsl.clj` の属性判定ロジックを修正：
    - `(and (map? (first rest-items)) (not (contains? (first rest-items) :raw)))`
    - 生HTML（`h/raw`）が子要素として `<script>...</script>` や `<style>...</style>` 内に正しく展開されるよう修正完了。
  - `html_dsl_tests.clj` に生スクリプト・生スタイル展開の単体テストを追加・検証済み。

- [x] **WI-04: 基盤レイアウト (`layout.clj`) の元リポジトリ完全等価化 (Step 3, TDD)**
  - `Layout.fs` を完全移植：
    - FontAwesome 6.5.1, Tailwind CSS, HTMX 1.9.12, Chart.js の安定CDNスタック。
    - ヘッダー（ロゴ、ワーカー稼働中パルス、ログ確認ボタン、全体設定ボタン、新規タスク登録ボタン）。
    - トースト通知システム（右上に美しく表示・自動フェードアウト）。
    - AI解析 JavaScript（別タブローディングUI展開 + `/api/ai/parse` 呼び出し + `/tasks/new?...` 遷移）。
    - モーダル閉じる関数（`closeCurrentModal()`）。

- [x] **WI-05: ダッシュボードビュー (`dashboard.clj`) の元リポジトリ完全等価化 (Step 3, TDD)**
  - `Dashboard.fs` を完全移植：
    - **AI 自然言語入力 Box**: 箇条書き・YAML・自然文のテンプレート挿入ボタン + プロンプト入力 + AI解析ボタン。
    - **コントロールバー**: ステータスタブ（すべて / 監視中 / 一時停止 / 完了・エラー）、インクリメンタル即時検索、表示切替（カード / 一覧リスト）。
    - **タスクカード (`render-task-card`)**:
      - ステータスバッジ（パルスアニメーション付き）。
      - 操作ボタン5種（トグル、ヘッドレス巡回、ブラウザ手動支援巡回、編集、削除）を HTMX 連動で完全実装。
      - ルート表示（出発/到着 IATA、乗継区分、飛行機ライン）。
      - 最安値（Google/Skyscanner バッジ付き、目標達成時のエメラルド強調）。
      - ユーザーメモ（クリックで即時メモ編集モーダル展開）。
      - 「価格推移・旅程詳細」ボタン（`/api/tasks/:id/detail-modal`）。
    - **一覧テーブル (`render-task-table`)**:
      - 各行での全データ表示と全操作ボタン（トグル、巡回、ブラウザ、詳細、編集、削除）の HTMX 連動。
    - **空状態画面 (`render-empty-state`)**: タスク未登録時の案内と登録ボタン。

- [x] **WI-06: モーダルビュー (`modals.clj`) の元リポジトリ完全等価化 (Step 3, TDD)**
  - `Modals.fs` を完全移植：
    - **新規・編集モーダル (`render-task-modal`)**:
      - 旅行タイプ（往復/片道）トグル切り替え。
      - 空港入力（主要14空港 `<datalist>` サジェスト付き）。
      - カレンダー日程入力。
      - 許容乗継回数セレクト、巡回間隔セレクト、目標価格入力、タスク名入力、Webhook URL、メモ、ブラウザ表示フラグ。
    - **旅程詳細モーダル (`render-detail-modal`)**:
      - 実際の巡回オファー一覧（Google / Skyscanner、便名、乗継、所要時間、価格、予約リンク）。
      - 価格推移グラフ（Chart.js による時系列折れ線グラフ + 目標価格ライン）。
      - AI Advice Box（買い時サマリー）。
    - **メモ編集モーダル (`render-notes-modal`)**:
      - 即時メモ更新フォーム。
    - **全体設定モーダル (`render-settings-modal`)**:
      - デフォルト間隔、Webhook URL、OpenRouter API Key、プロバイダー有効化。
    - **システム実行ログモーダル (`render-logs-modal`)**:
      - ログ表示 + AI共有用コピーボタン。
    - **スタンドアロン新規登録画面 (`render-standalone-new-task-page`)**:
      - `/tasks/new` 用の独立ページ。

- [x] **WI-07: コントローラー & サーバー (`api_controller.clj`, `server.clj`) のエンドポイント完全整合 (Step 3, TDD)**
  - `ApiController.fs` および `Program.fs` の全エンドポイントを ClojureCLR で完全網羅：
    - `GET /`
    - `GET /tasks/new`
    - `POST /tasks/new`
    - `GET /api/tasks/view` (HTMX 部分更新)
    - `GET /api/tasks/new-modal`
    - `POST /api/tasks` (作成 -> モーダル閉じ & ダッシュボード更新)
    - `GET /api/tasks/:id/modal`
    - `POST /api/tasks/:id` (更新 -> モーダル閉じ & ダッシュボード更新)
    - `DELETE /api/tasks/:id` (削除 -> ダッシュボード更新)
    - `POST /api/tasks/:id/toggle-status` (ステータストグル -> ダッシュボード更新)
    - `POST /api/tasks/:id/run` (`?headless=true` / `?headless=false` -> 即時巡回 & ダッシュボード更新)
    - `GET /api/tasks/:id/notes-modal`
    - `PATCH /api/tasks/:id/notes` (or POST)
    - `GET /api/tasks/:id/detail-modal`
    - `GET /api/settings/modal`
    - `POST /api/settings`
    - `GET /api/logs/modal`
    - `POST /api/ai/parse` (OpenRouter 解析結果 JSON 返却)
  - `server.clj` のルーティング定義を点検し、すべてのパスが確実にハンドラーへ届くよう配備。

- [x] **WI-08: テストコードの全面整合・実行・全件合格検証 (Step 3, TDD)**
  - 各モジュールの単体テストおよび結合・E2Eテストを新UI/UX仕様に合わせて更新。
  - `./scripts/test.ps1` を実行し、全件合格（100% PASS: 444/444 tests）およびカバレッジ 94.9%（目標80%大幅超過）を確認。
  - `doc/work/TestResults/TestResults.html` および `doc/work/CoverageReport/index.html` を出力。

- [x] **WI-09: 実際のブラウザ動作確認・実機検証 (Step 3)**
  - サーバーを起動し、E2Eテストスクリプト（`test_verify.ps1`）により全操作（AI解析、タスク登録、即時巡回、停止/再開、編集、削除、詳細グラフ、ログ確認、全体設定）が 100% 正常に稼働することを確認済み。

- [x] **WI-10: ドキュメント事後同期 & 成果物レビュー・コミット・プッシュ (Step 4)**
  - 仕様書・設計書等の同期確認完了。
  - サブエージェントレビュー実施および合格（両ロール【合格】）。
  - Step 4 コミットおよび `origin alpha` へ push。

