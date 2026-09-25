# AGENTS

## プロジェクト基本方針（OpenSpec ネイティブ）

本プロジェクトは **OpenSpec** による仕様駆動開発 (Spec-Driven Development / SDD) を完全採用しています。

- **Single Source of Truth**: システム仕様の唯一の真実（正本）は `openspec/specs/` です。OpenSpec 管理外に個別の恒久仕様書を作成・二重管理しません。
- **仕様駆動ワークフロー**: すべての機能追加・仕様変更・大規模リファクタリングは、OpenSpec の 3 フェーズ（Propose → Apply → Archive）に則って進めます。
- **UIモック合意規約の徹底**: UI変更を伴う場合は、実装前に `doc/mock/` にてモックを作成・提示し、ユーザー合意を得てから実装に着手します。
- **サブエージェントレビュー必須**: 各フェーズで定義された専門の役割（User / SE / PG / QA Agent）によるレビューを実施し、品質と整合性を担保します。

---

## 構成

- `src/`
  - ソースコードのルートディレクトリ (`src/[Project]/[Path]/[file_name].clj` ※万一の代替言語: `.fs`)
- `test/`
  - テストコードのルートディレクトリ（ソースコードと 1:1 対応: `test/[Project].Tests/[Path]/[file_name]_tests.clj` ※万一の代替言語: `[FileName]Tests.fs`）
- `doc/`
  - 調査メモ・外部リファレンス・補足資料等の配置ディレクトリ（※恒久的なシステム仕様・タスクはすべて `openspec/` で管理）
- `doc/mock/`
  - **UIモック・ワイヤーフレーム・画面プロトタイプ配置ディレクトリ**（※UI変更時のユーザー合意対象）
- `doc/work/TestResults/` および `test-results/`
  - **テスト実行エビデンスおよびカバレッジレポートの出力ディレクトリ**（※Git管理対象外）
  - `doc/work/TestResults/latest/TestResults.html` - テストケース合否レポート (OK/NG一覧)
  - `doc/work/TestResults/latest/CoverageReport.html` - コードカバレッジレポート (網羅率%)
  - `test-results/` - E2E・Playwright 等のテスト実行成果物・スクリーンショット
- `openspec/`
  - **OpenSpecフレームワークのルートディレクトリ**
- `openspec/config.yaml`
  - OpenSpec設定（スキーマ: `spec-driven`、運用ルール・レビュー定義・プロジェクト共通コンテキスト）
- `openspec/specs/`
  - **メイン仕様書・デルタ仕様書の配置ディレクトリ**（システムの恒久的な仕様書）
- `openspec/changes/`
  - **変更管理（提案・タスク・設計・レビュー記録）の配置ディレクトリ**
- `openspec/changes/archive/`
  - 完了した変更のアーカイブ
- `openspec/templates/`
  - **OpenSpecレビュー・チェックリスト用テンプレート**

## 利用可能なMCPサーバ

## 利用可能なSkills

- `openspec-propose` - 新しい変更の提案・計画アーティファクト生成
- `openspec-apply-change` - 変更の実装・タスク実行
- `openspec-archive-change` - 完了した変更のアーカイブ
- `openspec-sync-specs` - デルタ仕様からメイン仕様への同期
- `openspec-update-change` - 既存変更の計画見直し
- `openspec-explore` - アイデア探索・要件明確化

---

## 開発運用ルール

### 1. UI変更時のモック合意規約

- **UI変更が伴う場合は、mockにてユーザと合意したうえで実装を進めること。**
- 画面レイアウト、コンポーネント配置、配色、モーダル、インタラクション等のUI変更が発生する場合は、実装前に必ず `doc/mock/` 配下のプロトタイプ・HTMLモックを更新し、ユーザーに提示して合意を得てから実装に着手する。
- ユーザーによるモック合意のないまま、ビュー（`views/`）やスタイルの変更・実装を行ってはならない。
- OpenSpec においては、**Propose フェーズの `design.md` 作成時** にモックを作成・提示し、ユーザーレビューで合意を形成する。

### 2. ソースコードとテストコードの 1:1 対応規約

- ソースコードとテストコードは **1：1 の対応関係** とし、ファイル名およびディレクトリ構造から対応関係を直接推測できるように命名・配置する。
  - **命名規則**: `src/[Project]/[Path]/[file_name].clj` ⇔ `test/[Project].Tests/[Path]/[file_name]_tests.clj`
  - 例: `src/FlightTrackerAI.Core/flight_tracker_ai/core/domain.clj` ⇔ `test/FlightTrackerAI.Core.Tests/domain_tests.clj`
  - 例: `src/FlightTrackerAI.Infrastructure/flight_tracker_ai/infrastructure/task_repository.clj` ⇔ `test/FlightTrackerAI.Infrastructure.Tests/task_repository_tests.clj`
- 型定義・ロジック・DTO・リポジトリ等の実装ファイルごとに対応するテストファイルを必ず用意し、テストの分散・不透明化を防止する。
- 複数コンポーネントを跨ぐ結合・E2Eテストは `test/[Project].Tests/integration/` 等に配置し、単体テストと明確に分離する。

### 3. テスト実行とレポート出力規約 (合否一覧 & カバレッジレポート)

本プロジェクトは **TDD (Test-Driven Development)** を前提とし、テストの実行結果およびカバレッジを測定・確認しながら開発を進めます。

- テスト実行時は、単にコンソールで合否判定を行うだけでなく、必ず **以下2つのHTML視覚レポートを出力・確認** すること。
  1. **テストケース合否レポート (OK/NG一覧)**: `doc/work/TestResults/latest/TestResults.html` (各実行履歴: `doc/work/TestResults/YYYYMMDD-HHMMSS/`)
     - 全テストケース名、OK(✔)/NG(❌)、所要時間、失敗時の期待値・実際値差分およびスタックトレースを明示。
  2. **コードカバレッジレポート (網羅率%)**: `doc/work/TestResults/latest/CoverageReport.html`
     - 全体およびファイルごとの行・ブランチ網羅率、実行行(緑)/未実行行(赤)のソースコード可視化。
- **標準実行コマンド**: `./scripts/test.ps1`
- **目標カバレッジ**: デフォルト 80% 以上を維持し、未達の場合はテストケース（境界値・異常系）を追加すること。
- **Git管理不要**: テストエビデンスやカバレッジレポートは自動生成されるバイナリ/大容量ファイルを含むため、Git 管理には含めない（`.gitignore` に登録済み）。

### 4. 自動フォーマット実行コマンド

| ファイル種別                  | 対象拡張子         | 使用ツール    | 実行コマンド                                      |
| ----------------------------- | ------------------ | ------------- | ------------------------------------------------- |
| **.NET / F# ソース・テスト**  | `.csproj`, `.fs`   | dotnet format | `dotnet format FlightTrackerAI.slnx`              |
| **Markdown ドキュメント**     | `.md`              | Prettier      | `npx prettier --write "**/*.md"`                  |
| **JSON 設定ファイル**         | `.json`            | Prettier      | `npx prettier --write "**/*.json"`                |
| **OpenSpec アーティファクト** | `openspec/**/*.md` | Prettier      | `npx prettier --write "openspec/**/*.md"`         |
| **Web / UIモック資産**        | `.html`, `.css`    | Prettier      | `npx prettier --write "doc/mock/**/*.{html,css}"` |

### 5. ブランチ運用 & Git操作ルール

#### ブランチ運用

- 開発ブランチ: `alpha`
- 全てのコミットおよびプッシュは `alpha` ブランチ（`origin alpha`）上で行う（または作業用ブランチから `alpha` へマージ）。

#### Commit タイミング（ローカルリポジトリへの記録）

以下のタイミングで **必ず commit** する:

1. **Propose 合意後** (Phase 1 完了時)
   - `openspec/changes/<name>/` 配下の全アーティファクト（`proposal.md`, `specs/`, `design.md`, `tasks.md`, `reviews.md`）
   - コミット例: `docs(change-name): propose phase completed`
2. **Apply 中のタスク完了時** (Phase 2 中)
   - 実装成果物（ソースコード、テストコード）
   - `openspec/changes/<name>/tasks.md` の進捗更新
   - `openspec/changes/<name>/reviews.md` のレビュー記録
   - コミット例: `feat(change-name): implement [task description]`
3. **Archive 完了後** (Phase 3 完了時)
   - `openspec sync specs` で更新されたメイン仕様（`openspec/specs/`）
   - アーカイブされた変更（`openspec/changes/archive/`）
   - README や関連ドキュメント等の更新
   - コミット例: `docs(change-name): archive change and sync specs`

**Commit メッセージ規約**: Conventional Commits に準拠する

```
<type>(<scope>): <subject>

<body>

<footer>
```

- type: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `review`, `spec`
- scope: 変更名 (例: `flight-search`) または `spec`, `design`, `impl` 等

#### Push タイミング（リモートリポジトリへの反映）

1. **Archive 完了・ユーザー最終合意後** (Phase 3 完了時・タスク完了時)
2. **作業中断・セッション終了時** (コミット済みの成果物をリモートへバックアップするため)

#### 禁止事項

- **ユーザー合意なしでの push は禁止**
- **未フォーマット・未テスト状態（ビルドエラーやテストNG）での commit は禁止**
- **テストエビデンス（`doc/work/TestResults/`, `test-results/`）の commit は禁止（Git管理対象外）**

### 6. 合意レベル

タスクの規模に応じて、必要な合意回数を調整する。

| レベル          | 対象                             | 必要な合意                  |
| :-------------- | :------------------------------- | :-------------------------- |
| **L1 (軽微)**   | バグ修正、typo、軽微なリファクタ | 実装後の最終確認のみ（1回） |
| **L2 (中規模)** | 新機能追加、UI変更               | 設計合意 + 最終確認（2回）  |
| **L3 (大規模)** | アーキテクチャ変更、破壊的変更   | 全ステップで合意（4回）     |

---

## OpenSpec CLI コマンド リファレンス

```bash
# 変更の作成・一覧
openspec new change "<name>"           # 新規変更作成
openspec list --json                   # 変更一覧取得
openspec status --change "<name>" --json  # 進捗確認

# アーティファクト操作
openspec instructions <artifact> --change "<name>" --json  # 作成指示取得
openspec validate --change "<name>"    # 検証

# 実装・完了
openspec instructions apply --change "<name>" --json  # 実装指示取得
openspec sync specs --change "<name>"  # スペック同期
openspec archive --change "<name>"     # アーカイブ

# コンテキスト・設定
openspec context --json                # プロジェクトルート確認
openspec schemas --json                # 利用可能スキーマ一覧
```

---

## サブエージェントレビュープロセス

OpenSpecの3フェーズ（Propose → Apply → Archive）それぞれで、定義された役割のサブエージェントによるレビューを**必須**とする。レビュー記録は `openspec/changes/<name>/reviews.md` にテンプレート準拠で蓄積する。

### 1. Propose Phase Review（必須）

- **トリガー**: `openspec new change` 完了、アーティファクト（`proposal.md`, `specs/`, `design.md`, `tasks.md`）作成後、Propose合意前
- **レビュアー**:
  - **User Agent**: 要件妥当性・ビジネス価値・UX・**UIモック合意（`doc/mock/`）**・受け入れ基準
  - **SE Agent**: 技術的実現性・アーキテクチャ整合・影響範囲・非機能要件
- **観点チェックリスト** (`checklist_propose.md` 使用):
  - What/Why が明確かつ500語以内か、Non-goalsが明記されているか
  - **UI変更がある場合、`doc/mock/` 配下にモックが作成・提示され、合意が取れているか**
  - 既存仕様（`openspec/specs/`）との矛盾・影響範囲が整理されているか
  - タスク分解粒度が「2時間以内」目安で妥当か
  - ソースとテストの 1:1 対応方針および TDD 手順が含まれているか
- **合否**: 全役割LGTMで次フェーズへ。指摘がある場合はアーティファクトを更新して再レビュー
- **成果物**: `reviews.md` に記録追記

### 2. Apply Phase Review（各タスク必須）

- **トリガー**: `tasks.md` のチェックボックス `[x]` 更新前（タスク完了宣言前）
- **レビュアー・観点**:
  - **実装タスク** → **PG Agent**（`checklist_apply_impl.md`）
    - 設計書（`design.md`）および UIモック（`doc/mock/`）との整合性
    - コード品質：可読性・命名・関数分割・DRY・ClojureCLR イディオム
    - 境界値・異常系・エラー処理の網羅
    - リファクタの妥当性（テスト変更なし）
    - 自動フォーマット実行済みか
    - ソースとテストの 1:1 対応命名規約遵守
  - **テストタスク** → **QA Agent**（`checklist_apply_test.md`）
    - 仕様妥当性検証テスト：仕様書の期待値をコード化できているか
    - 回帰テスト：既存機能を壊さない観点で網羅できているか
    - 境界値・異常系・エッジケースのテストケース
    - E2E/結合テスト：受け入れシナリオから導出できているか
    - **カバレッジ基準（80%以上）達成・エビデンス出力済みか（`./scripts/test.ps1` 実行、`TestResults.html` & `CoverageReport.html` 確認）**
- **フロー**:
  1. 実装者がタスク完了宣言（PR/コミット前）
  2. 該当役割エージェントがレビュー実施、指摘を `reviews.md` に記録
  3. 指摘への対応方針（採用/保留/却下＋理由）を明記
  4. 実装者が対応コミット
  5. 再レビュー → 全指摘クローズ（LGTM）でタスク完了 `[x]`
- **保留指摘の扱い**: 当該タスクでは完了扱いとし、内容を「申し送り事項」として `reviews.md` および Archive時の別ファイル（`handover.md`）に記録
- **成果物**: `reviews.md` 記録更新、`tasks.md` チェックボックス更新

### 3. Archive Phase Review（必須）

- **トリガー**: `openspec archive` 実行前
- **レビュアー**: 全役割（User Agent / SE Agent / PG Agent / QA Agent）
- **観点チェックリスト** (`checklist_archive.md` 使用):
  - デルタ仕様がメイン仕様（`openspec/specs/`）へ正しく反映されているか（`openspec sync specs` 実行済み）
  - 全テスト通過・カバレッジ基準達成のエビデンスがあるか（`./scripts/test.ps1` 実行、HTMLレポート確認）
  - **UI変更があった場合、`doc/mock/` のモックが実装成果物と同期しているか**
  - README や関連ドキュメントが同期更新済みか
  - 既知の課題・技術的負債・保留指摘が `handover.md` に整理記録されているか
  - 変更サマリ（何が変わったか、テスト結果、レビュー指摘対応状況）が記録されているか
- **合否**: ユーザー最終合意で Archive 実行
- **成果物**: 総括レビュー記録を `reviews.md` に追記、`handover.md` 作成
