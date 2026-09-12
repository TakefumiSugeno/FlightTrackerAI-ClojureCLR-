# タスク計画: 出発日未超過タスクが早期完了 (Completed) に移行する不具合の修正

## 1. 概要・背景
タスク登録直後の自動巡回において、以下のログが出力されタスクが勝手に完了ステータスへ移行してしまう事象が発生した。
```
[2026-09-13 00:28:42.365 JST] [INFO] [Scraper] タスク 'HND ➔ MNL' は出発日を超過したため完了 (Completed) に移行します。
```

### 根本原因
1. **キー名不整合 (`:outbound` vs `:outbound-date`)**:
   - `api_controller.clj` でのタスク生成時に `:outbound-date` / `:inbound-date` キーを設定していたが、`dto.clj` の `task->row` は `:outbound` / `:inbound` を参照していたため、DBの `tasks.outbound_date` に空文字列 `""` が保存されていた。
2. **空文字列復元時のUTCタイムゾーン起因の「昨日」化**:
   - `dto.clj` の `row->task` で `outbound_date` が空の場合、`DateOnly/Parse` 失敗により `System.DateTime/UtcNow`（UTC現在日）にフォールバックしていた。
   - JST 深夜帯（00:00〜08:59）においては UTC は前日となり、タスクの出発日が「昨日（2026-09-12）」として復元された。
3. **巡回ワーカーでの出発日超過判定**:
   - `scraping_worker.clj` は JST 本日（2026-09-13）とタスクの出発日（2026-09-12）を比較し、過去日とみなして自動完了に移行させていた。

## 2. 合意レベル
**L1 (軽微なバグ修正)**: 仕様・UIの追加・変更はなく、データ不整合と日付処理のバグ修正。実装・テスト完了後に最終確認を行う。

## 3. タスク作業項目
- [ ] **Phase 1: テスト作成 (TDD Red)**
  - `dto_tests.clj`: `:outbound-date` および `:outbound` いずれのキーを持つタスクでも正しく `task->row` で保存用文字列に変換されること、および空文字時に昨日にならないことのテストを追加。
  - `api_controller_tests.clj`: タスク作成API (`/api/tasks`, `/api/tasks/standalone`) で作成されたタスクの出発日・帰国日が欠落せず正しくDBに保存されることのテストを追加。
  - `scraping_worker_tests.clj`: 未来日タスクが出発日超過と判定されないことの境界値テスト。
- [ ] **Phase 2: 実装・修正 (Green)**
  - `src/FlightTrackerAI.Core/flight_tracker_ai/core/dto.clj`:
    - `task->row`: `:outbound` と `:outbound-date`、`:inbound` と `:inbound-date` の両方をサポート。
    - `row->task`: JST現在日付を取得するヘルパーを使用、またはパース例外時の安全な処理。
  - `src/FlightTrackerAI.Web/flight_tracker_ai/web/controllers/api_controller.clj`:
    - タスク登録時の旅行日程マップ生成で、`:outbound` と `:outbound-date` の双方を設定（後方互換性と統一）。
    - JST基準の本日日付取得ヘルパーを使用し、UTC日付のズレを解消。
  - `src/FlightTrackerAI.Web/flight_tracker_ai/web/views/modals.clj` & `dashboard.clj`:
    - 表示・編集時に `:outbound` と `:outbound-date` の両方に対応。
- [ ] **Phase 3: テスト実行・検証・カバレッジ確認**
  - `./scripts/test.ps1` を実行し、全テストパス (OK) およびカバレッジ 80% 以上を維持していることを確認。
  - 既存のDB内にある壊れたタスクレコード（outbound_dateが2026-09-12かつCompleted）の確認・必要に応じたリカバリ。
- [ ] **Phase 4: サブエージェントレビューとドキュメント同期**
  - SE/PG およびユーザーロールによる批判的レビューの記録 (`reviews.md`)。
  - 変更内容のコミット。
