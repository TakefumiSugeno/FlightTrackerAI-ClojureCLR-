# タスク計画: モーダル画面の閉じる操作および親画面への復帰・ナビゲーション改善

## 1. タスク概要

- **タスクID**: `20260910-014500_fix_modal_close_navigation`
- **合意レベル**: L2 (中規模: UIナビゲーション・モーダル開閉UX挙動修正およびHTMXイベント・History API連携)
- **対象成果物**:
  - ドキュメント群: `doc/spec.md`, `doc/design_detail.md`, `doc/mock/index.html`
  - レビュー記録: `doc/tasks/20260910-014500_fix_modal_close_navigation/reviews.md`
  - ソースコード:
    - `src/FlightTrackerAI.Web/flight_tracker_ai/web/views/layout.clj`
    - `src/FlightTrackerAI.Web/flight_tracker_ai/web/views/modals.clj`
    - `src/FlightTrackerAI.Web/flight_tracker_ai/web/views/dashboard.clj`
    - `src/FlightTrackerAI.Web/flight_tracker_ai/web/controllers/api_controller.clj`
    - `src/FlightTrackerAI.Web/flight_tracker_ai/web/server.clj`
  - テストコード (1:1 対応規約):
    - `test/FlightTrackerAI.Web.Tests/flight_tracker_ai/web/views/layout_tests.clj`
    - `test/FlightTrackerAI.Web.Tests/flight_tracker_ai/web/views/modals_tests.clj`
    - `test/FlightTrackerAI.Web.Tests/flight_tracker_ai/web/views/dashboard_tests.clj`
    - `test/FlightTrackerAI.Web.Tests/flight_tracker_ai/web/controllers/api_controller_tests.clj`
    - `test/FlightTrackerAI.Web.Tests/flight_tracker_ai/web/server_tests.clj`
    - `test/FlightTrackerAI.Web.Tests/flight_tracker_ai/web/integration/integration_flow_tests.clj`
  - レポート出力:
    - `doc/work/TestResults/TestResults.html`
    - `doc/work/CoverageReport/index.html`

## 2. 現状の課題と根本原因分析

1. **二重フェッチ（HTMX と onclick の競合）によるモーダル再表示バグ**:
   - `dashboard.clj` 内のモーダル起動ボタン（新規登録、設定変更、メモ編集、詳細表示など）に、HTMX 属性 (`:hx-get` + `:hx-target`) とインライン JS (`:onclick="open...Modal()"`) の双方が指定されている。
   - 1回のクリックで2重にリクエストが送信され、閉じるボタン等を押した直後に遅延到着したレスポンスによってモーダルが再挿入・再表示され、閉じられない事象が発生する。
2. **フォーム送信（登録・更新・メモ保存・設定保存）後にモーダルが閉じないバグ**:
   - モーダル内のフォーム送信時、HTMX swap（`hx-swap="outerHTML"` 等）によって `#dashboard-container` や `#modal-container` を更新するが、モーダルを閉じる処理がサーバー返却 HTML 内のインライン `<script>` 評価に依存している。
   - HTMX のデフォルト設定や環境によってスクリプトが自動実行されず、モーダル（`#modal-container` 内）が画面前面に残存し親画面に戻れない。
3. **ブラウザの「戻る」ナビゲーション（History API / PopState）非対応**:
   - モーダル表示時にブラウザ履歴（`history.pushState` / hash）がスタックされないため、ユーザーがブラウザの戻るボタン、マウス戻るボタン、スマートフォン/タブレットのスワイプ戻る操作で親画面に戻ろうとすると、外部サイトや前画面に離脱してしまう。
   - また、UI操作で閉じた場合に履歴が適切に同期されないと「ゾンビ履歴」が発生し、後からのブラウザ戻る操作で2回押さなければ戻れなくなる。
4. **AI解析時の画面遷移の不整合と重複定義**:
   - `layout.clj` と `dashboard.clj` の双方で `parseWithAI()` が二重定義されている。
   - `layout.clj` 側の実装により新規別タブでスタンドアロン画面 (`/tasks/new`) が開かれ、元の親画面（親タブ）へのモーダル的クローズ導線が失われている。
5. **エラー時の入力データ消失リスク**:
   - バリデーションエラーや送信失敗時にモーダルが誤って閉じたり画面が崩壊すると、ユーザーの入力データが失われるため、エラー時はモーダルを維持しインラインでエラー理由を表示する必要がある。

## 3. 作業項目 (Work Items)

- [x] **WI-01: タスク計画の立案とサブエージェントレビュー・合意 (Step 1)**
  - 「ユーザー」「SE/PG」ロールによる `tasks.md` レビュー実施、指摘事項の反映と `reviews.md` 記録、ユーザー合意取得。
- [ ] **WI-02: 仕様書・設計書・UIモックの更新と合意 (Step 2)**
  - `doc/spec.md`, `doc/design_detail.md`, `doc/mock/index.html` にモーダルの閉じる操作、ブラウザ戻る対応（History API）、成功時のみ自動クローズ・エラー時モーダル維持＆インラインエラー表示、AI解析モーダル統合、背面スクロールロックを反映。
  - 「ユーザー」「SE/PG」ロールによるドキュメントレビュー実施、合意取得。
- [ ] **WI-03: `layout.clj` & `modals.clj` のモーダル制御・History API連動・イベントハンドリング改善 (Step 3, TDD)**
  - `closeCurrentModal()` の堅牢化と History API 排他制御:
    - クライアント側に `window.__modalState = { isOpen: false, isNavigatingBack: false }` を導入。
    - モーダル展開時: 既に開いていなければ `history.pushState({ modalOpen: true }, '', '#modal')` を発行し `isOpen = true`。連続展開時は `replaceState` を適用。
    - `popstate` イベント監視: ブラウザ「戻る」操作時は DOM 破棄のみ行い、重複 `history.back()` は呼ばない。
    - UI 操作（×ボタン、キャンセル、ESCキー、背景クリック、フォーム送信成功）時: `isOpen` を確認し、`history.back()` で履歴スタックを整合。
    - 入力系モーダル（新規登録・編集・全体設定）では、入力途中データの背景クリック即時破棄を防止（明示的な「キャンセル」「×」ボタン押下、または未入力時のみ閉じる）。
    - 閲覧系モーダル（詳細、ログ）は背景クリックで即座に閉じる。
    - モーダル表示中の背面スクロールロック（`document.body.classList.toggle('overflow-hidden', isOpen)`）。
  - HTMX `closeModal` イベントリスナー導入:
    - サーバーからの `HX-Trigger: closeModal` を購読し、フォーム送信成功時のみ確実にモーダルをクローズ。
  - AI解析 `parseWithAI()` の一本化:
    - `layout.clj` の重複定義を削除し、`dashboard.clj` の直接モーダル展開（`GET /api/tasks/new-modal?prompt=...`）に統一。
    - 実行中のローディングスピナー表示・連打抑止、失敗時のプロンプト保持とエラー通知。
- [ ] **WI-04: `dashboard.clj` の二重呼び出しボタンのクリーンアップ (Step 3, TDD)**
  - カードビュー・リストビュー・空状態の全モーダル呼び出しボタンから `:hx-get` と `:onclick` の競合を解消し、HTMX ベースまたは一貫したモーダル表示呼び出しに整理。
  - 二重フェッチによるモーダル復活バグを完全に排除。
- [ ] **WI-05: `api_controller.clj` および `server.clj` のモーダル連携改善 (Step 3, TDD)**
  - `server.clj` の `write-response` でカスタム HTTP レスポンスヘッダー出力をサポート（.NET の ASCII 準拠制約を守り、ヘッダー値は安全な ASCII のみ）。
  - `api_controller.clj` において:
    - 正常完了（200 OK）時: レスポンスヘッダーに `HX-Trigger: closeModal` を付与し、モーダル自動クローズをトリガー。トースト通知等の日本語メッセージはレスポンスHTML側で安全に伝達。
    - バリデーションエラー・処理失敗時: モーダルを閉じずに開いたまま維持し、モーダル内にインラインでエラー理由（赤字）を表示して再入力・再送信を可能にする。
- [ ] **WI-06: 単体・結合テストの追加・更新と検証 (Step 3, TDD)**
  - `layout_tests.clj`, `modals_tests.clj`, `dashboard_tests.clj`, `api_controller_tests.clj`, `server_tests.clj`, `integration_flow_tests.clj` に対応するテストを追加。
  - `dashboard_tests.clj`: 全ボタンから `:hx-get` と `:onclick` の重複同居がゼロ件であることを機械的にアサート。
  - `server_tests.clj`: `HttpListener` 経由でカスタムヘッダー（`HX-Trigger` 等）が正常に出力されることをアサート。
  - `api_controller_tests.clj`: 成功時の `HX-Trigger` 付与、エラー時のモーダル維持＆インラインエラー返却をアサート。
  - 全テスト実行 (`./scripts/test.ps1`) による合否確認。
- [ ] **WI-07: テスト合否レポート & カバレッジレポート & ブラウザ実機検証 (Step 3)**
  - `doc/work/TestResults/TestResults.html` の全件合格確認。
  - `doc/work/CoverageReport/index.html` の目標カバレッジ 80% 以上達成確認。
  - ブラウザ実機での「×ボタン」「キャンセル」「背景クリック」「ESCキー」「ブラウザ戻る連打」「フォーム送信成功後」「バリデーションエラー時」のエッジケース手動/実機検証。
- [ ] **WI-08: ドキュメント事後同期 & 成果物コミット (Step 4)**
  - 実装差分のドキュメント反映、サブエージェント成果物レビューとユーザー最終確認。

## 4. 受入基準 (Acceptance Criteria)

1. **閉じる導線の完全性と誤操作防止**:
   - 右上の「×」ボタン、フッターの「キャンセル」「閉じる」ボタン、モーダル背景（外側暗色部）のクリック、および `ESC` キー押下のいずれでも、確実にモーダルが閉じて親画面に戻れること。
   - ただし入力系モーダル（新規登録・編集等）で入力中データがある場合、背景誤クリックによる即時破棄を防ぐ安全対策（明示的キャンセル要求または未入力時のみ閉じる）が講じられていること。
   - 詳細モーダル（旅程タイムライン）およびログ確認モーダルも同様に確実に閉じて元の親画面へ復帰できること。
2. **二重リクエストの排除**:
   - ダッシュボード上のボタン操作でリクエストが二重送信されず、閉じたモーダルが再描画される不具合が発生しないこと。
   - テストコードで `:hx-get` と `:onclick` の同居がゼロ件であることが自動検証されていること。
3. **フォーム送信後の自動クローズとエラーハンドリング**:
   - 各フォーム送信**成功時**のみ自動的にモーダルが閉じ、親画面（ダッシュボード）が更新されて成功トーストが表示されること。
   - **バリデーションエラーや送信失敗時はモーダルを閉じずに維持**し、モーダル内にインラインでエラー理由を表示して、ユーザーが入力内容を保持したまま修正・再送信できること。
4. **ブラウザの「戻る」ボタン対応と履歴整合**:
   - モーダル表示中にブラウザの「戻る」ボタン（またはマウス・スワイプ戻る）を実行した際、外部ページに離脱せず、モーダルが閉じて前の親画面に復帰すること。
   - 「×」ボタンや「キャンセル」ボタン、フォーム送信完了でモーダルを閉じた場合もブラウザ履歴（History Stack）が正しく同期され、その後にブラウザ「戻る」を押した際に履歴の空振りや2回押さないと戻れない不具合が発生しないこと。
5. **AI解析からのモーダル連携と待機UX**:
   - AI自然言語解析を実行した際、別タブのスタンドアロン画面でなく、ダッシュボード上のモーダルとして条件が直接展開されること。
   - 実行中はスピナー等の待機案内が表示されて二重送信が防止され、解析失敗時にも入力プロンプトが保持され、エラー理由の通知と再試行が容易に行えること。
6. **テスト & カバレッジ**:
   - ソースコードとテストコードの 1:1 対応（`server.clj` ⇔ `server_tests.clj` を含む）が保たれ、全テストがパスし、カバレッジ 80% 以上を維持すること。
