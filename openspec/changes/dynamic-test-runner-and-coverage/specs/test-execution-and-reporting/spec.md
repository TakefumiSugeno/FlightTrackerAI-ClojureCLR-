# Spec Delta

## Purpose

本機能は、ClojureCLR (.NET 10) 環境におけるテストスイートの自動探索・動的実行、ランタイム Var インストルメンテーションによる実測コードカバレッジ計測、およびテスト合否・カバレッジの視覚的 HTML レポート出力機能を提供します。

## ADDED Requirements

### Requirement: Dynamic Test Discovery

テストランナーは、テスト対象名前空間を手動定義することなく、ファイルシステム上のテストコードを自動検知して実行しなければならない (SHALL)。

- テストランナーは `test/` ディレクトリ配下（`bin/` および `obj/` を除く）の `*_tests.clj` ファイルを再帰的に走査しなければならない (SHALL)。
- 各テストファイル先頭の `ns` 宣言から名前空間シンボルを抽出し、動的に `require` して `clojure.test/test-ns` で実行しなければならない (SHALL)。

#### Scenario: テストファイルの自動検出と全実行

- **WHEN** 開発者が `./scripts/test.ps1` またはテストランナーを実行したとき
- **THEN** `test/` 配下のすべての `*_tests.clj` に対応するテスト名前空間が自動的にロードされ、テストスイートとして実行されること。

---

### Requirement: Dynamic Code Coverage Measurement

テストランナーは、静的ハードコーディングによる架空データではなく、ランタイム上で実行されたコードの実測カバレッジを測定しなければならない (SHALL)。

- テストランナーは `src/` 配下の Clojure モジュール（`*.clj`）を自動探索し、計測対象モジュールとして認識しなければならない (SHALL)。
- 対象名前空間のすべての公開・非公開 Var（関数）を動的にインターセプトし、テスト実行中の呼び出し回数を記録しなければならない (SHALL)。
- 各 Var のメタデータ（`:line`）およびソースファイルの有効コード行数（空行・コメント行を除く）に基づき、実行行数および行カバレッジ率（%）を正確に算出しなければならない (SHALL)。

#### Scenario: テスト実行時の動的カバレッジ算出

- **WHEN** テストランナーが全テストスイートの実行を完了したとき
- **THEN** 各モジュールの実ソース行数、有効コード行数、テストで実行された行数、およびパーセンテージ（%）が動的に算出され、固定値ではない実測値が集計されること。

---

### Requirement: HTML Test Results Reporting

テストランナーは、テスト実行結果の全詳細を含む HTML レポートを出力しなければならない (SHALL)。

- テストランナーは、実行されたテスト名前空間（Suites）、テストケース（`deftest`）、アサーション数（Pass / Fail / Error）、所要時間、および失敗時のエラー詳細・スタックトレースを含む HTML レポートを生成しなければならない (SHALL)。
- レポートは `doc/work/TestResults/latest/TestResults.html` および実行日時フォルダ（`doc/work/TestResults/YYYYMMDD-HHMMSS/TestResults.html`）の両方に出力しなければならない (SHALL)。

#### Scenario: テスト合否レポートの出力

- **WHEN** テストスイートが実行されたとき
- **THEN** `doc/work/TestResults/latest/TestResults.html` に全テストケースの合否結果・所要時間・詳細メッセージが反映された HTML が生成されること。

---

### Requirement: HTML Coverage Reporting

テストランナーは、モジュール別の実測コード網羅率を含む HTML カバレッジレポートを出力しなければならない (SHALL)。

- テストランナーは、モジュールごとの実コード行数、カバー行数、網羅率%、およびプロジェクト全体の総合カバレッジ率を表示する HTML レポートを生成しなければならない (SHALL)。
- 目標カバレッジ（80%以上）の達成状況を視覚的バッジおよび色分けで表示しなければならない (SHALL)。
- レポートは `doc/work/TestResults/latest/CoverageReport.html` および実行日時フォルダ（`doc/work/TestResults/YYYYMMDD-HHMMSS/CoverageReport.html`）の両方に出力しなければならない (SHALL)。

#### Scenario: カバレッジレポートの出力

- **WHEN** テスト実行とカバレッジ集計が完了したとき
- **THEN** `doc/work/TestResults/latest/CoverageReport.html` に実測行数および網羅率が記載された HTML が生成されること。
