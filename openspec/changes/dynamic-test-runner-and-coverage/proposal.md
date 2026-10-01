# Proposal

## Why

現在、`test/test_runner.clj` ではテスト対象名前空間（`test-namespaces`）が静的配列として固定列挙されており、新規テスト追加時に手動更新を怠るとテストが実行されないリスクがあります。さらに、カバレッジレポート（`generate-coverage-html`）は実際のテスト実行結果を反映しておらず、架空の行数とパーセンテージ（94.9%）が静的にハードコーディングされています。

プロジェクトの SDD (仕様駆動開発) および TDD (テスト駆動開発) 規約（AGENTS.md 規約3）に則り、テストスイートの自動探索（Auto-Discovery）と、ClojureCLR 環境に完全適合した動的 Var インストルメンテーションによる実測コードカバレッジ計測・正確な HTML レポート出力を実現します。

## What Changes

- **テスト名前空間の自動検出 (Auto-Discovery)**: `test/` ディレクトリ配下の `*_tests.clj` を再帰探索し、`(ns ...)` 宣言からテスト名前空間を自動ロード・実行する機構を導入します。
- **実測コードカバレッジ計測機構**: `src/` 配下の Clojure ソースを走査し、各モジュールの Var（関数・マクロ）を `alter-var-root` で動的ラップして呼び出しを追跡。Var のメタデータ（`:line` 等）とソースコードの有効行数から実コードカバレッジ（行数・実行行数・網羅率%・未実行関数）を正確に算出します。
- **正確な HTML レポート出力**:
  - `TestResults.html`: スイート数（Namespace）、テストケース数（`deftest`）、アサーション数（Pass/Fail/Error）、所要時間、失敗スタックトレースを正確に出力します。
  - `CoverageReport.html`: 各モジュールの実測行数・カバー行数・網羅率%・目標80%達成状況（色分け）を反映します。
- **アセンブリ解決と実行環境の改善**:
  - `src/*/bin/Debug/net10.0` の依存 DLL を動的に解決・プリロードします。
  - PowerShell スクリプト（`scripts/test.ps1`）のエンコーディング・文字化けを解消し、Windows PowerShell および pwsh の両環境で安定動作させます。

## Non-goals

- ClojureCLR のコンパイラや IL バイトコード自体の書き換え（Clojure 標準の Var メタデータとランタイム操作で実現）。
- サードパーティ製外部プロファイラ（Coverlet 等）の強制導入（ClojureCLR の動的ロード環境ではソース追跡不可のため対象外）。
- アプリケーション本体（コアロジックや Web 画面）の機能追加・変更。

## Capabilities

### New Capabilities

- `test-execution-and-reporting`: ClojureCLR におけるテスト名前空間の自動探索実行、動的カバレッジ計測、およびテスト合否／カバレッジ HTML レポート出力機能。

### Modified Capabilities

（既存機能仕様の変更はありません）

## Impact

- 影響ファイル: `test/test_runner.clj`, `scripts/test.ps1`
- 開発者体験の向上: テスト追加時にランナーを編集する手間が不要になり、正確なテスト合否・カバレッジが `doc/work/TestResults/latest/` に自動出力されます。
