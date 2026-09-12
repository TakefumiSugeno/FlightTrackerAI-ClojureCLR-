# タスク計画書: テスト結果・カバレッジレポートの出力先集約と実行履歴管理

- **タスクID**: `20260913-042200_consolidate_test_and_coverage_reports`
- **合意レベル**: **L1 (軽微 - レポート出力先・スクリプト整理)**
- **ステータス**: **完了 (Completed)**
- **目的**:
  現在 `doc/work/TestResults/` と `doc/work/CoverageReport/` に分散して出力されているテスト合否レポートとコードカバレッジレポートを、`doc/work/TestResults/` の1つのディレクトリ配下に集約する。さらに、テスト実行ごとに日時サブフォルダ（`doc/work/TestResults/YYYYMMDD-HHMMSS/`）を生成して過去の実行履歴を保持するとともに、常に最新結果にアクセスできる `doc/work/TestResults/latest/` への出力・連携を行う。

---

## 1. 背景と課題

1. **レポート出力先の分散**:
   - テスト合否レポートが `doc/work/TestResults/TestResults.html`、カバレッジレポートが `doc/work/CoverageReport/index.html` と別々のフォルダに分かれており、`doc/work/` 直下に複数のレポートディレクトリが散乱していた。
2. **実行履歴の保持ニーズ**:
   - テスト実行のたびに前回の結果が直接上書きされており、直前の実行結果との比較や過去履歴の追跡ができなかった。
3. **最新レポートへのアクセス性の確保**:
   - 日時ごとのフォルダを作成しつつも、CIや開発者が固定URL/パスから即座に最新レポートを開けるように `latest/` ディレクトリ（またはショートカット）が必要。

---

## 2. 実装計画・ディレクトリ構成

### 2.1 新しい出力先ディレクトリ構造
```text
doc/work/TestResults/
├── latest/                                # 常に最新の実行結果（固定パスアクセス用）
│   ├── TestResults.html                   # テスト合否一覧レポート
│   └── CoverageReport.html                # コードカバレッジレポート
│
└── 20260913-042500/                       # テスト実行ごとの日時フォルダ（履歴保持）
    ├── TestResults.html
    └── CoverageReport.html
```
※ 旧 `doc/work/CoverageReport/` ディレクトリは廃止・整理。

### 2.2 変更対象ファイル
1. `test/test_runner.clj`:
   - 実行開始時の JST 日時タイムスタンプ（`yyyyMMdd-HHmmss`）を取得。
   - `doc/work/TestResults/yyyyMMdd-HHmmss/` および `doc/work/TestResults/latest/` に `TestResults.html` と `CoverageReport.html` を出力。
2. `scripts/test.ps1`:
   - 検証・ログ出力パスを `doc/work/TestResults/latest/` および日時フォルダに対応。
3. `.gitignore`:
   - `!doc/work/CoverageReport/` の削除。
   - `doc/work/TestResults/` の扱い整理。
4. ドキュメント類の同期:
   - `AGENTS.md`, `doc/workflow.md`, `doc/design_detail.md`, `README.md` のレポートパス記述を更新。

---

## 3. 実装手順（Step / Phase）

### Phase 1: 計画・設計 (Step 1-2)
- [x] ユーザー希望（日時サブフォルダでの履歴保持＋1つのフォルダ配下への集約）の合意
- [x] タスク計画書 (`tasks.md`) および レビュー記録 (`reviews.md`) の作成

### Phase 2: 実装・動作検証 (Step 3)
- [x] `test/test_runner.clj` の出力パスロジック更新
- [x] `scripts/test.ps1` の検証ロジック更新
- [x] `.gitignore` の更新
- [x] 旧 `doc/work/CoverageReport/` の削除・移行
- [x] `./scripts/test.ps1` を実行し、`doc/work/TestResults/YYYYMMDD-HHMMSS/` および `latest/` に正しく出力されることを検証

### Phase 3: ドキュメント同期 & コミット (Step 4)
- [x] `AGENTS.md`、`doc/workflow.md`、`doc/design_detail.md`、`README.md` 等の関連ドキュメント同期
- [x] コミット作成
  - 実装コミット: `f3625c5` (`refactor: テスト合否結果およびカバレッジレポートの出力先をTestResults配下に集約・実行履歴保持対応`)
  - ドキュメントコミット: `24200d2` (`docs: テストレポート集約に伴う規約・仕様書・設計書・タスク管理の同期`)
- [x] ユーザーへの完了報告 & リモートプッシュ (`origin alpha`) 完了

---

## 4. 完了検証結果
- **テスト全件パス**: 22スイート、541アサーション全件通過 (Fail: 0, Error: 0)
- **カバレッジ**: Overall 94.9% 達成
- **出力確認**:
  - `doc/work/TestResults/latest/TestResults.html` (生成OK)
  - `doc/work/TestResults/latest/CoverageReport.html` (生成OK)
  - `doc/work/TestResults/20260913-042357/` (履歴フォルダ生成OK、Git対象外OK)
