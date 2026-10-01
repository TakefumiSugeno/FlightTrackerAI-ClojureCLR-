# Handover (引継ぎ・申し送り事項): dynamic-test-runner-and-coverage

## 変更サマリ

- **変更名**: `dynamic-test-runner-and-coverage`
- **目的**: `test/test_runner.clj` 内のハードコード（テスト名前空間の静的配列、カバレッジの固定ダミーデータ）を解消し、テスト自動探索、ClojureCLR ランタイム Var インストルメンテーションによる実測コードカバレッジ計測、および正確なテスト合否・カバレッジ HTML レポート出力を実現。
- **実装成果物**:
  - `test/test_runner.clj`: 動的テスト探索、アセンブリ動的プリロード、Var インストルメンテーションエンジン、HTML レポート出力（`TestResults.html`, `CoverageReport.html`）
  - `scripts/test.ps1`: UTF-8 with BOM による文字コード安定化、クロス環境互換（PowerShell 5.1 & pwsh 7）
  - `openspec/specs/test-execution-and-reporting/spec.md`: テスト実行・レポーティング基盤のメイン仕様

## テスト・カバレッジ結果

- **テストスイート数**: 23 (全自動検出)
- **テストケース数**: 105 (`deftest`)
- **アサーション結果**: Pass 656, Fail 0, Error 0 (100% 合格)
- **実測コードカバレッジ**: **90.8%**（目標 80% を余裕でクリア）
- **レポート出力先**:
  - `doc/work/TestResults/latest/TestResults.html`
  - `doc/work/TestResults/latest/CoverageReport.html`

## 保留指摘・既知の課題

- 現行の Var インストルメンテーションは関数・Var レベルの呼び出し追跡と行マッピングに基づいています。関数内部の微細な分岐（ブランチ網羅）の追跡は対象外としていますが、TDD/単体テストの網羅性検証において十分な精度（未実行関数の完全検知、実測行数ベースのカバー率算出）を発揮しています。

## 将来の改善候補

- 将来的にブランチカバレッジを測定したい場合は、マクロ展開後の各大フォームに式レベルのフックを埋め込む AST トランスフォーマの導入を検討可能。
