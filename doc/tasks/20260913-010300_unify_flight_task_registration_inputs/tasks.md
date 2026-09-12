# タスク計画書: 新規フライト登録における入力項目の完全統一（手動登録 vs AI解析登録）

- **タスクID**: `20260913-010300_unify_flight_task_registration_inputs`
- **合意レベル**: **L2 (中規模 - UI/UX画面項目統一・フロント/バックエンド連携)**
- **起票日時**: 2026-09-13 01:03 (改訂: 2026-09-13 01:06)
- **参照原典**:
  - `doc/mock/index.html` (UIモック)
  - `doc/spec.md` (システム仕様書)
  - `doc/design_detail.md` (詳細設計書)
  - `src/FlightTrackerAI.Web/flight_tracker_ai/web/views/modals.clj` (`render-task-modal`, `render-standalone-new-task-page`)
  - `src/FlightTrackerAI.Web/flight_tracker_ai/web/views/layout.clj` (`parseWithAI` JavaScript)
  - `src/FlightTrackerAI.Web/flight_tracker_ai/web/controllers/api_controller.clj` (`/api/tasks`, `/api/tasks/standalone`, `/api/tasks/new-modal`, `/api/ai/parse`)
  - `D:\programming\repos\MyGitHubRepos\FlightTrackerAI\src\FlightTrackerAI.Web\Views\Modals.fs` (元リポジトリ実装)

---

## 1. 現状の課題と根本原因 (Root Cause Analysis)

ユーザーより「手動で新規フライト登録する場合と、AI解析にてフライト登録する場合で、入力項目に差異がある」との指摘を受領した。
調査およびサブエージェント批判的レビューの結果、以下の重大な課題・欠陥を特定した：

1. **スタンドアロン新規登録画面 (`render-standalone-new-task-page`) における項目の欠落**:
   - 手動登録モーダル (`render-task-modal`) には存在する以下の項目が、AI解析後に別タブで開くスタンドアロン画面からすっぽり抜け落ちている：
     - **タスク名 (任意) (`title`)**: 入力フィールドが存在しない。
     - **デフォルトの Discord / Slack Webhook に通知する (`useDefaultWebhook`)**: チェックボックスが存在しない。
     - **定期巡回時もブラウザを表示する (手動支援モード) (`showBrowser`)**: チェックボックスが存在しない。
   - 根本原因: モーダルとスタンドアロン画面で12項目のフォームHTMLおよびPOST処理を別々に二重管理（コピペ）していたこと。

2. **Webhook通知チェックボックスの有名無実化バグ**:
   - ユーザーが `useDefaultWebhook` のチェックを外しても、バックエンド側でタスク個別URLが空（nil）の場合、巡回通知ワーカー（`notification.clj`）で `(or (:notification-webhook-url task-item) global-webhook)` となり、無条件で全体設定のWebhookに通知が送信されてしまう。UIの選択肢がダミーになっていた。

3. **AI解析モーダル展開 (`/api/tasks/new-modal?prompt=...`) におけるパラメータ渡し抜け**:
   - `api_controller.clj` において、OpenRouter の解析結果に `:MaxStops` が含まれているにもかかわらず、モーダル初期化用 `params` に `:maxStops` が渡されておらず、画面を開くと常にデフォルトの Any（制限なし）にリセットされてしまっていた。`:title` の初期補完も同様。

4. **スタンドアロン画面におけるバリデーションエラー時の画面崩壊**:
   - `POST /api/tasks/standalone` でエラーが発生した場合、白画面に `<p class='text-rose-400'>エラー文</p>` だけが返り、入力値が消滅してブラウザ戻るボタンが必要になっていた。

5. **全体設定の動的反映漏れと過去日付の選択リスク**:
   - スタンドアロン画面で巡回間隔が「全体設定に従う (現在 12h)」と静的にハードコードされていた。また、日付選択で本日以前の過去日を選択できてしまうリスクが存在していた。

---

## 2. 入力項目の統一仕様（全12項目 完全整合表）

以下の全12項目について、手動登録モーダル、スタンドアロン登録画面、およびAIモーダル反映のすべてで完全一致させる：

| # | 項目名 | フォーム名 (`name`) | 必須/任意 | 説明 / 選択肢 | 初期値 / AI解析反映 |
| :- | :--- | :--- | :-: | :--- | :--- |
| 1 | **旅行タイプ** | `tripType` | 必須 | 「往復」または「片道」ボタン選択 | AI解析判定値（初期値: 往復） |
| 2 | **出発地** | `origin` | 必須 | 都市名またはIATA 3レターコード (Datalist候補付き) | AI抽出空港（例: `HND - 東京(羽田)`） |
| 3 | **目的地** | `destination` | 必須 | 都市名またはIATA 3レターコード (Datalist候補付き) | AI抽出空港（例: `CDG - パリ(シャルル・ド・ゴール)`） |
| 4 | **往路出発日** | `outboundDate` | 必須 | カレンダー日付選択 (`YYYY-MM-DD`、`min`=本日) | AI抽出日付（初期値: 1ヶ月後） |
| 5 | **復路出発日** | `inboundDate` | 任意 | カレンダー日付選択 (`YYYY-MM-DD`、`min`=本日 ※片道時は非表示) | AI抽出日付（初期値: 往路+7日後） |
| 6 | **許容乗継回数** | `maxStops` | 任意 | `Any` (制限なし) / `OneStop` (1回乗継) / `DirectOnly` (直行便) | AI抽出乗継条件（初期値: `Any`） |
| 7 | **巡回間隔** | `checkIntervalHours` | 任意 | `12` (全体設定に従う・動的表示) / `3` / `6` / `12` / `24` 時間 | 初期値: `12` (全体設定に従う) |
| 8 | **目標アラート価格** | `targetPriceJpy` | 任意 | JPY 金額（円） | AI抽出予算価格 |
| 9 | **タスク名** | `title` | 任意 | 監視タスクの識別名 | AI抽出タイトルまたは `[出発地] ➔ [目的地]` 自動補完 |
| 10 | **構造化メモ / 要望・制約** | `userNotes` | 任意 | 自然言語・箇条書き要望メモ | AI抽出メモまたは入力プロンプト本文 |
| 11 | **Webhook通知設定** | `useDefaultWebhook` | 任意 | チェックボックス「デフォルトの Discord / Slack Webhook に通知する」 | 初期値: チェック ON（OFF時は通知完全停止） |
| 12 | **ブラウザ表示モード** | `showBrowser` | 任意 | チェックボックス「定期巡回時もブラウザを表示する (手動支援モード)」 | 初期値: チェック OFF (全体設定準拠) |

---

## 3. 実装タスク計画

### Phase 1: 仕様・設計・UIモックの更新と合意
- [ ] `doc/mock/index.html` の更新:
  - 手動登録モーダル (`newTaskModal`) の入力項目を全12項目に整備・確認。
- [ ] `doc/spec.md`, `doc/design_detail.md` の更新:
  - 手動登録・AI解析登録・スタンドアロン画面における入力12項目の統一仕様を明記。
  - フォーム共通部品化（`render-task-form-fields`）のコンポーネント設計を記載。
  - `useDefaultWebhook` OFF 時の通知スキップ仕様（`"DISABLED"` 格納）を明記。

### Phase 2: バックエンド & フロントエンド実装（TDD）
- [ ] **フォーム部品の共通化リファクタリング (`modals.clj`)**:
  - `render-task-form-fields` を新設し、全12項目の共通入力UIコンポーネント化（コピペ根絶）。
  - `render-task-modal` と `render-standalone-new-task-page` の双方が共通関数を呼び出す設計に統一。
  - 日付入力欄に `min`（本日日付）属性を追加し、過去日付の選択を抑止。
  - 巡回間隔の全体設定値を動的に反映表示。
- [ ] **AI解析〜フロントエンド連携の強化 (`ai_client.clj`, `layout.clj`, `api_controller.clj`)**:
  - `ai_client.clj`: AI抽出結果に `:Title`（タイトル）も含める（またはプロンプトから生成）。
  - `layout.clj`: `parseWithAI()` において、`title` を URL クエリパラメータに確実に引き渡す。
  - `api_controller.clj`: `GET /api/tasks/new-modal` において、`maxStops` と `title` を `params` に確実にマップする。
- [ ] **コントローラ共通化 & エラーハンドリング強化 (`api_controller.clj`)**:
  - `parse-task-form` ヘルパーを新設し、パラメータ正規化・バリデーション・TaskItem生成ロジックを一元化。
  - `POST /api/tasks/standalone` でバリデーションエラーが発生した場合、白画面ではなく入力値を保持してエラー表示付きで再レンダリングする。
  - `useDefaultWebhook` が OFF（未チェック）の場合、タスクの `:notification-webhook-url` に `"DISABLED"` を設定。
- [ ] **通知スキップ制御の実装 (`notification.clj`)**:
  - `(:notification-webhook-url task-item)` が `"DISABLED"` の場合、通知を確実にスキップする。
- [ ] **テストの追加・更新**:
  - `modals_tests.clj`: モーダルおよびスタンドアロン画面の双方に全12項目が存在することのテスト。
  - `api_controller_tests.clj`: スタンドアロン画面での全項目登録テスト、バリデーションエラー時の再描画テスト。
  - `notification_tests.clj`: `"DISABLED"` 時に通知が送信されないことのテスト。

### Phase 3: テスト実行・検証
- [ ] `./scripts/test.ps1` を実行し、全テスト通過 (✔) およびカバレッジ 80% 以上を確認。
- [ ] テスト合否レポート (`doc/work/TestResults/TestResults.html`) およびカバレッジレポート (`doc/work/CoverageReport/index.html`) の確認。

### Phase 4: ドキュメント同期 & コミット
- [ ] 仕様書・設計書等の最終同期。
- [ ] ガイドラインに沿った Conventional Commits コミットの作成。

---

## 4. 成功基準 (Acceptance Criteria)
1. 手動新規登録モーダルとAI解析新規登録画面（スタンドアロンおよびモーダル展開）で、入力・設定できる全12項目が完全一致していること。
2. AI解析実行時に、乗継条件 (`maxStops`) やタスク名 (`title`) が欠落せず、画面上に正しく事前入力されること。
3. AI解析経由の登録でも、Webhook通知のON/OFFや手動支援ブラウザ表示モードのON/OFFをユーザーが自由に設定して登録できること。
4. Webhook通知チェックを外した場合に、巡回時の通知が確実に停止すること。
5. スタンドアロン画面でバリデーションエラーが発生しても画面崩壊せず、入力値を維持してエラー表示されること。
6. 全テストがパスし、カバレッジ 80% 以上を維持していること。
