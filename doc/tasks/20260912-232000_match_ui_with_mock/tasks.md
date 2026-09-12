# タスク計画: Web UI のモック (mock/index.html) 完全準拠化およびデザイン・操作性一致修正

## 1. タスク概要

- **タスクID**: `20260912-232000_match_ui_with_mock`
- **合意レベル**: L2 (中規模: UIデザイン・アイコン・モーダル構成・Excel風フィルタ機能のモック完全準拠化)
- **対象成果物**:
  - ドキュメント群:
    - `doc/spec.md`
    - `doc/design_detail.md`
    - `doc/mock/index.html` (原典確認・維持)
  - レビュー記録:
    - `doc/tasks/20260912-232000_match_ui_with_mock/reviews.md`
  - タスク管理:
    - `doc/tasks/20260912-232000_match_ui_with_mock/tasks.md`
  - ソースコード:
    - `src/FlightTrackerAI.Web/flight_tracker_ai/web/views/layout.clj`
    - `src/FlightTrackerAI.Web/flight_tracker_ai/web/views/dashboard.clj`
    - `src/FlightTrackerAI.Web/flight_tracker_ai/web/views/modals.clj`
    - `src/FlightTrackerAI.Web/flight_tracker_ai/web/controllers/api_controller.clj`
    - `src/FlightTrackerAI.Web/flight_tracker_ai/web/server.clj`
  - テストコード (1:1 対応規約):
    - `test/FlightTrackerAI.Web.Tests/flight_tracker_ai/web/views/layout_tests.clj`
    - `test/FlightTrackerAI.Web.Tests/flight_tracker_ai/web/views/dashboard_tests.clj`
    - `test/FlightTrackerAI.Web.Tests/flight_tracker_ai/web/views/modals_tests.clj`
    - `test/FlightTrackerAI.Web.Tests/flight_tracker_ai/web/controllers/api_controller_tests.clj`
    - `test/FlightTrackerAI.Web.Tests/flight_tracker_ai/web/server_tests.clj`
  - レポート出力:
    - `doc/work/TestResults/TestResults.html`
    - `doc/work/CoverageReport/index.html`

## 2. 現状の課題とモック（doc/mock/index.html）との乖離点

現状の ClojureCLR Web 実装と `doc/mock/index.html` を詳細比較した結果、以下の主要な乖離が存在することが判明しました:

1. **アイコンライブラリの不一致 (Lucide Icons vs FontAwesome)**:
   - **モック**: `Lucide Icons` (`<script src="https://unpkg.com/lucide@latest"></script>`, `<i data-lucide="..."></i>`) を採用しており、モダンで統一された細線ストロークデザイン。
   - **実装**: `FontAwesome 6` (`fa-solid ...`) を使用しており、アイコンの形状、線の太さ、余白感がモックと大きく乖離。
2. **ヘッダー (Header) のレイアウト・ボタン構成**:
   - **モック**: ロゴ（`plane`）、タイトル（`FlightTrackerAI`）、サブタイトル、巡回ワーカー稼働バッジ、全体設定ボタン（`sliders` + "全体設定" テキスト）、新規タスク登録ボタン（`plus` + "新規タスク登録"）。
   - **実装**: ヘッダーに FontAwesome の「ログ確認」ボタンが配置され、全体設定ボタンがテキスト無しの歯車アイコンのみになっているなど構成が不一致。
3. **新規タスク登録モーダル (`#newTaskModal`) および編集モーダル (`#editModal`) のフォーム構成**:
   - **モック**:
     - 旅行タイプ切り替え（「往復」「片道」のトグルスイッチ。片道選択時は復路日入力欄が連動して非表示）。
     - 出発地・目的地に主要空港（羽田、成田、関空、伊丹、福岡、新千歳、CDG、LHR、LAX、SFO、HNL、BKK、SIN、TPE等）の `<datalist id="airportsList">` 補完サジェスト。
     - 許容乗継回数 (Max Stops) セレクト（「乗継制限なし」「1回乗継まで」「直行便のみ」）。
     - 巡回間隔セレクト（全体設定に従う / 3h / 6h / 12h / 24h）。
     - 目標アラート価格 (JPY)、優先航空会社 (任意)、構造化メモ（任意）。
     - 「デフォルトの Discord Webhook に通知する」チェックボックス。
     - アクションボタン: 「キャンセル」「登録して巡回開始」。
   - **実装**:
     - タスク名入力欄、IATA直打ち（サジェストなし）、日付常時2枠、許容乗継回数選択不可、巡回間隔が数値入力、優先航空会社入力不可など、モックのフォーム定義と著しく乖離。
4. **一覧リスト (Excel風) (`#listView`) のフィルタ・ソート機能**:
   - **モック**:
     - 列ヘッダーに Excel 風フィルタドロップダウンボタン（ステータス `#statusDropdown`、区間 `#routeDropdown`、航空会社 `#airlineDropdown`）。
     - ドロップダウン内でのチェックボックス選択による複合絞り込み。
     - 価格列の昇順/降順ソート。
     - アクティブフィルタチップスバー（`#activeFilterChipsBar`）との完全連動。
   - **実装**: 静的な `<th>` のみで、モックの最大の特徴である Excel 風インタラクティブフィルタ・ソートが未実装。
5. **削除確認ダイアログの乖離 (専用モーダル vs ブラウザネイティブ confirm)**:
   - **モック**: 専用の警告モーダルUI (`#deleteModal` - 警告アイコン、対象ルート名、データ完全削除の注意文、キャンセル/削除ボタン)。
   - **実装**: ブラウザ標準のネイティブ `confirm()` で済ませており、モックのデザインが欠落。
6. **詳細モーダル (旅程タイムライン & 比較) の視覚表現**:
   - **モック**: AI Advice Box（買い時サマリー）、往路・復路のセグメント別旅程タイムライン（乗継空港・レイオーバー時間・便名・航空会社の階層カード表示）、Chart.js チャート（期間切替タブ付き）、複数候補便比較テーブル。
   - **実装**: 単純なテキストと簡易ボックスになっており、モックの豊かなビジュアルタイムラインが再現されていない。
7. **全体設定モーダル (`#settingsModal`) の構成**:
   - **モック**: デフォルト巡回間隔セレクト (3h, 6h, 12h, 24h)、デフォルト Discord Webhook URL & 「テスト送信」ボタン、OpenRouter API Key、プロバイダー有効化チェックボックス（Google Flights, Skyscanner）。
   - **実装**: 数値入力や不要なヘッドレス設定など、モックの構成と異なる。
8. **Tailwind CSS カスタム設定 (`skyline` カラー等) と Lucide 初期化**:
   - モック固有の Tailwind カラー設定、動的アイコン描画（`lucide.createIcons()`）が未導入。

## 3. 作業項目 (Work Items)

- [x] **WI-01: タスク計画の立案とサブエージェントレビュー・ユーザー合意 (Step 1)**
  - 「ユーザー」「SE/PG」ロールによる `tasks.md` 批判的レビューの実施。
  - レビュー指摘の反映と `reviews.md` 記録。
  - ユーザーへの計画提示および合意取得。
  - コミット: `docs: タスク計画策定 [UIのmock完全準拠化・デザイン一致修正]`
- [x] **WI-02: 仕様書・詳細設計書の更新と合意 (Step 2)**
  - `doc/spec.md`, `doc/design_detail.md` にモック準拠のUI仕様（Lucideアイコン、Excel風フィルタ、モーダル構成、削除モーダル等）を反映。
  - 「ユーザー」「SE/PG」ロールによるドキュメントレビュー実施、合意取得。
  - コミット: `docs: 仕様・詳細設計策定 [UIのmock完全準拠化]`
- [ ] **WI-03: 基盤レイアウト・Tailwind設定・Lucide Icons 移行 (Step 3, TDD)**
  - `layout.clj`:
    - Tailwind 設定（`skyline` パレット）を追加。
    - `Lucide Icons` CDN の導入と FontAwesome の全廃・置換。
    - HTMX swap 後および動的要素挿入時に自動で `lucide.createIcons()` を呼び出すグローバルハンドラーの導入。
    - ヘッダーをモックと完全一致（ロゴ、テキスト、ワーカーバッジ、全体設定ボタン、新規登録ボタン）。※ログ確認モーダルへの導線はモック全体の景観を損ねない適切な配置（フッターまたは全体設定内）へ調整。
- [ ] **WI-04: 新規タスク登録・タスク編集モーダルのモック完全準拠化 (Step 3, TDD)**
  - `modals.clj` & `api_controller.clj`:
    - 往復/片道のトグルスイッチUIおよび復路入力欄の表示/非表示連動。
    - `<datalist id="airportsList">` による主要空港サジェスト（都市名・空港名・IATAコード）。
    - 許容乗継回数（Max Stops: Any, 1, DirectOnly）のセレクトボックス化。
    - 巡回間隔セレクト（全体設定に従う / 3h / 6h / 12h / 24h）。
    - 優先航空会社入力欄、構造化メモ入力欄の追加。
    - 「デフォルトの Discord Webhook に通知する」チェックボックス。
    - モックと同一のクラス名・パディング・ボタン配置・Lucideアイコン。
    - 編集モーダル (`#editModal`) も同様にモック仕様へ刷新。
- [ ] **WI-05: 削除確認モーダル (`#deleteModal`) の実装 (Step 3, TDD)**
  - ブラウザネイティブ `confirm()` を廃止し、モック準拠の `#deleteModal` UI を実装。
  - 削除対象タスクのルート名・注意文言を表示し、安全に削除処理（DELETE API）を実行。
- [ ] **WI-06: Excel風一覧リスト (`#listView`) のインタラクティブフィルタ・ソート実装 (Step 3, TDD)**
  - `dashboard.clj`:
    - ステータス列・区間列・最安航空会社列に Excel 風ドロップダウンフィルタボタンを設置。
    - クライアントサイド JS によるリアルタイム複合フィルタリングロジック。
    - 価格列の昇順/降順ソート機能。
    - アクティブフィルタチップスバー（`chipStatus`, `chipAirline`, `chipRoute`）の完全同期。
- [ ] **WI-07: カードビュー (`#cardsView`) および詳細モーダル (`#detailModal`) のモック一致化 (Step 3, TDD)**
  - カードビューのレイアウト、バッジ、Lucideアイコン、アクションボタンをモックと一致。
  - 詳細モーダルのタイムライン（セグメント階層カード、乗継情報、航空会社バッジ）の視覚的再現。
  - チャートの期間選択タブ (3日/7日/14日/全期間) およびデザインの洗練。
- [ ] **WI-08: 全体設定モーダル (`#settingsModal`) のモック完全準拠化 (Step 3, TDD)**
  - セレクト式デフォルト間隔、Webhook URL & テスト送信、プロバイダー有効化チェックボックス等、モックと完全に一致。
- [ ] **WI-09: 単体・結合テストの更新・実行・全件合格検証 (Step 3, TDD)**
  - `layout_tests.clj`, `dashboard_tests.clj`, `modals_tests.clj`, `api_controller_tests.clj` 等のテストを改修後のUIに合わせて更新。
  - `./scripts/test.ps1` を実行し、全テスト合格（OK）およびカバレッジ 80% 以上を確認。
  - `doc/work/TestResults/TestResults.html` および `doc/work/CoverageReport/index.html` を出力。
- [ ] **WI-10: ドキュメント事後同期 & 成果物レビュー・コミット (Step 4)**
  - 実装後の仕様書・詳細設計書同期。
  - サブエージェントレビュー実施およびユーザー最終確認。

## 4. 受入基準 (Acceptance Criteria)

1. **デザイン・視覚的一致**:
   - `doc/mock/index.html` をブラウザで開いた見た目と、ローカルサーバー（`http://localhost:5000`）で開いたUIが、ヘッダー、カードビュー、Excel風リストビュー、各モーダル（新規、編集、設定、削除、詳細、メモ）において視覚的・構造的に一致していること。
2. **Lucide Icons の完全適用**:
   - FontAwesome アイコンが完全に排除され、全画面でモックと同一の Lucide Icons が正しく描画されていること。
   - HTMX の動的ロードやモーダルオープン時にもアイコンが正常にレンダリングされること（`lucide.createIcons()`）。
3. **フォームと操作性の完全性**:
   - 新規登録・編集モーダルで「往復」「片道」の切り替え、空港サジェスト、乗継回数選択、巡回間隔選択、優先航空会社指定、メモ保存が正常に機能すること。
   - 削除操作時にネイティブ `confirm()` ではなく、モック準拠の `#deleteModal` が表示され、確実に削除できること。
4. **Excel風リストビューのフィルタ・ソート**:
   - リストビューの各ドロップダウン（ステータス、区間、航空会社）から動的に絞り込みが行え、価格ソートおよびチップスバーの連動が機能すること。
5. **テストと品質担保**:
   - 全自動テストが 100% 通過（エラー・失敗ゼロ）し、カバレッジが 80% 以上を維持していること。
   - 前タスクで修正した「モーダルが確実に閉じる」「ブラウザ戻るで親画面に戻れる」ナビゲーション挙動が一切退行（デグレード）していないこと。
