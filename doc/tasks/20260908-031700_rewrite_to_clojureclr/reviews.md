# サブエージェント批判的レビュー記録: ClojureCLR (.NET 10) 全面リライトタスク計画

- **タスクID**: `20260908-031700_rewrite_to_clojureclr`
- **合意レベル**: L3 (大規模: ClojureCLR (.NET 10) へのアーキテクチャ再構築・全面リライト)
- **対象**: `doc/tasks/20260908-031700_rewrite_to_clojureclr/tasks.md`

---

## 1. 第1回レビュー結果（タスク計画レビュー）

2026年9月8日、「ユーザー」ロールおよび「SE/PG」ロールのサブエージェントにより、初期タスク計画に対する批判的レビューを実施。

### 1.1 ユーザーロール レビュー結果

**【判定】: 要改善 (Conditional Approval with Revisions)**

- **🚨 Critical-01: Bot検知・有頭手動支援（PRESS & HOLD）発生時のUI通知・ユーザー誘導導線の欠落**
  - **指摘内容**: Skyscanner等のKasada/PerimeterXによる `PRESS & HOLD` チャレンジ検知時、有頭モードで最大60秒待機しユーザー手動解除を待つ仕様があるが、Web画面側でユーザーに手動支援を促す通知・誘導の仕組みがタスク計画から欠落している。
  - **対応方針**: WI-04（ワーカー）および WI-05（WebUI）に、チャレンジ検知時にヘッダーステータスやトースト通知でガイダンス（「認証チャレンジを検知しました。画面上のブラウザで長押しを解除してください（残り◯秒）」）を表示し、解除完了時に巡回再開フィードバックを返す仕様・タスクを追加する。
- **🚨 Critical-02: AIアシスタント（自然言語・構造化テキスト入力）から新規登録フォームへの連携フローの欠落**
  - **指摘内容**: モック上部に常設されているAIアシスタント機能（テンプレート選択・AI解析・新規登録モーダルへの自動流し込み補完）の操作連携フローが作業項目として具体化されていない。
  - **対応方針**: WI-05 に「AIアシスタント入力エリア（テンプレート挿入ボタン群含む）」の実装およびパース結果を新規登録モーダルへ自動バインドする連携処理を明記する。
- **⚠️ Major-01: クイックメモ編集（User Notes）モーダル・即時更新導線の欠落**
  - **指摘内容**: カード・リストのメモをクリックして即座に編集できる「クイックメモ編集モーダル」が WI-05 のモーダル一覧から抜けている。
  - **対応方針**: WI-05 に `openQuickNoteModal` および `PATCH /api/tasks/:id/notes` の連携処理を追加する。
- **⚠️ Major-02: カード表示 ⇔ Excel風リスト表示間の双方向フィルタ・ソート状態の同期・保持**
  - **指摘内容**: 表示切替（カード ⇔ リスト）を行っても絞り込み状態や検索語句、アクティブフィルタチップがリセットされない状態同期処理の要件が欠落している。
  - **対応方針**: WI-05 に表示切替時のフィルタ・ソート状態保持処理およびアクティブフィルタチップバー（個別/一括クリア）の実装・検証を明記する。
- **⚠️ Major-03: エラー発生時の人間向けエラー理由表示と手動再試行（リカバリ）導線**
  - **指摘内容**: 例外メッセージをユーザーフレンドリーな説明文に変換する処理や、UI上の手動再試行ボタンの連動が計画に明記されていない。
  - **対応方針**: WI-03 にエラー理由変換ロジック、WI-05 に再試行ボタン（即時実行API呼び出し・トースト連携）を追加する。
- **⚠️ Major-04: 即時巡回実行時のUIフィードバック（排他制御ロック中の案内）**
  - **指摘内容**: 即時実行ボタン押下時のスピナー回転、排他ロック中の待機案内メッセージ返却が計画に抜けている。
  - **対応方針**: WI-05 にローディング状態、トースト通知、および排他ロック中の適切なメッセージ返却を明記する。
- **ℹ️ Minor-01: 「目標達成」バッジとステータスタブの概念整理**
  - **対応方針**: `active` かつ `最安値 <= 目標価格` の派生状態としてUIバッジを整理し、フィルタリング条件に対応させる。
- **ℹ️ Minor-02: 初回利用・空状態（Empty State / 0件画面）時のオンボーディング誘導**
  - **対応方針**: WI-05 に「登録タスク0件時の空状態（Empty State）画面」の実装・検証を明記する。
- **ℹ️ Minor-03: 航空業界標準タイムゾーン表記および翌日到着マーク（`(+1)`）のUIレンダリング保証**
  - **対応方針**: WI-05 に `(+1)` や JST タイムスタンプフォーマットのレンダリング単体テストを明記する。

---

### 1.2 SE/PGロール レビュー結果

**【判定】: 条件付き合格 (CONDITIONAL PASS - 要計画修正)**

- **🚨 Critical-01: ClojureCLR におけるコードカバレッジ (`CoverageReport/index.html`) および HTML テストレポートの技術的実現性の検証不足**
  - **指摘内容**: ClojureCLR スクリプトを動的ロード・評価する場合、Coverlet による行カバレッジ測定が行われない可能性がある。また `clojure.test` の結果を `dotnet test` の TRX/HTML ロガーへ連携するブリッジが必要。
  - **対応方針**:
    1. `FlightTrackerAI.Tests` を .NET 10 テストプロジェクト（xUnit / NUnit）として構成し、ClojureCLR テストスイートを実行して TRX/HTML レポート（`doc/work/TestResults/TestResults.html`）を生成するブリッジを WI-02 に明記。
    2. AOT コンパイルによる Coverlet 行カバレッジ収集を検証しつつ、スクリプト実行時における全公開関数の正常・境界・異常系 100% テスト網羅基準を定義。
- **🚨 Critical-02: Playwright .NET の非同期 API (`Task`/`ValueTask`) と ClojureCLR の Interop（デッドロック対策）の設計欠落**
  - **指摘内容**: Playwright は完全非同期 (`Task<T>`) であり、ClojureCLR からナイーブに `.Result` 等を呼ぶとデッドロックやスレッド枯渇を招く。
  - **対応方針**: WI-04 の `scraper_common.clj` に、安全な非同期 Task 解決ユーティリティ (`await-task`, `await-task-result` / `Task.Run` + `ConfigureAwait(false)`) を共通基盤として実装・テストするサブタスクを追加する。
- **🚨 Critical-03: ASP.NET Core Kestrel / Minimal API と ClojureCLR の配線アーキテクチャの具体化不足**
  - **指摘内容**: Minimal API や Kestrel の C# 拡張メソッド・ジェネリックデリゲートを ClojureCLR から直接扱うのは Interop 難易度が高い。
  - **対応方針**: WI-02 / WI-05 において、薄い C# ホスト（`Program.cs`）を用意し、Minimal API のルーティングから ClojureCLR の関数ハンドラー（リクエストマップを受け取りレスポンスマップを返すリングライクなハンドラー）を呼び出す構成、および純粋関数による HTML 生成 DSL を採用する方針を明記。
- **⚠️ Major-01: ソースコードとテストコードの 1:1 対応規約の違反 (Web層)**
  - **指摘内容**: Web層の実装ファイルに対し、テストファイルがまとめられており 1:1 対応規約に反している。
  - **対応方針**: Web層のテストを以下のように 1:1 で厳密に対応付ける:
    - `src/FlightTrackerAI.Web/views/layout.clj` ⇔ `test/FlightTrackerAI.Web.Tests/views/layout_tests.clj`
    - `src/FlightTrackerAI.Web/views/dashboard.clj` ⇔ `test/FlightTrackerAI.Web.Tests/views/dashboard_tests.clj`
    - `src/FlightTrackerAI.Web/views/modals.clj` ⇔ `test/FlightTrackerAI.Web.Tests/views/modals_tests.clj`
    - `src/FlightTrackerAI.Web/controllers/api_controller.clj` ⇔ `test/FlightTrackerAI.Web.Tests/controllers/api_controller_tests.clj`
    - `src/FlightTrackerAI.Web/server.clj` ⇔ `test/FlightTrackerAI.Web.Tests/server_tests.clj`
- **⚠️ Major-02: テスト計画における境界値・異常系・並行排他制御の具体的テストケース欠如**
  - **指摘内容**: 日付逆転、IATA不正値、目標価格境界値、OpenRouter 429/タイムアウト、SemaphoreSlim 排他競合のテストケースが欠落している。
  - **対応方針**: WI-03 に境界値検証（日付逆転、同日、IATAコード、目標価格0/負数）、WI-04 に異常系・並行制御（OpenRouter 429/タイムアウト、Playwright タイムアウト/Bot検知、SemaphoreSlim 排他競合テスト）を明記する。
- **⚠️ Major-03: スクレイピングテストにおける実ブラウザ依存とオフラインフィクスチャ戦略の欠落**
  - **指摘内容**: テスト実行のたびに実サイトへアクセスするとテストが極度に遅延し Bot 判定される。
  - **対応方針**: WI-04 の単体テストにおいて、オフライン HTML フィクスチャ（`test/.../Fixtures/`）を用いた確定的なパース単体テストを実施し、実ブラウザ E2E は WI-06 に隔離する。
- **ℹ️ Minor-01: Clojure の名前空間規則と .NET プロジェクト・ファイル命名規約の整合性の明文化**
  - **対応方針**: Clojure 名前空間ハイフン（`flight-tracker-ai.core.domain`）とファイルパス（`flight_tracker_ai/core/domain.clj`）の対応規約を明文化する。
- **ℹ️ Minor-02: SQLite データベースマイグレーションの冪等性・バージョニング設計**
  - **対応方針**: `schema_version` テーブルによるステップ管理および冪等なマイグレーション関数とテストを含める。
- **ℹ️ Minor-03: Playwright プロファイル永続化ディレクトリ (`userDataDir`) のライフサイクル管理**
  - **対応方針**: テスト実行時は一時ディレクトリまたはテスト用プロファイルパスを使用し、ロック競合を防止する。

---

## 2. 是正後のタスク計画反映状況

上記の全指摘事項（Critical 5件, Major 7件, Minor 6件）に対する対応方針を `tasks.md` に反映し、作業項目および受入基準を改訂。
